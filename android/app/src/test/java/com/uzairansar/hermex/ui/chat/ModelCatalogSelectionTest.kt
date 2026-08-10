package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogSelectionTest {
    @Test
    fun uniqueModelMatchSurvivesProviderAliasDifference() {
        val model = ModelSummary(
            id = "cx/gpt-5.6-sol",
            label = "GPT-5.6 Sol",
            provider = "custom:local-localhost-20128",
        )

        assertEquals(
            model,
            listOf(model).firstMatchingCatalogModel("cx/gpt-5.6-sol", "custom"),
        )
    }

    @Test
    fun exactProviderWinsWhenModelExistsAcrossProviders() {
        val openAi = ModelSummary(id = "shared-model", provider = "openai")
        val custom = ModelSummary(id = "shared-model", provider = "custom")

        assertEquals(
            custom,
            listOf(openAi, custom).firstMatchingCatalogModel("shared-model", "custom"),
        )
    }

    @Test
    fun unmatchedProviderDoesNotGuessBetweenAmbiguousModels() {
        val models = listOf(
            ModelSummary(id = "shared-model", provider = "openai"),
            ModelSummary(id = "shared-model", provider = "custom"),
        )

        assertNull(models.firstMatchingCatalogModel("shared-model", "unknown-provider"))
    }

    @Test
    fun bareDefaultIdMatchesNamespacedNousEntryFirst() {
        val nous = ModelSummary(
            id = "@nous:deepseek/deepseek-v4-flash-0731",
            label = "Deepseek V4 Flash 0731 (via Nous)",
            provider = "nous",
        )
        val openRouter = ModelSummary(
            id = "deepseek/deepseek-v4-flash-0731",
            label = "deepseek/deepseek-v4-flash-0731",
            provider = "openrouter",
        )
        // Catalog order mirrors the server's group order: nous group first.
        val models = listOf(nous, openRouter)

        // Bare default model id, no provider pin: must resolve to the nous entry,
        // not the openrouter one (the @nous: prefix must not break the match).
        assertEquals(nous, models.firstMatchingCatalogModel("deepseek/deepseek-v4-flash-0731", null))
    }

    @Test
    fun namespacedTargetMatchesBareCatalogEntry() {
        val nous = ModelSummary(id = "@nous:deepseek/deepseek-v4-flash-0731", provider = "nous")

        assertEquals(
            nous,
            listOf(nous).firstMatchingCatalogModel("@nous:deepseek/deepseek-v4-flash-0731", "nous"),
        )
    }

    @Test
    fun explicitProviderStillWinsWithNamespacedEntries() {
        val nous = ModelSummary(id = "@nous:deepseek/deepseek-v4-flash-0731", provider = "nous")
        val openRouter = ModelSummary(id = "deepseek/deepseek-v4-flash-0731", provider = "openrouter")
        val models = listOf(nous, openRouter)

        assertEquals(
            openRouter,
            models.firstMatchingCatalogModel("deepseek/deepseek-v4-flash-0731", "openrouter"),
        )
        assertEquals(
            nous,
            models.firstMatchingCatalogModel("deepseek/deepseek-v4-flash-0731", "nous"),
        )
    }
}
