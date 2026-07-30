package app.calio.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemorySecretStoreTest {

    private val store = InMemorySecretStore()

    @Test
    fun `what was put in comes back out`() = runTest {
        store.put("oauth:account-1", "refresh-token")

        assertEquals("refresh-token", store.get("oauth:account-1"))
    }

    @Test
    fun `an unknown key has no secret`() = runTest {
        assertNull(store.get("oauth:nobody"))
    }

    @Test
    fun `writing again replaces the secret`() = runTest {
        store.put("oauth:account-1", "first")
        store.put("oauth:account-1", "second")

        assertEquals("second", store.get("oauth:account-1"))
    }

    @Test
    fun `removing leaves nothing behind`() = runTest {
        store.put("oauth:account-1", "refresh-token")

        store.remove("oauth:account-1")

        assertNull(store.get("oauth:account-1"))
    }

    @Test
    fun `removing a key that was never there is not an error`() = runTest {
        store.remove("oauth:nobody")
    }
}
