package com.uzairansar.hermex.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class KanbanAvailability {
    Loading,
    Content,
    AuthenticationRequired,
    NetworkUnavailable,
    ServerUnavailable,
    IncompatibleContract,
}

internal data class KanbanFilterState(
    val profile: String? = null,
    val tenant: String? = null,
    val includeArchived: Boolean = false,
    val onlyMine: Boolean = false,
    val groupByProfile: Boolean = false,
) {
    val hasServerFilters: Boolean
        get() = profile != null || tenant != null || includeArchived || onlyMine

    fun request(): KanbanBrowseFilters = KanbanBrowseFilters(
        profile = profile.takeUnless { onlyMine },
        tenant = tenant,
        includeArchived = includeArchived,
        onlyMine = onlyMine,
    )
}

internal data class KanbanLabUiState(
    val availability: KanbanAvailability = KanbanAvailability.Loading,
    val configuration: KanbanConfiguration? = null,
    val boards: List<KanbanBoardSummary> = emptyList(),
    val selectedBoardSlug: String? = null,
    val snapshot: KanbanBoardSnapshot? = null,
    val stats: KanbanStats? = null,
    val assigneeHistory: KanbanAssigneeHistory? = null,
    val warnings: List<KanbanCompatibilityWarning> = emptyList(),
    val filters: KanbanFilterState = KanbanFilterState(),
    val selectedStatus: String = "triage",
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    val selectedBoard: KanbanBoardSummary?
        get() = boards.firstOrNull { it.slug?.trim() == selectedBoardSlug }

    val availableStatuses: List<String>
        get() = availableKanbanStatuses(snapshot, filters.includeArchived)

    val visibleCards
        get() = visibleKanbanCards(snapshot, selectedStatus, searchQuery)

    val profileOptions: List<String>
        get() = kanbanProfileOptions(configuration, snapshot, assigneeHistory?.names.orEmpty())

    val tenantOptions: List<String>
        get() = kanbanTenantOptions(snapshot)

    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() || filters.hasServerFilters
}

