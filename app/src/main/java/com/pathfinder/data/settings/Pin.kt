package com.pathfinder.data.settings

import java.security.MessageDigest

/** PIN do coordenador: só o hash é guardado. */
object Pin {
    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("pathfinder:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun matches(pin: String, storedHash: String): Boolean = storedHash.isEmpty() || hash(pin) == storedHash

    fun isValid(pin: String) = pin.length in 4..8 && pin.all(Char::isDigit)
}
