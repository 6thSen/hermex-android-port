package com.uzairansar.hermex.data.secure

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerRegistrySafetyTest {
    @Test
    fun corruptRegistryIsReadOnlyUntilTheUserResetsSecureStorage() {
        val store = MutableSecretStore(mutableMapOf("servers" to "not-json"))
        val registry = ServerRegistry(store)

        assertNotNull(registry.loadFailure)
        assertEquals(emptyList<ServerAccount>(), registry.snapshot.value.servers)
        assertThrows(IllegalStateException::class.java) {
            registry.activate("https://example.com".toHttpUrl())
        }
        assertEquals("not-json", store.values["servers"])
    }

    @Test
    fun corruptCustomHeadersMakeTheRegistryRecoverableInsteadOfSilentlyDroppingThem() {
        val store = MutableSecretStore()
        val account = ServerRegistry(store).activate("https://example.com".toHttpUrl())
        store.values["custom_headers::${account.id}"] = "not-json"

        val restored = ServerRegistry(store)

        assertNotNull(restored.loadFailure)
        assertThrows(IllegalStateException::class.java) {
            restored.setActive(account.id)
        }
    }
}

private class MutableSecretStore(
    val values: MutableMap<String, String> = mutableMapOf(),
) : SecretStore {
    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clearPrefix(prefix: String) {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
    }
}
