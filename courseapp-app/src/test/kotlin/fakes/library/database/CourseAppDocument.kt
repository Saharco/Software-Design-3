package fakes.library.database

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.github.vjames19.futures.jdk8.Future
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture

/**
 * Implementation of [DocumentReference].
 *
 * This class is abstract - it can only be constructed via [CourseAppCollection]
 */
abstract class CourseAppDocument internal constructor(path: String, protected val storage: SecureStorage)
    : DocumentReference {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    protected val path: String = "$path/"
    private var data: HashMap<String, String> = HashMap()

    override fun set(field: Pair<String, String>): CourseAppDocument {
        data[field.first] = field.second
        return this
    }

    override fun set(field: String, list: List<String>): CourseAppDocument {
        val jsonString = Gson().toJson(list)
        data[field] = jsonString
        return this
    }

    override fun set(data: Map<String, String>): CourseAppDocument {
        for (entry in data.entries)
            this.data[entry.key] = entry.value
        return this
    }

    override fun write(): CompletableFuture<Unit> {
        if (data.isEmpty())
            return Future { throw IllegalStateException("Can\'t write empty document") }
        return exists().thenCompose { result ->
            if (result) {
                logger.debug { "attempted writing a document that already exists" }
                throw IllegalArgumentException("Document already exists")
            } else {
                allocatePath().thenCompose {
                    writeEntries(data.entries.toMutableList())
                }
            }
        }
    }

    private fun writeEntries(entries: MutableList<MutableMap.MutableEntry<String, String>>): CompletableFuture<Unit> {
        if (entries.isEmpty())
            return CompletableFuture.completedFuture(Unit)
        val entry = entries.removeAt(0)
        return writeEntry("$path${entry.key}/", entry.value).thenCompose {
            writeEntries(entries)
        }
    }

    override fun read(field: String): CompletableFuture<String?> {
        return readField(field, ::deserializeToString)
    }

    override fun readList(field: String): CompletableFuture<List<String>?> {
        return readField(field, ::deserializeToList)
    }

    /**
     * Reads a document's field from the database.
     * The generic type T is the type of the field being read.
     *
     * @param field: document's field to be read
     * @param deserializer: a function that deserializes the bytes obtained from storage into object of type T
     * @return the field's value in the database if it exists and can be deserialized properly, null otherwise
     */
    private fun <T> readField(field: String, deserializer: (List<Byte>) -> T): CompletableFuture<T?> {
        return isValidPath().thenCompose { result ->
            if (!result) {
                logger.debug { "did not find field \"$field\" in the document" }
                CompletableFuture.completedFuture(null as T?)
            } else {
                val key = ("$path$field/").toByteArray()
                storage.read(key)
                        .thenApply { value ->
                            val bytesList = value?.toList()
                            if (bytesList == null || !isActivated(bytesList[0])) {
                                logger.debug { "$field was previously deleted from the document: it cannot be read" }
                                null
                            } else {
                                deserializer(bytesList)
                            }
                        }
            }
        }
    }

    private fun deserializeToString(bytesList: List<Byte>): String {
        logger.debug { "reading string formatted field" }
        return String(bytesList
                .takeLast(bytesList.size - 1)
                .toByteArray())
    }

    private fun deserializeToList(bytesList: List<Byte>): List<String> {
        logger.debug { "reading list formatted field" }
        val json = String(bytesList
                .takeLast(bytesList.size - 1)
                .toByteArray())
        val collectionType = object : TypeToken<Collection<String>>() {}.type
        return Gson().fromJson(json, collectionType)
    }

    override fun update(): CompletableFuture<Unit> {
        if (data.isEmpty())
            return Future { throw IllegalStateException("Can\'t write empty document") }
        return allocatePath().thenCompose {
            writeEntries(data.entries.toMutableList())
        }
    }

    override fun delete(): CompletableFuture<Unit> {
        return deleteFields(data.keys.toMutableList()).thenCompose {
            deleteEntry(path)
        }
    }

    override fun delete(fields: List<String>): CompletableFuture<Unit> {
        return deleteFields(fields.toMutableList())
    }

    private fun deleteFields(fields: MutableList<String>): CompletableFuture<Unit> {
        if (fields.isEmpty())
            return CompletableFuture.completedFuture(Unit)
        val field = fields.removeAt(0)
        return deleteEntry("$path$field/").thenCompose {
            deleteFields(fields)
        }
    }

    override fun exists(): CompletableFuture<Boolean> {
        return pathExists(path)
    }

    /**
     * Checks whether the document's path is valid or not.
     *
     * Path is "valid" if all the documents in the path leading to the last document exist and weren't logically written off
     */
    private fun isValidPath(): CompletableFuture<Boolean> {
        val reg = Regex("(?<=/)")
        val pathSequence = ArrayList<String>(path.split(reg))
        return isValidPathAux(pathSequence, pathSequence.removeAt(0))
    }

    private fun isValidPathAux(pathSequence: ArrayList<String>, prevPath: String): CompletableFuture<Boolean> {
        if (pathSequence.size <= 2)
            return CompletableFuture.completedFuture(true)
        val extension = pathSequence.removeAt(0) + pathSequence.removeAt(0)
        val currentPath = prevPath + extension
        return pathExists(currentPath).thenCompose { result ->
            if (!result)
                CompletableFuture.completedFuture(false)
            else
                isValidPathAux(pathSequence, currentPath)
        }
    }

    /**
     * Checks if the desired path exists in the file system
     */
    private fun pathExists(pathToCheck: String): CompletableFuture<Boolean> {
        val key = pathToCheck.toByteArray()
        return storage.read(key)
                .thenApply { value ->
                    value != null && isActivated(value[0])
                }
    }

    private fun isActivated(byte: Byte): Boolean {
        return byte != statusBlock(activated = false)[0]
    }

    /**
     * Creates a prefix ByteArray block to be chained to data in order to logically turn it on/off
     */
    private fun statusBlock(activated: Boolean = true): ByteArray {
        val status = if (activated) 1 else 0
        return ByteArray(1) { status.toByte() }
    }

    /**
     * Creates all the documents in the full path leading to the final document in the path
     */
    private fun allocatePath(): CompletableFuture<Unit> {
        val reg = Regex("(?<=/)") // this regex splits the path by the '/' delimiter
        val pathSequence = ArrayList<String>(path.split(reg))
        val currentPath = pathSequence.removeAt(0)
        return allocatePathAux(pathSequence, currentPath)
    }

    private fun allocatePathAux(pathSequence: ArrayList<String>, prevPath: String): CompletableFuture<Unit> {
        if (pathSequence.size <= 2)
            return CompletableFuture.completedFuture(Unit)
        val extension = pathSequence.removeAt(0) + pathSequence.removeAt(0)
        val currentPath = prevPath + extension
        val key = currentPath.toByteArray()
        return storage.write(key, statusBlock(activated = true)).thenCompose {
            allocatePathAux(pathSequence, currentPath)
        }
    }

    /**
     * Performs a low-level write with some key (field) and its value.
     * Chains a prefix activation block to logically turn it on
     */
    private fun writeEntry(field: String, value: String): CompletableFuture<Unit> {
        val key = field.toByteArray()
        val data = statusBlock(activated = true) + value.toByteArray()
        return storage.write(key, data)
    }

    /**
     * Performs a low-level delete of some key (path).
     * Chains a prefix activation block to logically turn it off
     */
    private fun deleteEntry(path: String): CompletableFuture<Unit> {
        val key = path.toByteArray()
        val store = statusBlock(activated = false)
        return storage.write(key, store)
    }
}
