package app.calio.sync

/**
 * Seals a payload before it leaves the device and opens it when it comes back.
 *
 * Two implementations exist from the start so that switching to real encryption is a change of one
 * binding rather than a migration of every record that has already been synchronised.
 */
interface PayloadCipher {
    val scheme: String

    fun seal(plaintext: String): SealedPayload

    /** Returns null when the payload cannot be opened, for example after a key change. */
    fun open(payload: SealedPayload): String?
}

/**
 * The cipher for a local-only installation.
 *
 * It carries the payload unchanged while still producing a proper envelope, so nothing about the
 * stored format changes on the day encryption is switched on.
 */
class PassthroughCipher : PayloadCipher {

    override val scheme: String = SCHEME

    override fun seal(plaintext: String): SealedPayload =
        SealedPayload(scheme = SCHEME, ciphertext = plaintext)

    override fun open(payload: SealedPayload): String? =
        payload.ciphertext.takeIf { payload.scheme == SCHEME }

    private companion object {
        const val SCHEME = "plain"
    }
}
