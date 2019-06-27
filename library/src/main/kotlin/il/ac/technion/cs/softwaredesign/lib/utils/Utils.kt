package il.ac.technion.cs.softwaredesign.lib.utils

import il.ac.technion.cs.softwaredesign.lib.db.dal.KeyPair
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*

/**
 * Simple md5 hash implementation
 */
fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
}

/**
 * Hash a string as a password
 */
fun String.hashPassword(): String {
    return this
}

private val pool = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun generateToken() : String {
    val seed = (1..4).map { kotlin.random.Random.nextInt(0, pool.size) }.map(pool::get).joinToString("")
    return "${Date().time}_$seed"
}

fun Int.bytes(): ByteArray {
    val buffer = ByteBuffer.allocate(Integer.BYTES).putInt(this)
    val ret = ByteArray(Integer.BYTES)
    buffer.rewind()
    buffer.get(ret)
    return ret
}

fun Long.bytes(): ByteArray {
    val buffer = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this)
    val ret = ByteArray(Long.SIZE_BYTES)
    buffer.rewind()
    buffer.get(ret)
    return ret
}

fun Double.bytes(): ByteArray {
    val buffer = ByteBuffer.allocate(Long.SIZE_BYTES).putDouble(this)
    val ret = ByteArray(Long.SIZE_BYTES)
    buffer.rewind()
    buffer.get(ret)
    return ret
}

fun KeyPair<Long>.bytes(): ByteArray {
    val buffer = ByteBuffer.allocate(Long.SIZE_BYTES * 2).putLong(this.getFirst()).putLong(this.getSecond())
    val ret = ByteArray(Long.SIZE_BYTES * 2)
    buffer.rewind()
    buffer.get(ret)
    return ret
}