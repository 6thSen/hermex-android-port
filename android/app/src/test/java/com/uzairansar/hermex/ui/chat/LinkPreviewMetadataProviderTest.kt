package com.uzairansar.hermex.ui.chat

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPreviewMetadataProviderTest {
    @Test
    fun parsesOpenGraphMetadataAndResolvesRelativeImages() {
        val metadata = parseLinkPreviewMetadata(
            """
            <html><head>
              <meta property="og:title" content="Hermex &amp; Android">
              <meta content="A richer preview" name="description">
              <meta property="og:image" content="/images/card.png">
              <title>Fallback title</title>
            </head></html>
            """.trimIndent(),
            "https://example.com/articles/one".toHttpUrl(),
        )

        assertEquals("Hermex & Android", metadata.title)
        assertEquals("A richer preview", metadata.description)
        assertEquals("https://example.com/images/card.png", metadata.imageUrl)
    }

    @Test
    fun fallsBackToTheDocumentTitleAndCleansMarkup() {
        val metadata = parseLinkPreviewMetadata(
            "<html><head><title>  A <b>useful</b> page  </title></head></html>",
            "https://example.com".toHttpUrl(),
        )

        assertEquals("A useful page", metadata.title)
    }
}
