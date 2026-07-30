package com.uzairansar.hermex.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

data class LinkPreviewMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val imageBytes: ByteArray? = null,
)

object LinkPreviewMetadataProvider {
    private const val MAX_HTML_BYTES = 256L * 1_024L
    private const val MAX_IMAGE_BYTES = 2L * 1_024L * 1_024L
    private const val MAX_CACHE_ENTRIES = 64
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val cache = object : LinkedHashMap<String, LinkPreviewMetadata>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkPreviewMetadata>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    suspend fun metadata(url: HttpUrl): LinkPreviewMetadata = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[url.toString()] }?.let { return@withContext it }
        val metadata = runCatching { fetch(url) }.getOrDefault(LinkPreviewMetadata())
        synchronized(cache) { cache[url.toString()] = metadata }
        metadata
    }

    private fun fetch(url: HttpUrl): LinkPreviewMetadata {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", "Hermex-Android-LinkPreview/1.0")
            .build()
        val parsed = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return LinkPreviewMetadata()
            val body = response.body
            val type = body.contentType()?.toString().orEmpty()
            if (!type.contains("html", ignoreCase = true)) return LinkPreviewMetadata()
            val bytes = body.source().readByteArray(MAX_HTML_BYTES + 1)
            if (bytes.size > MAX_HTML_BYTES) return LinkPreviewMetadata()
            parseLinkPreviewMetadata(bytes.toString(Charsets.UTF_8), response.request.url)
        }
        val imageBytes = parsed.imageUrl
            ?.toHttpUrlOrNull()
            ?.let(::fetchImage)
        return parsed.copy(imageBytes = imageBytes)
    }

    private fun fetchImage(url: HttpUrl): ByteArray? {
        val request = Request.Builder().url(url).header("Accept", "image/*").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body.contentType()?.type != "image") return null
            val bytes = response.body.source().readByteArray(MAX_IMAGE_BYTES + 1)
            bytes.takeIf { it.size <= MAX_IMAGE_BYTES }
        }
    }
}

internal fun parseLinkPreviewMetadata(html: String, baseUrl: HttpUrl): LinkPreviewMetadata {
    val meta = linkedMapOf<String, String>()
    META_TAG.findAll(html).forEach { tagMatch ->
        val attributes = ATTRIBUTE.findAll(tagMatch.value).associate { match ->
            match.groupValues[1].lowercase() to decodeHtmlText(match.groupValues[3])
        }
        val key = (attributes["property"] ?: attributes["name"])?.lowercase()
        val content = attributes["content"]?.trim()?.takeIf { it.isNotEmpty() }
        if (key != null && content != null && key !in meta) meta[key] = content
    }
    val title = sequenceOf(meta["og:title"], meta["twitter:title"], TITLE.find(html)?.groupValues?.get(1))
        .mapNotNull { it?.let(::decodeHtmlText)?.cleanPreviewText() }
        .firstOrNull()
    val description = sequenceOf(meta["og:description"], meta["twitter:description"], meta["description"])
        .mapNotNull { it?.cleanPreviewText() }
        .firstOrNull()
    val image = sequenceOf(meta["og:image"], meta["twitter:image"], meta["twitter:image:src"])
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .mapNotNull(baseUrl::resolve)
        .firstOrNull()
        ?.toString()
    return LinkPreviewMetadata(title = title, description = description, imageUrl = image)
}

private fun String.cleanPreviewText(): String? =
    replace(TAG, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)
        .takeIf { it.isNotEmpty() }

private fun decodeHtmlText(value: String): String = value
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&apos;", "'", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)

private val META_TAG = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ATTRIBUTE = Regex("([A-Za-z_:.-]+)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.DOT_MATCHES_ALL))
private val TITLE = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val TAG = Regex("<[^>]+>")
