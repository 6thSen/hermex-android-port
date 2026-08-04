package com.uzairansar.hermex.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class KanbanConfiguration(
    @Serializable(with = LossyNullableStringListSerializer::class)
    val columns: List<String>? = null,
    @Serializable(with = LossyNullableKanbanAssigneeListSerializer::class)
    val assignees: List<KanbanAssigneeValue>? = null,
    @SerialName("default_tenant") val defaultTenant: String? = null,
    @SerialName("lane_by_profile") val laneByProfile: Boolean? = null,
    @SerialName("include_archived_by_default") val includeArchivedByDefault: Boolean? = null,
    @SerialName("render_markdown") val renderMarkdown: Boolean? = null,
    @SerialName("read_only") val readOnly: Boolean? = null,
) {
    val assigneeNames: List<String>
        get() = assignees.orEmpty().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.distinct()
}

@Serializable(with = KanbanAssigneeValueSerializer::class)
data class KanbanAssigneeValue(
    val name: String? = null,
)

object KanbanAssigneeValueSerializer : KSerializer<KanbanAssigneeValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("KanbanAssigneeValue") {
        element<String?>("name")
    }

    override fun deserialize(decoder: Decoder): KanbanAssigneeValue {
        val jsonDecoder = decoder as? JsonDecoder ?: return KanbanAssigneeValue()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> KanbanAssigneeValue(element.takeIf { it.isString }?.contentOrNull)
            is JsonObject -> KanbanAssigneeValue(element["name"]?.jsonPrimitive?.contentOrNull)
            else -> KanbanAssigneeValue()
        }
    }

    override fun serialize(encoder: Encoder, value: KanbanAssigneeValue) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(value.name?.let(::JsonPrimitive) ?: JsonNull)
    }
}

@Serializable
data class KanbanBoardsResponse(
    @Serializable(with = LossyNullableKanbanBoardListSerializer::class)
    val boards: List<KanbanBoardSummary>? = null,
    val current: String? = null,
    @SerialName("read_only") val readOnly: Boolean? = null,
)

@Serializable
data class KanbanBoardSummary(
    val slug: String? = null,
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("is_current") val isCurrent: Boolean? = null,
    @Serializable(with = LossyNullableIntSerializer::class)
    val total: Int? = null,
    @Serializable(with = LossyNullableIntMapSerializer::class)
    val counts: Map<String, Int>? = null,
    @SerialName("read_only") val readOnly: Boolean? = null,
)

@Serializable
data class KanbanBoardSnapshot(
    @Serializable(with = LossyNullableKanbanColumnListSerializer::class)
    val columns: List<KanbanColumn>? = null,
    @Serializable(with = LossyNullableStringListSerializer::class)
    val tenants: List<String>? = null,
    @Serializable(with = LossyNullableStringListSerializer::class)
    val assignees: List<String>? = null,
    val filters: KanbanAppliedFilters? = null,
    val changed: Boolean? = null,
    @SerialName("latest_event_id")
    @Serializable(with = LossyNullableIntSerializer::class)
    val latestEventId: Int? = null,
    @SerialName("read_only") val readOnly: Boolean? = null,
)

@Serializable
data class KanbanColumn(
    val name: String? = null,
    @SerialName("tasks")
    @Serializable(with = LossyNullableKanbanCardListSerializer::class)
    val cards: List<KanbanCardSummary>? = null,
)

@Serializable
data class KanbanCardSummary(
    @SerialName("id") val cardId: String? = null,
    val title: String? = null,
    val status: String? = null,
    val assignee: String? = null,
    val body: String? = null,
    val tenant: String? = null,
    @Serializable(with = LossyNullableIntSerializer::class)
    val priority: Int? = null,
    @SerialName("comment_count")
    @Serializable(with = LossyNullableIntSerializer::class)
    val commentCount: Int? = null,
    @SerialName("link_counts") val linkCounts: KanbanLinkCounts? = null,
    @SerialName("age_seconds") val ageSeconds: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("workspace_kind") val workspaceKind: String? = null,
    @SerialName("workspace_path") val workspacePath: String? = null,
    val skills: List<String>? = null,
    @SerialName("max_runtime_seconds")
    @Serializable(with = LossyNullableIntSerializer::class)
    val maxRuntimeSeconds: Int? = null,
) {
    val hasSupportedStatus: Boolean
        get() = status?.trim()?.lowercase() in supportedKanbanStatuses
}

@Serializable
data class KanbanLinkCounts(
    @Serializable(with = LossyNullableIntSerializer::class)
    val parents: Int? = null,
    @Serializable(with = LossyNullableIntSerializer::class)
    val children: Int? = null,
)

@Serializable
data class KanbanAppliedFilters(
    val tenant: String? = null,
    val assignee: String? = null,
    @SerialName("include_archived") val includeArchived: Boolean? = null,
    @SerialName("only_mine") val onlyMine: Boolean? = null,
    val profile: String? = null,
)

@Serializable
data class KanbanStats(
    @Serializable(with = LossyNullableIntSerializer::class)
    val total: Int? = null,
    @SerialName("by_status")
    @Serializable(with = LossyNullableIntMapSerializer::class)
    val byStatus: Map<String, Int>? = null,
    @SerialName("by_assignee")
    @Serializable(with = LossyNullableIntMapSerializer::class)
    val byAssignee: Map<String, Int>? = null,
)

