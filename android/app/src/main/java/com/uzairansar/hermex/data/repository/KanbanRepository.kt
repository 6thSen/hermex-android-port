package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.supportedKanbanStatuses
import com.uzairansar.hermex.core.network.HermesApiClient
import okhttp3.HttpUrl

class KanbanRepository(
    private val client: HermesApiClient,
) {
    suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
        val configuration = client.kanbanConfiguration()
        val configuredColumns = configuration.columns.orEmpty().map(String::trim).filter(String::isNotEmpty)
        if (configuredColumns.isEmpty()) throw KanbanContractViolation.MissingConfigurationColumns

        val boardsResponse = client.kanbanBoards()
        val boards = boardsResponse.boards.orEmpty()
        if (boards.any { it.slug.isNullOrBlank() }) throw KanbanContractViolation.MissingBoardIdentity
        val currentSlug = boardsResponse.current?.trim()?.takeIf(String::isNotEmpty)
            ?: boards.firstOrNull { it.isCurrent == true }?.slug?.trim()?.takeIf(String::isNotEmpty)
            ?: throw KanbanContractViolation.MissingCurrentBoard
        val currentBoard = boards.firstOrNull { it.slug?.trim() == currentSlug }
            ?: throw KanbanContractViolation.MissingCurrentBoard

        val snapshot = client.kanbanBoard(currentSlug)
        val columns = snapshot.columns ?: throw KanbanContractViolation.MissingBoardSnapshot
        if (columns.any { it.name.isNullOrBlank() }) throw KanbanContractViolation.MissingColumnStatus
        val cards = columns.flatMap { it.cards.orEmpty() }
        if (cards.any { it.cardId.isNullOrBlank() }) throw KanbanContractViolation.MissingCardIdentity
        if (cards.any { it.status.isNullOrBlank() }) throw KanbanContractViolation.MissingCardStatus

        val warnings = buildList {
            if (configuration.readOnly == true || boardsResponse.readOnly == true || currentBoard.readOnly == true || snapshot.readOnly == true) {
                add(KanbanCompatibilityWarning.ReadOnly)
            }
            cards.mapNotNull { it.status?.trim()?.lowercase() }
                .filterNot(supportedKanbanStatuses::contains)
                .distinct()
                .forEach { add(KanbanCompatibilityWarning.UnsupportedStatus(it)) }
        }
        return KanbanCompatibilityReport(configuration, boards, currentBoard, snapshot, warnings)
    }

    suspend fun stats(board: String): KanbanStats = client.kanbanStats(board)
    suspend fun assignees(board: String): KanbanAssigneeHistory = client.kanbanAssignees(board)
    suspend fun events(board: String, since: Int, limit: Int = 200): KanbanEventsEnvelope =
        client.kanbanEvents(board, since, limit)
    fun eventsStreamUrl(board: String, since: Int): HttpUrl = client.kanbanEventsStreamUrl(board, since)
}
