package il.ac.technion.cs.softwaredesign.lib.db.dal

import com.google.gson.*
import com.google.gson.internal.LinkedTreeMap
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture
import il.ac.technion.cs.softwaredesign.lib.utils.bytes
import java.lang.IllegalArgumentException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.ArrayList


/**
 * Singleton class for retrieving Gson instance
 */
class GsonInstance {
    companion object {
        val instance: Gson = GsonBuilder()
                // Register custom serializer/deserializer for JSON maps
                .registerTypeAdapter(object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type, MapDeserializer())
                .registerTypeAdapter(object: com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type, MapSerializer())
                .create()

    }
}

//convert a map to a data class
fun <T> Map<String, Any>.toDataClass(clazz: Class<T>): T {
    val jsonElement = Gson().toJsonTree(this)
    return Gson().fromJson(jsonElement, clazz)
}

/**
 * The data access layer is responsible for coordinating read/write operations to the database using keys and mapped
 * values (objects). This DAL uses Gson as a serializer/deserializer
 */
class DocumentDataAccessLayer(private val defaultStorage: CompletableFuture<SecureStorage>) : DataAccessLayer<String, Map<String, Any>> {

    private val objectCache: MutableMap<String, ByteArray> = HashMap()

    private fun exists(id: String): CompletableFuture<Boolean> {
        return defaultStorage.thenCompose { storage ->
            val k = (id + "__e")
            if (objectCache.containsKey(k)) CompletableFuture.completedFuture(objectCache[k])
            else storage.read(k.toByteArray())

        }.thenApply { e -> (e != null) && e[0].compareTo(1) == 0 }
    }

    /**
     * Read an object from the database for a given id (id)
     * @param id The id of the object
     * @return An object mapping of (id,value) pairs, or null if no object was found
     */
    override fun readObject(id: String, keys: List<String>): CompletableFuture<Map<String, Any>?> {
        return exists(id)
                .thenCompose { e ->
                    if (!e) CompletableFuture.completedFuture(null as Map<String, Any>?)
                    else {
                        val obj: MutableMap<String, Any> = mutableMapOf()
                        CompletableFuture.allOf(*keys.map { key ->
                            defaultStorage.thenCompose { s ->
                                if (objectCache.containsKey(id + key)) CompletableFuture.completedFuture(objectCache[id + key])
                                else s.read((id + key).toByteArray())
                            }
                                    .thenApply { value ->
                                        if (value == null) throw IllegalArgumentException("Key $key doesn't exist")

                                        val type = value[0].toChar()
                                        val bytes = value.drop(1).toByteArray()
                                        val buffer = ByteBuffer.wrap(bytes)
                                        val str = String(bytes)

                                        var actual: Any = str
                                        when (type) {
                                            'a' -> actual = buffer.long
                                            'i' -> actual = buffer.int
                                            'f' -> actual = buffer.float
                                            'd' -> actual = buffer.double
                                            's' -> actual = String(bytes)
                                            'b' -> actual = bytes.isNotEmpty()
                                            'p' -> actual = bytes
                                            'l' -> actual = if (str.isNotEmpty()) str.split(",").toList() else listOf()
                                            'k' -> actual = KeyPair(buffer.long, buffer.long)
                                        }
                                        obj[key] = actual
                                    }
                        }.toTypedArray())
                                .thenApply {
                                    obj.toMap()
                                }
                    }
                }

    }

    /**
     * Write an object to the database with a given id and (id,value) pair mapping
     * @param id The id of the object
     * @param value A (id,value) mapping of the object's values
     */
    override fun writeObject(id: String, value: Map<String, Any>) : CompletableFuture<Unit> {
        return defaultStorage.thenCompose { storage ->
            val k = (id + "__e")
            val boolByte = byteArrayOf(1)
            objectCache[k] = boolByte
            storage.write(k.toByteArray(), boolByte).thenApply { storage }
        }.thenCompose { storage ->

            CompletableFuture.allOf(*value.entries.map { entry ->
                var type = 's'
                var v: ByteArray = entry.value.toString().toByteArray()
                when (entry.value) {
                    is Int -> {
                        type = 'i'
                        v = (entry.value as Int).bytes()
                    }
                    is Double -> {
                        type = 'd'
                        v = (entry.value as Double).bytes()
                    }
                    is Long -> {
                        type = 'a'
                        v = (entry.value as Long).bytes()
                    }
                    is String -> type = 's'
                    is Boolean -> {
                        type = 'b'
                        v = if (entry.value as Boolean) byteArrayOf(1) else byteArrayOf()
                    }
                    is ByteArray -> {
                        type = 'p'
                        v = entry.value as ByteArray
                    }
                    is List<*> -> {
                        type = 'l'
                        v = (entry.value as List<String>).joinToString(",").toByteArray()
                    }
                    is KeyPair<*> -> {
                        type = 'k'
                        v = (entry.value as KeyPair<Long>).bytes()
                    }
                }

                val array = byteArrayOf(type.toByte()).plus(v)
                objectCache[id + entry.key] = array
                storage.write((id + entry.key).toByteArray(), array)
            }.toTypedArray()).thenApply {  }
        }
    }

    /**
     * Delete an object from the database for a given id
     * @param id The id of the object
     */
    override fun deleteObject(id: String, keys: List<String>) : CompletableFuture<Map<String, Any>?> {
        // Currently only a logical delete
        return readObject(id, keys)
                .thenCompose { obj -> defaultStorage.thenApply { Pair(it, obj) } }
                .thenCompose { (storage, obj) ->
                    val k = (id + "__e")
                    objectCache.remove(k)
                    storage.write(k.toByteArray(), byteArrayOf(0)).thenApply { obj }
                }
    }
}

class MapSerializer : JsonSerializer<Map<String, Any>> {
    override fun serialize(src: Map<String, Any>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val serialized = context?.serialize(src)
        return serialized!!
    }
}

class MapDeserializer : JsonDeserializer<Map<String, Any>> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Map<String, Any> {
        return read(json) as Map<String, Any>? ?: HashMap()
    }

    fun read(element: JsonElement): Any? {

        when {
            element.isJsonArray -> {
                val list = ArrayList<Any>()
                val arr = element.asJsonArray
                for (anArr in arr) {
                    list.add(read(anArr) ?: continue)
                }
                return list
            }
            element.isJsonObject -> {
                val map = LinkedTreeMap<String, Any>()
                val obj = element.asJsonObject
                val entitySet = obj.entrySet()
                for ((key, value) in entitySet) {
                    map[key] = read(value)
                }
                return map
            }
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> return prim.asBoolean
                    prim.isString -> return prim.asString
                    prim.isNumber -> {
                        val num = prim.asNumber
                        // here you can handle double int/long values
                        // and return any type you want
                        // this solution will transform 3.0 float to long values
                        return if (Math.ceil(num.toDouble()) == num.toInt().toDouble())
                            num.toInt()
                        else {
                            num.toDouble()
                        }
                    }
                }
            }
        }
        return null
    }
}
