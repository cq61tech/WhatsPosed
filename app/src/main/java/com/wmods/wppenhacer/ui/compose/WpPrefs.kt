package com.wmods.wppenhacer.ui.compose

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.App

class BoolState(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: Boolean,
) {
    private var state by mutableStateOf(sp.getBoolean(key, default))
    var value: Boolean
        get() = state
        set(v) {
            state = v
            sp.edit().putBoolean(key, v).apply()
        }

    fun reload() {
        state = sp.getBoolean(key, default)
    }
}

class FloatState(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: Float,
) {
    private var state by mutableStateOf(sp.getFloat(key, default))
    var value: Float
        get() = state
        set(v) {
            state = v
            sp.edit().putFloat(key, v).apply()
        }

    fun reload() {
        state = sp.getFloat(key, default)
    }
}

class TextState(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: String,
) {
    private var state by mutableStateOf(sp.getString(key, default) ?: default)
    var value: String
        get() = state
        set(v) {
            state = v
            sp.edit().putString(key, v).apply()
        }

    fun reload() {
        state = sp.getString(key, default) ?: default
    }
}

class WpPrefs(ctx: Context) {
    private val sp = PreferenceManager.getDefaultSharedPreferences(ctx)

    private var themeState by mutableStateOf(readTheme())
    private var monetState by mutableStateOf(readMonet())
    private var presetState by mutableStateOf(sp.getString("wae_color_preset", "green") ?: "green")

    val amoled = BoolState(sp, "wpui_amoled", false)
    val fontId = TextState(sp, "wpui_font_id", "auto")
    val fontWeight = FloatState(sp, "wpui_font_weight", 600f)
    val fontRound = FloatState(sp, "wpui_font_round", 100f)
    val fontScale = FloatState(sp, "wpui_font_scale", 100f)
    val fontSpacing = FloatState(sp, "wpui_font_spacing", 0f)
    val dockFloating = BoolState(sp, "wpui_dock_floating", true)
    val dockWidth = FloatState(sp, "wpui_dock_width", 94f)
    val dockHeight = FloatState(sp, "wpui_dock_height", 64f)
    val dockOpacity = FloatState(sp, "wpui_dock_opacity", 100f)
    val dockTextSize = FloatState(sp, "wpui_dock_text", 10f)
    val dockBottom = FloatState(sp, "wpui_dock_bottom", 8f)
    val dockLabels = BoolState(sp, "wpui_dock_labels", false)

    private fun readTheme(): Int = sp.getString("thememode", "0")?.toIntOrNull() ?: 0
    private fun readMonet(): Boolean = sp.getString("wae_color_mode", "monet") == "monet"

    var themeMode: Int
        get() = themeState
        set(v) {
            themeState = v
            sp.edit().putString("thememode", v.toString()).apply()
            App.setThemeMode(v)
        }

    var monet: Boolean
        get() = monetState
        set(v) {
            monetState = v
            sp.edit().putString("wae_color_mode", if (v) "monet" else "preset").apply()
        }

    var preset: String
        get() = presetState
        set(v) {
            presetState = v
            sp.edit().putString("wae_color_preset", v).apply()
        }

    fun snapshot(): String =
        (sp.getString("wae_color_mode", "monet") ?: "") +
            (sp.getString("wae_color_preset", "green") ?: "") +
            (sp.getString("thememode", "0") ?: "")

    fun reload() {
        themeState = readTheme()
        monetState = readMonet()
        presetState = sp.getString("wae_color_preset", "green") ?: "green"
        amoled.reload()
        fontId.reload()
        fontWeight.reload()
        fontRound.reload()
        fontScale.reload()
        fontSpacing.reload()
        dockFloating.reload()
        dockWidth.reload()
        dockHeight.reload()
        dockOpacity.reload()
        dockTextSize.reload()
        dockBottom.reload()
        dockLabels.reload()
    }

    companion object {
        fun migrate(ctx: Context) {
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            if (!sp.getBoolean("wpui_migrated", false)) {
                sp.edit()
                    .putString("wae_color_mode", "monet")
                    .putBoolean("wpui_migrated", true)
                    .apply()
            }
        }
    }
}
