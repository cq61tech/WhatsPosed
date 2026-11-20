package com.wmods.wppenhacer.ui.compose

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

class IntState(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: Int,
) {
    private var state by mutableStateOf(sp.getInt(key, default))
    var value: Int
        get() = state
        set(v) {
            state = v
            sp.edit().putInt(key, v).apply()
        }

    fun reload() {
        state = sp.getInt(key, default)
    }
}

class FloatingBarPrefs(ctx: Context) {
    private val sp = PreferenceManager.getDefaultSharedPreferences(ctx)

    val enabled = BoolState(sp, "floating_bottom_bar", false)
    val preset = TextState(sp, "fbb_preset", "classic")
    val radius = IntState(sp, "floating_bottom_bar_radius", 24)
    val bottomMargin = IntState(sp, "fbb_bottom_margin", 16)
    val sideMargin = IntState(sp, "fbb_side_margin", 12)
    val padV = IntState(sp, "fbb_pad_v", 0)
    val padH = IntState(sp, "fbb_pad_h", 0)
    val iconSize = IntState(sp, "fbb_icon_size", 0)
    val indicator = TextState(sp, "fbb_indicator", "default")
    val indicatorColor = IntState(sp, "fbb_indicator_color", 0)
    val iconSelected = IntState(sp, "fbb_icon_selected", 0)
    val iconUnselected = IntState(sp, "fbb_icon_unselected", 0)
    val hideLabels = BoolState(sp, "fbb_hide_labels", false)
    val textSize = IntState(sp, "fbb_text_size", 0)
    val labelGap = IntState(sp, "fbb_label_gap", 0)
    val glass = BoolState(sp, "fbb_glass", false)
    val glassAlpha = IntState(sp, "fbb_glass_alpha", 35)
    val shadow = BoolState(sp, "fbb_shadow", true)
    val hideScroll = BoolState(sp, "fbb_hide_scroll", false)
    val hideMode = TextState(sp, "fbb_hide_mode", "slide")

    fun applyPreset(id: String) {
        preset.value = id
        when (id) {
            "enhanced" -> {
                radius.value = 28
                bottomMargin.value = 18
                sideMargin.value = 14
                padV.value = 4
                padH.value = 6
                indicator.value = "oval"
                glass.value = false
                shadow.value = true
            }

            "glass" -> {
                radius.value = 32
                bottomMargin.value = 18
                sideMargin.value = 14
                padV.value = 4
                padH.value = 6
                indicator.value = "oval"
                glass.value = true
                glassAlpha.value = 35
                shadow.value = true
            }

            else -> {
                radius.value = 24
                bottomMargin.value = 16
                sideMargin.value = 12
                padV.value = 0
                padH.value = 0
                indicator.value = "default"
                glass.value = false
                shadow.value = true
            }
        }
    }

    fun reset() {
        applyPreset("classic")
        iconSize.value = 0
        indicatorColor.value = 0
        iconSelected.value = 0
        iconUnselected.value = 0
        hideLabels.value = false
        textSize.value = 0
        labelGap.value = 0
        glassAlpha.value = 35
        hideScroll.value = false
        hideMode.value = "slide"
    }
}
