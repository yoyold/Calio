package app.calio.crypto

/**
 * Where a secret lives that has to survive a restart but must never appear in a backup.
 *
 * Refresh tokens are the reason this exists. One is a standing key to somebody's calendar, valid
 * until it is revoked, so it cannot sit in the database next to the events: that file is copied into
 * backups, exports and support archives. Each platform has somewhere better, and the difference
 * between those places is the only thing this interface hides.
 */
interface SecretStore {

    suspend fun put(key: String, secret: String)

    suspend fun get(key: String): String?

    suspend fun remove(key: String)
}

/** For tests, and for a run that must not leave anything behind. */
class InMemorySecretStore : SecretStore {

    private val secrets = mutableMapOf<String, String>()

    override suspend fun put(key: String, secret: String) {
        secrets[key] = secret
    }

    override suspend fun get(key: String): String? = secrets[key]

    override suspend fun remove(key: String) {
        secrets.remove(key)
    }
}
