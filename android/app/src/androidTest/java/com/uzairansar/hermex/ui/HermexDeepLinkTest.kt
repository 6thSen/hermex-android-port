package com.uzairansar.hermex.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HermexDeepLinkTest {
    @Test
    fun debugKanbanLabHasAnExplicitHiddenDeepLink() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("hermes-agent://kanban-lab"))

        assertEquals("kanban-lab", intent.hermexRoute())
    }

    @Test
    fun debugKanbanLabAcceptsOnlyKnownFixtureScenarios() {
        assertEquals(
            "kanban-lab?scenario=dense",
            Intent(Intent.ACTION_VIEW, Uri.parse("hermes-agent://kanban-lab?scenario=dense")).hermexRoute(),
        )
        assertEquals(
            "kanban-lab",
            Intent(Intent.ACTION_VIEW, Uri.parse("hermes-agent://kanban-lab?scenario=unknown")).hermexRoute(),
        )
    }

    @Test
    fun iosSessionLinkRoutesToTheAndroidChatScreen() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("hermes-agent://session?id=session 123"))

        assertEquals("chat/session%20123", intent.hermexRoute())
    }

    @Test
    fun sessionLinkAcceptsSnakeCaseIdentifiersAndServerId() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("hermes-agent://session?session_id=s-1&server_id=server-2"),
        )

        assertEquals("chat/s-1", intent.hermexRoute())
        assertEquals("server-2", intent.hermexServerId())
    }

    @Test
    fun existingAndroidChatLinkRemainsCompatible() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("hermes-agent://chat?sessionId=s-1&serverId=server-1"),
        )

        assertEquals("chat/s-1", intent.hermexRoute())
        assertEquals("server-1", intent.hermexServerId())
    }

    @Test
    fun invalidSessionLinksDoNotCreateAChatRoute() {
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("hermes-agent://session")).hermexRoute())
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("https://session?id=s-1")).hermexRoute())
    }
}
