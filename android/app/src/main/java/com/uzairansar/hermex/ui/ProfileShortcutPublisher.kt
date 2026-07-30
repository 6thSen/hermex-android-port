package com.uzairansar.hermex.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.uzairansar.hermex.MainActivity
import com.uzairansar.hermex.R
import com.uzairansar.hermex.core.model.ProfileSummary
import java.util.Locale

internal data class ProfileShortcutSpec(
    val id: String,
    val profileName: String,
    val title: String,
)

internal object ProfileShortcutPolicy {
    fun specs(profiles: List<ProfileSummary>, maximumCount: Int): List<ProfileShortcutSpec> =
        profiles.asSequence()
            .mapNotNull { profile ->
                val name = profile.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val title = profile.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: name
                ProfileShortcutSpec(
                    id = "profile_${name.lowercase(Locale.ROOT).hashCode().toUInt().toString(16)}",
                    profileName = name,
                    title = title,
                )
            }
            .distinctBy { it.profileName.lowercase(Locale.ROOT) }
            .take(maximumCount.coerceAtLeast(0))
            .toList()
}

internal class ProfileShortcutPublisher(context: Context) {
    private val appContext = context.applicationContext

    fun publish(profiles: List<ProfileSummary>) {
        val maximumDynamicCount =
            (ShortcutManagerCompat.getMaxShortcutCountPerActivity(appContext) - STATIC_SHORTCUT_COUNT)
                .coerceAtLeast(0)
        val shortcuts = ProfileShortcutPolicy.specs(profiles, maximumDynamicCount).map { spec ->
            val uri = Uri.Builder()
                .scheme("hermes-agent")
                .authority("new-chat-profile")
                .appendQueryParameter("profile", spec.profileName)
                .build()
            ShortcutInfoCompat.Builder(appContext, spec.id)
                .setShortLabel("New ${spec.title}".take(SHORT_LABEL_MAX_LENGTH))
                .setLongLabel("New chat in ${spec.title}".take(LONG_LABEL_MAX_LENGTH))
                .setIcon(IconCompat.createWithResource(appContext, R.drawable.ic_shortcut_new_session))
                .setIntent(Intent(Intent.ACTION_VIEW, uri, appContext, MainActivity::class.java))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(appContext, shortcuts) }
    }

    private companion object {
        const val STATIC_SHORTCUT_COUNT = 3
        const val SHORT_LABEL_MAX_LENGTH = 30
        const val LONG_LABEL_MAX_LENGTH = 80
    }
}
