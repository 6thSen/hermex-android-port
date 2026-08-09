package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.network.HermesApiClient
import okhttp3.HttpUrl

data class KanbanBrowseFilters(
    val profile: String? = null,
    val tenant: String? = null,
    val includeArchived: Boolean = false,
    val onlyMine: Boolean = false,
)

interface KanbanBrowseDataSource {
    suspend fun compatibilityHandshake(): KanbanCompatibilityReport
    suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot
    suspend fun stats(board: String): KanbanStats
    suspend fun assignees(board: String): KanbanAssigneeHistory
}

class KanbanRepository(
    private val client: HermesApiClient,
) : KanbanBrowseDataSource {
    override suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
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
        validateSnapshot(snapshot)

        val warnings = compatibilityWarnings(
            configurationReadOnly = configuration.readOnly,
            boardsReadOnly = boardsResponse.readOnly,
            boardReadOnly = currentBoard.readOnly,
            snapshot = snapshot,
            configuredColumns = configuredColumns,
        )
        return KanbanCompatibilityReport(configuration, boards, currentBoard, snapshot, warnings)
    }

    override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot =
        client.kanbanBoard(
            board = board,
            tenant = filters.tenant,
            assignee = filters.profile,
            includeArchived = filters.includeArchived,
            onlyMine = filters.onlyMine,
        ).also(::validateSnapshot)

    override suspend fun stats(board: String): KanbanStats = client.kanbanStats(board)
    override suspend fun assignees(board: String): KanbanAssigneeHistory = client.kanbanAssignees(board)
    suspend fun events(board: String, since: Int, limit: Int = 200): KanbanEventsEnvelope =
        client.kanbanEvents(board, since, limit)
    fun eventsStreamUrl(board: String, since: Int): HttpUrl = client.kanbanEventsStreamUrl(board, since)

    private fun validateSnapshot(snapshot: KanbanBoardSnapshot) {
        val columns = snapshot.columns
        if (snapshot.changed != true || columns.isNullOrEmpty()) throw KanbanContractViolation.MissingBoardSnapshot
        if (columns.any { it.name.isNullOrBlank() }) throw KanbanContractViolation.MissingColumnStatus
        val cards = columns.flatMap { it.cards.orEmpty() }
        if (cards.any { it.cardId.isNullOrBlank() }) throw KanbanContractViolation.MissingCardIdentity
        if (cards.any { it.status.isNullOrBlank() }) throw KanbanContractViolation.MissingCardStatus
    }

    private fun compatibilityWarnings(
        configurationReadOnly: Boolean?,
        boardsReadOnly: Boolean?,
        boardReadOnly: Boolean?,
        snapshot: KanbanBoardSnapshot,
        configuredColumns: List<String>,
    ): List<KanbanCompatibilityWarning> {
        val configuredStatuses = configuredColumns.map { it.lowercase() }.toSet()
        return buildList {
            if (configurationReadOnly == true || boardsReadOnly == true || boardReadOnly == true || snapshot.readOnly == true) {
                add(KanbanCompatibilityWarning.ReadOnly)
            }
            if (configurationReadOnly == null || boardsReadOnly == null || boardReadOnly == null || snapshot.readOnly == null) {
                add(KanbanCompatibilityWarning.WriteCapabilityUnavailable)
            }
            snapshot.columns.orEmpty().flatMap { column ->
                buildList {
                    column.name?.trim()?.lowercase()?.let(::add)
                    addAll(column.cards.orEmpty().mapNotNull { it.status?.trim()?.lowercase() })
                }
            }
                .filterNot(configuredStatuses::contains)
                .distinct()
                .forEach { add(KanbanCompatibilityWarning.UnsupportedStatus(it)) }
        }
    }
}
