package il.ac.technion.cs.softwaredesign.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * (De)serializes an object
 */
object ObjectSerializer {
    /**
     * Serialize given object of generic type [T] into [String]
     * @param obj object to serialize
     * @see ObjectInputStream
     * @return the serialization result (an empty string for null input)
     */
    fun <T : Serializable> serialize(obj: T): String {
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(obj)
        oos.close()

        return baos.toString("ISO-8859-1")
    }

    /**
     * Deserialize given [String] into object of generic type [T]
     * @param string the string to deserialize
     * @return deserialized object of type [T] (null if an empty string is passed)
     */
    fun <T : Serializable> deserialize(string: String): T {
        val bais = ByteArrayInputStream(string.toByteArray(charset("ISO-8859-1")))
        val ois = ObjectInputStream(bais)

        @Suppress("UNCHECKED_CAST")
        return ois.readObject() as T
    }
}