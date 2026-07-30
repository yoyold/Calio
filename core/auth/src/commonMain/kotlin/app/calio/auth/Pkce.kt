package app.calio.auth

import app.calio.crypto.encodeBase64Url
import app.calio.crypto.randomBytes
import app.calio.crypto.sha256

/**
 * The proof that the application asking for the tokens is the one that started the sign-in.
 *
 * A desktop or mobile client has no secret, so the authorization code on its own would be enough for
 * anyone who intercepted the redirect — another program listening on the loopback port, another app
 * claiming the same custom scheme. The verifier is invented for this one sign-in and never leaves
 * the device until the code is redeemed; only its digest travels with the request. An intercepted
 * code is therefore worth nothing without it.
 */
class PkceChallenge private constructor(val verifier: String, val challenge: String) {

    /** The plain method is still permitted by the specification and offers no protection. */
    val method: String get() = "S256"

    companion object {
        private const val VERIFIER_BYTES = 32

        fun generate(): PkceChallenge = of(randomBytes(VERIFIER_BYTES).encodeBase64Url())

        /** For a verifier that already exists, which is what makes the derivation testable. */
        fun of(verifier: String): PkceChallenge {
            require(verifier.length in 43..128) {
                "a code verifier is between 43 and 128 characters, this one is ${verifier.length}"
            }
            return PkceChallenge(
                verifier = verifier,
                challenge = sha256(verifier.encodeToByteArray()).encodeBase64Url(),
            )
        }
    }
}

/**
 * A value carried through the sign-in and checked when the user comes back.
 *
 * It ties the redirect to the request that started it. Without it, a redirect anyone can trigger
 * would be indistinguishable from the one the user actually asked for.
 */
fun newStateValue(): String = randomBytes(16).encodeBase64Url()