@Serializable
data class KanbanAssigneeHistory(
    @Serializable(with = LossyNullableKanbanAssigneeListSerializer::class)
    val assignees: List<KanbanAssigneeValue>? = null,
) {
    val names: List<String>
        get() = assignees.orEmpty().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.distinct()
}

@Serializable
data class KanbanEventsEnvelope(
    val events: List<KanbanEvent>? = null,
    @Serializable(with = LossyNullableIntSerializer::class)
    val cursor: Int? = null,
    @SerialName("latest_event_id")
    @Serializable(with = LossyNullableIntSerializer::class)
    val latestEventId: Int? = null,
    @SerialName("read_only") val readOnly: Boolean? = null,
)

@Serializable
data class KanbanEvent(
    @SerialName("id")
    @Serializable(with = LossyNullableIntSerializer::class)
    val eventId: Int? = null,
    @SerialName("task_id") val cardId: String? = null,
    @SerialName("run_id") val runId: String? = null,
    val kind: String? = null,
    @SerialName("created_at")
    @Serializable(with = LossyNullableIntSerializer::class)
    val createdAt: Int? = null,
)

data class KanbanCompatibilityReport(
    val configuration: KanbanConfiguration,
    val boards: List<KanbanBoardSummary>,
    val currentBoard: KanbanBoardSummary,
    val snapshot: KanbanBoardSnapshot,
    val warnings: List<KanbanCompatibilityWarning>,
)

sealed interface KanbanCompatibilityWarning {
    data object ReadOnly : KanbanCompatibilityWarning
    data class UnsupportedStatus(val status: String) : KanbanCompatibilityWarning
}

sealed class KanbanContractViolation(message: String) : IllegalStateException(message) {
    data object MissingConfigurationColumns : KanbanContractViolation("The server did not provide Kanban columns.")
    data object MissingCurrentBoard : KanbanContractViolation("The server did not identify the current Kanban board.")
    data object MissingBoardIdentity : KanbanContractViolation("The server returned a Kanban board without an identity.")
    data object MissingBoardSnapshot : KanbanContractViolation("The server did not provide the current Kanban board snapshot.")
    data object MissingColumnStatus : KanbanContractViolation("The server returned a Kanban column without a status.")
    data object MissingCardIdentity : KanbanContractViolation("The server returned a Kanban card without an identity.")
    data object MissingCardStatus : KanbanContractViolation("The server returned a Kanban card without a status.")
}

val supportedKanbanStatuses = setOf("triage", "todo", "blocked", "ready", "running", "done", "archived")

object LossyNullableIntMapSerializer : KSerializer<Map<String, Int>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LossyNullableIntMap")

    override fun deserialize(decoder: Decoder): Map<String, Int>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonObject -> element.mapNotNull { (key, value) -> value.jsonPrimitive.intOrNull?.let { key to it } }.toMap()
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, Int>?) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(
            value?.let { values -> buildJsonObject { values.forEach { (key, count) -> put(key, count) } } } ?: JsonNull,
        )
    }
}

object LossyNullableStringListSerializer : KSerializer<List<String>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LossyNullableStringList")

    override fun deserialize(decoder: Decoder): List<String>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonArray -> element.mapNotNull { value ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>?) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(value?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull)
    }
}

object LossyNullableKanbanAssigneeListSerializer : KSerializer<List<KanbanAssigneeValue>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LossyNullableKanbanAssigneeList")

    override fun deserialize(decoder: Decoder): List<KanbanAssigneeValue>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonArray -> element.mapNotNull { value ->
                runCatching { jsonDecoder.json.decodeFromJsonElement<KanbanAssigneeValue>(value) }.getOrNull()
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: List<KanbanAssigneeValue>?) = encodeList(encoder, value)
}

object LossyNullableKanbanBoardListSerializer : KSerializer<List<KanbanBoardSummary>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LossyNullableKanbanBoardList")

    override fun deserialize(decoder: Decoder): List<KanbanBoardSummary>? =
        decodeList(decoder)

    override fun serialize(encoder: Encoder, value: List<KanbanBoardSummary>?) = encodeList(encoder, value)
}

object LossyNullableKanbanColumnListSerializer : KSerializer<List<KanbanColumn>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LossyNullableKanbanColumnList")

    override fun deserialize(decoder: Decoder): List<KanbanColumn>? =
        decodeList(decoder)

    override fun serialize(encoder: Encoder, value: List<KanbanColumn>?) = encodeList(encoder, value)
}

object LossyNullableKanbanCardListSerializer : KSerializer<List<KanbanCardSummary>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LossyNullableKanbanCardList")

    override fun deserialize(decoder: Decoder): List<KanbanCardSummary>? =
        decodeList(decoder)

    override fun serialize(encoder: Encoder, value: List<KanbanCardSummary>?) = encodeList(encoder, value)
}

private inline fun <reified Value> decodeList(decoder: Decoder): List<Value>? {
    val jsonDecoder = decoder as? JsonDecoder ?: return null
    return when (val element = jsonDecoder.decodeJsonElement()) {
        JsonNull -> null
        is JsonArray -> element.mapNotNull { value ->
            runCatching { jsonDecoder.json.decodeFromJsonElement<Value>(value) }.getOrNull()
        }
        else -> null
    }
}

private inline fun <reified Value> encodeList(encoder: Encoder, value: List<Value>?) {
    val jsonEncoder = encoder as? JsonEncoder ?: return
    jsonEncoder.encodeJsonElement(
        value?.let { values -> JsonArray(values.map { jsonEncoder.json.encodeToJsonElement(it) }) } ?: JsonNull,
    )
}
