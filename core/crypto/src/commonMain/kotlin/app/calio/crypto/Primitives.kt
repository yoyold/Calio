package app.calio.crypto

/** SHA-256, needed for the PKCE challenge and for naming stored secrets without naming their key. */
expect fun sha256(input: ByteArray): ByteArray

/**
 * Bytes from the platform's cryptographic random source.
 *
 * The ordinary random number generator is predictable from a few of its outputs, which for a PKCE
 * verifier or an OAuth state value would defeat the point of having them.
 */
expect fun randomBytes(count: Int): ByteArray
