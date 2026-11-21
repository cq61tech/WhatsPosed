package com.wmods.wppenhacer.ui.compose

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

class MaterialExpressivePrefs(ctx: Context) {
    private val sp = PreferenceManager.getDefaultSharedPreferences(ctx)

    val monetAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val enabled = BoolState(sp, "me_enabled", false)
    val preset = TextState(sp, "me_preset", "qpr2")
    val colorSource = TextState(sp, "me_color_source", if (monetAvailable) "monet" else "neutral")
    val monetIntensity = IntState(sp, "me_monet_intensity", 70)
    val cardStyle = TextState(sp, "me_card_style", "segmented")
    val cornerRadius = IntState(sp, "me_corner_radius", 22)
    val cardGap = IntState(sp, "me_card_gap", 3)
    val cardOutline = BoolState(sp, "me_card_outline", true)
    val hideDividers = BoolState(sp, "me_hide_dividers", true)

    val chatsCards = BoolState(sp, "me_chats_cards", true)
    val settingsCards = BoolState(sp, "me_settings_cards", true)
    val searchPill = BoolState(sp, "me_search_pill", true)
    val chatComposer = BoolState(sp, "me_chat_composer", true)
    val expressiveFab = BoolState(sp, "me_expressive_fab", true)
    val styleDialogs = BoolState(sp, "me_dialogs", true)
    val syncBubble = BoolState(sp, "me_sync_bubble", true)

    private var accentSyncState by mutableStateOf(
        sp.getBoolean("changecolor", false) && sp.getString("changecolor_mode", "manual") == "monet"
    )

    var syncAccentEngine: Boolean
        get() = accentSyncState
        set(v) {
            accentSyncState = v
            if (v && monetAvailable) {
                sp.edit()
                    .putBoolean("changecolor", true)
                    .putString("changecolor_mode", "monet")
                    .apply()
            } else if (!v) {
                if (sp.getString("changecolor_mode", "manual") == "monet") {
                    sp.edit().putBoolean("changecolor", false).apply()
                }
            }
        }

    fun applyPreset(id: String) {
        preset.value = id
        when (id) {
            "subtle" -> {
                colorSource.value = if (monetAvailable) "monet" else "neutral"
                monetIntensity.value = 35
                cardStyle.value = "soft"
                cornerRadius.value = 16
                cardGap.value = 2
                cardOutline.value = false
                hideDividers.value = true
                chatsCards.value = true
                settingsCards.value = true
                searchPill.value = true
                chatComposer.value = true
                expressiveFab.value = false
                styleDialogs.value = true
                syncBubble.value = true
            }
            "full" -> {
                colorSource.value = if (monetAvailable) "monet" else "custom"
                monetIntensity.value = 100
                cardStyle.value = "rounded"
                cornerRadius.value = 26
                cardGap.value = 4
                cardOutline.value = true
                hideDividers.value = true
                chatsCards.value = true
                settingsCards.value = true
                searchPill.value = true
                chatComposer.value = true
                expressiveFab.value = true
                styleDialogs.value = true
                syncBubble.value = true
                if (monetAvailable) syncAccentEngine = true
            }
            else -> {
                colorSource.value = if (monetAvailable) "monet" else "neutral"
                monetIntensity.value = 70
                cardStyle.value = "segmented"
                cornerRadius.value = 22
                cardGap.value = 3
                cardOutline.value = true
                hideDividers.value = true
                chatsCards.value = true
                settingsCards.value = true
                searchPill.value = true
                chatComposer.value = true
                expressiveFab.value = true
                styleDialogs.value = true
                syncBubble.value = true
            }
        }
    }

    fun reset() {
        applyPreset("qpr2")
        if (monetAvailable) {
            syncAccentEngine = false
        }
    }
}
