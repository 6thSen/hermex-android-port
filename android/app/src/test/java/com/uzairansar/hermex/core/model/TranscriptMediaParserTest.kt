package com.uzairansar.hermex.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMediaParserTest {
    @Test
    fun liftsMediaReferencesOutsideFencedCode() {
        val segments = TranscriptMediaParser.segments(
            """
            Before MEDIA:/tmp/chart.png.
            ```text
            MEDIA:/tmp/inside-code.png
            ```
            After MEDIA:https://example.com/plot.webp!
            """.trimIndent(),
        )

        val media = segments.filterIsInstance<TranscriptMediaSegment.Media>()
        assertEquals("/tmp/chart.png", media[0].reference.rawReference)
        assertEquals("https://example.com/plot.webp", media[1].reference.rawReference)
        assertEquals(2, media.size)
        assertTrue(segments.filterIsInstance<TranscriptMediaSegment.Text>().any { it.text.contains("MEDIA:/tmp/inside-code.png") })
    }

    @Test
    fun classifiesRasterCandidatesLikeIos() {
        assertTrue(TranscriptMediaReference("/tmp/image.jpeg").isRasterImageCandidate)
        assertTrue(TranscriptMediaReference("https://example.com/render").isRasterImageCandidate)
        assertFalse(TranscriptMediaReference("/tmp/archive.zip").isRasterImageCandidate)
        assertEquals("image.jpeg", TranscriptMediaReference("/tmp/image.jpeg").displayName)
    }

    @Test
    fun shorterOrInfoBearingFenceDoesNotCloseACommonMarkCodeBlock() {
        val segments = TranscriptMediaParser.segments(
            """
            ````text
            ```
            MEDIA:/tmp/still-code.png
            ```` not-a-close
            MEDIA:/tmp/also-code.png
            ````
            MEDIA:/tmp/outside.png
            """.trimIndent(),
        )

        val media = segments.filterIsInstance<TranscriptMediaSegment.Media>()
        assertEquals(listOf("/tmp/outside.png"), media.map { it.reference.rawReference })
    }
}
