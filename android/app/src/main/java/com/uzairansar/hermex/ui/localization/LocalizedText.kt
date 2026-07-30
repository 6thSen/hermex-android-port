package com.uzairansar.hermex.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun localizedString(englishText: String): String {
    val resourceId = AndroidLocalizationCatalog.resourceId(englishText) ?: return englishText
    return stringResource(resourceId)
}

@Composable
internal fun localizedStringFormat(englishTemplate: String, vararg arguments: Any?): String {
    val localizedTemplate = localizedString(englishTemplate)
    var argumentIndex = 0
    return IOS_FORMAT_PLACEHOLDER.replace(localizedTemplate) {
        arguments.getOrNull(argumentIndex++)?.toString().orEmpty()
    }
}

private val IOS_FORMAT_PLACEHOLDER = Regex("%(?:\\d+\\$)?(?:lld|ld|d|@)")
