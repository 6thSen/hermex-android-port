package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.data.secure.SecretStore
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    private val secretStore: SecretStore,
) : CookieJar {
    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val existing = readAll(url).filterNot { stored ->
                cookies.any { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            }
            val updated = existing + cookies.map(CookieRecord::from)
            persist(updated)
            secretStore.remove(legacyKeyFor(url))
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val records = readAll(url)
        val fresh = records.filter { it.expiresAt == null || it.expiresAt > now }
        if (fresh.size != records.size || read(legacyKeyFor(url)).isNotEmpty()) {
            persist(fresh)
            secretStore.remove(legacyKeyFor(url))
        }
        fresh.mapNotNull { it.toCookie() }.filter { it.matches(url) }
    }

    fun clear(url: HttpUrl) {
        synchronized(lock) {
            val host = url.host.lowercase()
            persist(readAll(url).filterNot { record ->
                val domain = record.domain.lowercase().trimStart('.')
                host == domain || host.endsWith(".$domain") || domain.endsWith(".$host")
            })
            secretStore.remove(legacyKeyFor(url))
        }
    }

    private fun readAll(url: HttpUrl): List<CookieRecord> =
        (read(COOKIE_STORE_KEY) + read(legacyKeyFor(url)))
            .distinctBy { record -> Triple(record.name, record.domain, record.path) }

    private fun persist(records: List<CookieRecord>) {
        if (records.isEmpty()) secretStore.remove(COOKIE_STORE_KEY)
        else secretStore.putString(COOKIE_STORE_KEY, HermesJson.encodeToString(records))
    }

    private fun read(key: String): List<CookieRecord> =
        secretStore.getString(key)
            ?.let { runCatching { HermesJson.decodeFromString<List<CookieRecord>>(it) }.getOrNull() }
            .orEmpty()

    private fun legacyKeyFor(url: HttpUrl): String = "cookies::${ServerOrigin.from(url)}"

    private companion object {
        const val COOKIE_STORE_KEY = "cookies::all"
    }
}

private object ServerOrigin {
    fun from(url: HttpUrl): String = url.newBuilder()
        .encodedPath("/")
        .encodedQuery(null)
        .fragment(null)
        .build()
        .toString()
}

@Serializable
private data class CookieRecord(
    val name: String,
    val value: String,
    val expiresAt: Long? = null,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .apply {
                expiresAt?.let { expiresAt(it) }
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                path(path)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        fun from(cookie: Cookie): CookieRecord = CookieRecord(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt.takeIf { it != Long.MAX_VALUE },
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}
