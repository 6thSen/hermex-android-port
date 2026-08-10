package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ModelSummary

internal fun List<ModelSummary>.firstMatchingCatalogModel(
    model: String?,
    provider: String?,
): ModelSummary? {
    val targetModel = model.normalizedCatalogValue() ?: return null
    val candidates = filter { option ->
        listOfNotNull(option.id, option.name)
            .any { value -> value.normalizedCatalogValue()?.matchesModelId(targetModel) == true }
    }
    if (candidates.isEmpty()) return null

    val targetProvider = provider.normalizedCatalogValue() ?: return candidates.first()
    return candidates.firstOrNull { option ->
        option.provider.normalizedCatalogValue()?.equals(targetProvider, ignoreCase = true) == true
    } ?: candidates.singleOrNull()
}

private fun String?.normalizedCatalogValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

// Strip a leading "@provider:" namespace prefix (e.g. "@nous:vendor/model") so a
// bare id matches its namespaced catalog entry and vice versa. Without this, the
// bare default model id resolves only against the openrouter entry and the nous
// entry (@nous:-prefixed) never matches — pinning new sessions to openrouter.
private fun String.matchesModelId(target: String): Boolean {
    val bare = if (startsWith("@")) substringAfter(':', this) else this
    val bareTarget = if (target.startsWith("@")) target.substringAfter(':', target) else target
    return bare.equals(bareTarget, ignoreCase = true)
}
