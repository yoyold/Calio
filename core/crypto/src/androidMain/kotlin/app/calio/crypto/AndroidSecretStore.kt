package app.calio.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets sealed by a key the application never sees.
 *
 * The key is generated inside the Android keystore and cannot be read out of it, only used. What
 * ends up in the preferences file is therefore of no use to anyone who extracts it — including the
 * device backup, which is the reason a refresh token must not simply be stored as text.
 */
class AndroidSecretStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun put(key: String, secret: String): Unit = withContext(dispatcher) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(secret.encodeToByteArray())

        // The initialisation vector is generated per write and is not secret, but it is needed to
        // read the value back, so it travels in front of the ciphertext.
        preferences.edit().putString(key, (cipher.iv + sealed).encodeBase64Url()).apply()
    }

    override suspend fun get(key: String): String? = withContext(dispatcher) {
        val stored = preferences.getString(key, null) ?: return@withContext null

        // A value that will not open is gone: the key is dropped when the screen lock is removed or
        // the application is restored onto another device. Asking for a fresh sign-in is the only
        // outcome that helps.
        runCatching {
            val bytes = stored.decodeBase64Url()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES),
                )
            }
            cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES).decodeToString()
        }.getOrNull()
    }

    override suspend fun remove(key: String): Unit = withContext(dispatcher) {
        preferences.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "app.calio.secrets"
        const val PREFERENCES_NAME = "calio.secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_LENGTH_BITS = 256
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }
}
