package il.ac.technion.cs.softwaredesign

import java.util.*

fun String.toBytes():ByteArray{
    return toByteArray(Charsets.UTF_8)
}

class CByteArrayWrapper(val b: ByteArray){
    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        return b contentEquals (other as CByteArrayWrapper).b

    }
    override fun hashCode(): Int{
        return Arrays.hashCode(b)
    }
}