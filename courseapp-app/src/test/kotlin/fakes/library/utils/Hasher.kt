package fakes.library.utils

import java.security.MessageDigest

/**
 *  Wrapper class for encrypting messages.
 *  Encryption is one-way, using the SHA-256 algorithm
 */
class Hasher {
    /**
     * @param message: message to encrypt
     * @return SHA-256 encryption of [message]
     */
    fun hash(message: String): String {
        val bytes = message.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("", { str, it -> str + "%02x".format(it) })
    }

    /**
     * Calls the [hash] method to encrypt [message]
     */
    operator fun invoke(message: String): String {
        return hash(message)
    }
}