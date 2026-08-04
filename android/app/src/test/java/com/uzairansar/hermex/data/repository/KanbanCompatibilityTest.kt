package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanBoardsResponse
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanCompatibilityTest {
    @Test
    fun handshakeUsesServerCurrentBoardAndRetainsUnknownStatusesAsWarnings() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"columns":["triage","future"],"assignees":["one",{"name":"two"}]}"""))
            server.enqueue(json("""{"current":"main","boards":[{"slug":"main","name":"Main"}]}"""))
            server.enqueue(
                json(
                    """
                    {
                      "columns": [
                        {"name":"triage","tasks":[{"id":"c1","title":"Known","status":"triage"}]},
                        {"name":"future","tasks":[{"id":"c2","title":"Future","status":"future"}]}
                      ],
                      "read_only": true,
                      "future_envelope": "ignored"
                    }
                    """.trimIndent(),
                ),
            )
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val report = repository.compatibilityHandshake()

            assertEquals("main", report.currentBoard.slug)
            assertEquals(listOf("one", "two"), report.configuration.assigneeNames)
            assertTrue(report.warnings.contains(KanbanCompatibilityWarning.ReadOnly))
            assertTrue(report.warnings.contains(KanbanCompatibilityWarning.UnsupportedStatus("future")))
            assertEquals("/api/kanban/config", server.takeRequest().url.encodedPath)
            assertEquals("/api/kanban/boards", server.takeRequest().url.encodedPath)
            val boardRequest = server.takeRequest()
            assertEquals("/api/kanban/board", boardRequest.url.encodedPath)
            assertEquals("main", boardRequest.url.queryParameter("board"))
        } finally {
            server.close()
        }
    }

    @Test
    fun handshakeRejectsCardsWithoutIdentity() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"columns":["todo"]}"""))
            server.enqueue(json("""{"current":"main","boards":[{"slug":"main"}]}"""))
            server.enqueue(json("""{"columns":[{"name":"todo","tasks":[{"status":"todo"}]}]}"""))
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val error = runCatching { repository.compatibilityHandshake() }.exceptionOrNull()

            assertTrue(error is KanbanContractViolation.MissingCardIdentity)
        } finally {
            server.close()
        }
    }

    @Test
    fun kanbanConfigurationDropsOversizedOptionalCountsWithoutRejectingResponse() {
        val decoded = HermesJson.decodeFromString<KanbanConfiguration>(
            """{"columns":["todo"],"assignees":["one"],"future":999999999999}""",
        )

        assertEquals(listOf("todo"), decoded.columns)
        assertEquals(listOf("one"), decoded.assigneeNames)
    }

    @Test
    fun malformedOptionalKanbanMembersDoNotRejectOtherwiseUsableEnvelopes() {
        val config = HermesJson.decodeFromString<KanbanConfiguration>(
            """{"columns":["todo",42,null],"assignees":["one",42,{"name":"two"}]}""",
        )
        val boards = HermesJson.decodeFromString<KanbanBoardsResponse>(
            """{"current":"main","boards":["bad",{"slug":"main","counts":{"todo":2,"future":999999999999}}]}""",
        )

        assertEquals(listOf("todo"), config.columns)
        assertEquals(listOf("one", "two"), config.assigneeNames)
        assertEquals(listOf("main"), boards.boards.orEmpty().map { it.slug })
        assertEquals(mapOf("todo" to 2), boards.boards?.single()?.counts)
    }

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
