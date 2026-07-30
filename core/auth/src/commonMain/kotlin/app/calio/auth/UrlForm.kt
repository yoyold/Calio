package app.calio.auth

private const val HEX = "0123456789ABCDEF"

/**
 * Percent-encoding as the URI specification defines it, on the bytes of the UTF-8 text.
 *
 * Written out because a calendar name, a scope or a token can contain anything, and a value that
 * escapes its parameter turns into a different request rather than an error anyone can see.
 */
internal fun String.percentEncode(): String {
    val encoded = StringBuilder(length)
    for (byte in encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val character = value.toChar()
        val unreserved = character in 'A'..'Z' || character in 'a'..'z' ||
            character in '0'..'9' || character in "-._~"

        if (unreserved) {
            encoded.append(character)
        } else {
            encoded.append('%').append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
    }
    return encoded.toString()
}

internal fun String.percentDecode(): String {
    if ('%' !in this && '+' !in this) return this

    val decoded = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        when (val character = this[index]) {
            // A space arrives as '+' in a form-encoded query, which is where redirects come from.
            '+' -> {
                decoded += ' '.code.toByte()
                index++
            }

            '%' -> {
                val digits = substring(index + 1, minOf(index + 3, length))
                val value = digits.toIntOrNull(radix = 16)
                if (value == null || digits.length < 2) {
                    decoded += character.code.toByte()
                    index++
                } else {
                    decoded += value.toByte()
                    index += 3
                }
            }

            else -> {
                decoded += character.code.toByte()
                index++
            }
        }
    }
    return decoded.toByteArray().decodeToString()
}

internal fun Map<String, String>.toQueryString(): String =
    entries.joinToString(separator = "&") { (name, value) ->
        "${name.percentEncode()}=${value.percentEncode()}"
    }

/**
 * The parameters of a URL, whichever way round the provider chose to answer.
 *
 * Errors come back in the query and tokens sometimes in the fragment, so both are read.
 */
internal fun queryParametersOf(url: String): Map<String, String> {
    val afterPath = url.substringAfter('?', missingDelimiterValue = "")
    val query = afterPath.substringBefore('#')
    val fragment = url.substringAfter('#', missingDelimiterValue = "")

    return (query.split('&') + fragment.split('&'))
        .filter { it.isNotEmpty() }
        .associate { part ->
            part.substringBefore('=').percentDecode() to part.substringAfter('=', "").percentDecode()
        }
}
