package app.calio.crypto

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/**
 * Base64 in its URL-safe form and without padding, as OAuth and JOSE use it everywhere.
 *
 * Written out rather than taken from a platform class because both platforms spell it differently
 * and the values it produces travel to a provider, where a single wrong character is rejected
 * without an explanation.
 */
fun ByteArray.encodeBase64Url(): String {
    val encoded = StringBuilder((size + 2) / 3 * 4)
    var index = 0

    while (index + 2 < size) {
        val group = (this[index].toInt() and 0xFF shl 16) or
            (this[index + 1].toInt() and 0xFF shl 8) or
            (this[index + 2].toInt() and 0xFF)
        encoded.append(ALPHABET[group ushr 18 and 0x3F])
        encoded.append(ALPHABET[group ushr 12 and 0x3F])
        encoded.append(ALPHABET[group ushr 6 and 0x3F])
        encoded.append(ALPHABET[group and 0x3F])
        index += 3
    }

    // The last one or two bytes carry fewer than four characters, and no '=' takes their place.
    when (size - index) {
        1 -> {
            val group = this[index].toInt() and 0xFF shl 16
            encoded.append(ALPHABET[group ushr 18 and 0x3F])
            encoded.append(ALPHABET[group ushr 12 and 0x3F])
        }

        2 -> {
            val group = (this[index].toInt() and 0xFF shl 16) or (this[index + 1].toInt() and 0xFF shl 8)
            encoded.append(ALPHABET[group ushr 18 and 0x3F])
            encoded.append(ALPHABET[group ushr 12 and 0x3F])
            encoded.append(ALPHABET[group ushr 6 and 0x3F])
        }
    }

    return encoded.toString()
}

fun String.decodeBase64Url(): ByteArray {
    val decoded = ByteArray(length * 3 / 4)
    var buffer = 0
    var bits = 0
    var written = 0

    for (character in this) {
        // Padding is accepted on the way in, because some servers still add it.
        if (character == '=') continue
        val value = ALPHABET.indexOf(character)
        require(value >= 0) { "'$character' is not part of the URL-safe alphabet" }

        buffer = buffer shl 6 or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            decoded[written++] = (buffer ushr bits and 0xFF).toByte()
        }
    }

    return decoded.copyOf(written)
}
