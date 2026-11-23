package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences

object ThemeCoordinator {
    const val THEME_DEFAULT = "default"
    const val THEME_MATERIAL_EXPRESSIVE = "material_expressive"
    const val THEME_IOS27 = "ios27"
    const val THEME_MIUIX = "miuix"

    fun activeTheme(prefs: SharedPreferences): String {
        val explicit = prefs.getString("active_ui_theme", null)
        if (!explicit.isNullOrBlank()) return explicit
        return when {
            prefs.getBoolean("miuix_enabled", false) -> THEME_MIUIX
            prefs.getBoolean("ios27_enabled", false) -> THEME_IOS27
            prefs.getBoolean("me_enabled", false) -> THEME_MATERIAL_EXPRESSIVE
            else -> THEME_DEFAULT
        }
    }

    fun isIos27(prefs: SharedPreferences): Boolean =
        activeTheme(prefs) == THEME_IOS27

    fun isMaterialExpressive(prefs: SharedPreferences): Boolean =
        activeTheme(prefs) == THEME_MATERIAL_EXPRESSIVE

    fun isMiuix(prefs: SharedPreferences): Boolean =
        activeTheme(prefs) == THEME_MIUIX

    fun isMiuixWallpaperActive(prefs: SharedPreferences): Boolean =
        isMiuix(prefs) &&
            prefs.getBoolean("miuix_wallpaper_enabled", true) &&
            (prefs.getString("miuix_wallpaper_id", "wp1") ?: "wp1") != "none"
}