internal class KanbanLabViewModel(
    private val repository: KanbanBrowseDataSource,
) : ViewModel() {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(KanbanLabUiState())
    val state: kotlinx.coroutines.flow.StateFlow<KanbanLabUiState> = mutableState

    private var loadGeneration = 0

    init {
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        val previous = mutableState.value
        mutableState.value = if (previous.availability == KanbanAvailability.Content) {
            previous.copy(isRefreshing = true, refreshFailed = false)
        } else {
            KanbanLabUiState(availability = KanbanAvailability.Loading)
        }
        viewModelScope.launch {
            try {
                val report = repository.compatibilityHandshake()
                val previousFilters = previous.filters
                val filters = if (previous.availability == KanbanAvailability.Content) {
                    previousFilters
                } else {
                    previousFilters.copy(includeArchived = report.configuration.includeArchivedByDefault == true)
                }
                val previousSlug = previous.selectedBoardSlug
                val selectedSlug = previousSlug
                    ?.takeIf { slug -> report.boards.any { it.slug?.trim() == slug } }
                    ?: report.currentBoard.slug?.trim().orEmpty()
                val needsFilteredSnapshot = selectedSlug != report.currentBoard.slug?.trim() || filters.hasServerFilters
                val snapshot = if (needsFilteredSnapshot) {
                    repository.boardSnapshot(selectedSlug, filters.request())
                } else {
                    report.snapshot
                }
                val supplementary = loadSupplementary(selectedSlug)
                if (generation != loadGeneration) return@launch
                val statuses = availableKanbanStatuses(snapshot, filters.includeArchived)
                val selectedStatus = previous.selectedStatus.takeIf(statuses::contains)
                    ?: "triage".takeIf(statuses::contains)
                    ?: statuses.firstOrNull().orEmpty()
                mutableState.value = KanbanLabUiState(
                    availability = KanbanAvailability.Content,
                    configuration = report.configuration,
                    boards = report.boards,
                    selectedBoardSlug = selectedSlug,
                    snapshot = snapshot,
                    stats = supplementary.first,
                    assigneeHistory = supplementary.second,
                    warnings = warningsFor(report, selectedSlug, snapshot),
                    filters = filters,
                    selectedStatus = selectedStatus,
                    searchQuery = previous.searchQuery,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation != loadGeneration) return@launch
                mutableState.value = if (previous.availability == KanbanAvailability.Content) {
                    previous.copy(isRefreshing = false, refreshFailed = true)
                } else {
                    KanbanLabUiState(availability = kanbanAvailabilityFor(error))
                }
            }
        }
    }

    fun selectBoard(slug: String) {
        val normalized = slug.trim().takeIf(String::isNotEmpty) ?: return
        if (normalized == mutableState.value.selectedBoardSlug) return
        loadBoard(normalized, mutableState.value.filters)
    }

    fun applyFilters(filters: KanbanFilterState) {
        val state = mutableState.value
        val normalized = filters.copy(
            profile = filters.profile.normalizedFilterValue().takeUnless { filters.onlyMine },
            tenant = filters.tenant.normalizedFilterValue(),
        )
        if (normalized.request() == state.filters.request()) {
            mutableState.value = state.copy(filters = normalized)
            return
        }
        val slug = state.selectedBoardSlug ?: return
        loadBoard(slug, normalized)
    }

    fun clearFilters() {
        mutableState.value = mutableState.value.copy(searchQuery = "")
        val current = mutableState.value.filters
        applyFilters(KanbanFilterState(groupByProfile = current.groupByProfile))
    }

    fun setSearchQuery(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
    }

    fun selectStatus(status: String) {
        if (status in mutableState.value.availableStatuses) {
            mutableState.value = mutableState.value.copy(selectedStatus = status)
        }
    }

    private fun loadBoard(slug: String, filters: KanbanFilterState) {
        val previous = mutableState.value
        val generation = ++loadGeneration
        mutableState.value = previous.copy(isRefreshing = true, refreshFailed = false)
        viewModelScope.launch {
            try {
                val snapshot = repository.boardSnapshot(slug, filters.request())
                val supplementary = loadSupplementary(slug)
                if (generation != loadGeneration) return@launch
                val statuses = availableKanbanStatuses(snapshot, filters.includeArchived)
                val selectedStatus = previous.selectedStatus
                    .takeIf { it in statuses && (it != "archived" || filters.includeArchived) }
                    ?: "triage".takeIf(statuses::contains)
                    ?: statuses.firstOrNull().orEmpty()
                val report = KanbanCompatibilityReport(
                    configuration = requireNotNull(previous.configuration),
                    boards = previous.boards,
                    currentBoard = previous.selectedBoard ?: previous.boards.first(),
                    snapshot = snapshot,
                    warnings = previous.warnings,
                )
                mutableState.value = previous.copy(
                    availability = KanbanAvailability.Content,
                    selectedBoardSlug = slug,
                    snapshot = snapshot,
                    stats = supplementary.first,
                    assigneeHistory = supplementary.second,
                    warnings = warningsFor(report, slug, snapshot),
                    filters = filters,
                    selectedStatus = selectedStatus,
                    isRefreshing = false,
                    refreshFailed = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (generation == loadGeneration) {
                    mutableState.value = previous.copy(isRefreshing = false, refreshFailed = true)
                }
            }
        }
    }

    private suspend fun loadSupplementary(slug: String): Pair<KanbanStats?, KanbanAssigneeHistory?> = coroutineScope {
        val stats = async { runCatching { repository.stats(slug) }.getOrNull() }
        val assignees = async { runCatching { repository.assignees(slug) }.getOrNull() }
        stats.await() to assignees.await()
    }

    private fun warningsFor(
        report: KanbanCompatibilityReport,
        selectedSlug: String,
        snapshot: KanbanBoardSnapshot,
    ): List<KanbanCompatibilityWarning> {
        val configured = report.configuration.columns.orEmpty()
            .mapNotNull { it.trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty) }
            .toSet()
        val selectedBoard = report.boards.firstOrNull { it.slug?.trim() == selectedSlug }
        return buildList {
            addAll(report.warnings.filterNot { it is KanbanCompatibilityWarning.UnsupportedStatus })
            if (selectedBoard?.readOnly == true || snapshot.readOnly == true) add(KanbanCompatibilityWarning.ReadOnly)
            if (selectedBoard?.readOnly == null || snapshot.readOnly == null) {
                add(KanbanCompatibilityWarning.WriteCapabilityUnavailable)
            }
            snapshot.columns.orEmpty().flatMap { column ->
                listOfNotNull(column.name?.trim()?.lowercase(Locale.ROOT)) +
                    column.cards.orEmpty().mapNotNull { it.status?.trim()?.lowercase(Locale.ROOT) }
            }.filterNot(configured::contains).distinct().forEach { status ->
                add(KanbanCompatibilityWarning.UnsupportedStatus(status))
            }
        }.distinct()
    }
}

internal fun kanbanAvailabilityFor(error: Throwable): KanbanAvailability = when (error) {
    ApiError.Unauthorized -> KanbanAvailability.AuthenticationRequired
    is IOException -> KanbanAvailability.NetworkUnavailable
    is ApiError.Http -> if (error.statusCode >= 500) {
        KanbanAvailability.ServerUnavailable
    } else {
        KanbanAvailability.IncompatibleContract
    }
    is KanbanContractViolation,
    is kotlinx.serialization.SerializationException,
    -> KanbanAvailability.IncompatibleContract
    else -> KanbanAvailability.ServerUnavailable
}

private fun String?.normalizedFilterValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)
