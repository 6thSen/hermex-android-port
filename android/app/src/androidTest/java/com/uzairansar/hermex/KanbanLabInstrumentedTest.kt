package com.uzairansar.hermex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.ui.kanban.KanbanLabRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanbanLabInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readOnlyStatusFocusBrowsesFiltersAndSwitchesBoardsLocally() {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val server = MockWebServer().also { mockServer ->
            mockServer.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    return when (request.url.encodedPath) {
                        "/api/kanban/config" -> json(
                            """{"columns":["triage","todo","blocked","ready","running","done"],"assignees":["builder","reviewer"],"read_only":false}""",
                        )
                        "/api/kanban/boards" -> json(
                            """{"current":"main","read_only":false,"boards":[{"slug":"main","name":"Main Board","read_only":false},{"slug":"release","name":"Release Board","read_only":false}]}""",
                        )
                        "/api/kanban/board" -> if (request.url.queryParameter("board") == "release") {
                            json(
                                """{"changed":true,"read_only":false,"tenants":["app"],"assignees":["reviewer"],"columns":[{"name":"todo","tasks":[{"id":"CARD-release","title":"Prepare release","status":"todo","assignee":"reviewer","tenant":"app","priority":1,"age_seconds":300}]}]}""",
                            )
                        } else {
                            json(
                                """
                                {
                                  "changed": true,
                                  "read_only": false,
                                  "tenants": ["app", "infra"],
                                  "assignees": ["builder", "reviewer"],
                                  "columns": [
                                    {"name":"triage","tasks":[{"id":"CARD-1","title":"Triage mobile","body":"## Summary\nNeeds review","status":"triage","assignee":"builder","tenant":"app","priority":2,"comment_count":1,"age_seconds":120}]},
                                    {"name":"ready","tasks":[{"id":"CARD-2","title":"Ship Android","body":"- Verify bundle\n- Publish prerelease","status":"ready","assignee":"reviewer","tenant":"app","priority":0,"comment_count":4,"link_counts":{"parents":1,"children":2},"age_seconds":3600}]},
                                    {"name":"future","tasks":[{"id":"CARD-3","title":"Future workflow","status":"future","assignee":null,"tenant":"infra"}]}
                                  ]
                                }
                                """.trimIndent(),
                            )
                        }
                        "/api/kanban/stats" -> json("""{"total":3,"by_status":{"triage":1,"ready":1,"future":1}}""")
                        "/api/kanban/assignees" -> json("""{"assignees":["builder","reviewer"]}""")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            mockServer.start()
        }
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(application)

        try {
            composeRule.setContent {
                HermexTheme {
                    KanbanLabRoute(
                        repository = container.kanbanRepository(server.url("/")),
                        onBack = {},
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Main Board").assertIsDisplayed()
            composeRule.onNodeWithText("Unknown Status: future").assertIsDisplayed()
            composeRule.onNodeWithTag("kanban_status_ready").performClick()
            composeRule.onNodeWithText("CARD-2").assertIsDisplayed()
            composeRule.onNodeWithTag("kanban_search").performTextInput("reviewer")
            composeRule.onNodeWithText("Ship Android").assertIsDisplayed()

            composeRule.onNodeWithTag("kanban_filters").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("kanban_filter_profile").performClick()
            composeRule.onAllNodesWithText("reviewer")[1].performClick()
            composeRule.onNodeWithText("Group by Profile").performClick()
            composeRule.onNodeWithText("Apply").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                requests.any { request ->
                    request.url.encodedPath == "/api/kanban/board" && request.url.queryParameter("assignee") == "reviewer"
                }
            }
            composeRule.onNodeWithTag("kanban_profile_group_reviewer").assertIsDisplayed()

            composeRule.onNodeWithTag("kanban_board_picker").performClick()
            composeRule.onNodeWithText("Release Board").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                requests.any { request ->
                    request.url.encodedPath == "/api/kanban/board" &&
                        request.url.queryParameter("board") == "release" &&
                        request.url.queryParameter("assignee") == "reviewer"
                }
            }
            composeRule.onNodeWithTag("kanban_status_todo").performClick()
            composeRule.onNodeWithText("CARD-release").assertIsDisplayed()

            assertTrue(requests.any { it.url.encodedPath == "/api/kanban/config" })
            assertTrue(requests.any { it.url.encodedPath == "/api/kanban/boards" })
            assertFalse(requests.any { it.url.encodedPath.endsWith("/switch") })
        } finally {
            server.close()
        }
    }

    @Test
    fun incompatibleHandshakeIsDistinctAndRetryable() {
        val server = MockWebServer().also {
            it.enqueue(json("""{"columns":[],"read_only":false}"""))
            it.start()
        }
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(application)

        try {
            composeRule.setContent {
                HermexTheme {
                    KanbanLabRoute(container.kanbanRepository(server.url("/")), onBack = {})
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("kanban_unavailable_IncompatibleContract").assertIsDisplayed()
            composeRule.onNodeWithText("Retry").assertIsDisplayed()
        } finally {
            server.close()
        }
    }

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
