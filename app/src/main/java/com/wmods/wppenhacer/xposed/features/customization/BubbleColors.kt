package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBallonBorderDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBallonDateDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBubbleDrawableMethod
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class BubbleColors(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val properties = Utils.getProperties(xprefs, "custom_css", "custom_filters")
        val isIos27 = ThemeCoordinator.isIos27(xprefs)
        val isMiuix = ThemeCoordinator.isMiuix(xprefs)
        val ios27Bubbles = isIos27 && xprefs.getBoolean("ios27_bubbles", true)
        val miuixBubbles = isMiuix && xprefs.getBoolean("miuix_bubbles", true)
        val miuixGreen = xprefs.getString("miuix_accent_mode", "blue") == "green"
        val bubbleColor = !isIos27 && !isMiuix && xprefs.getBoolean("bubble_color", false)
        val propEnabled = !isIos27 && !isMiuix && properties.getProperty("bubble_colors") == "true"

        if (!ios27Bubbles && !miuixBubbles && !bubbleColor && !propEnabled) return

        val manualLeftColor = if (bubbleColor) xprefs.getInt("bubble_left", 0)
        else if (propEnabled) DesignUtils.checkSystemColor(properties.getProperty("bubble_left", "#00000000")).toColorInt()
        else 0

        val manualRightColor = if (bubbleColor) xprefs.getInt("bubble_right", 0)
        else if (propEnabled) DesignUtils.checkSystemColor(properties.getProperty("bubble_right", "#00000000")).toColorInt()
        else 0

        fun resolveRightColor(): Int = when {
            ios27Bubbles -> if (DesignUtils.isNightMode()) 0xFF005C4B.toInt() else 0xFFD9FDD3.toInt()
            miuixBubbles -> if (miuixGreen) {
                if (DesignUtils.isNightMode()) 0xFF145C3E.toInt() else 0xFFD9FBE3.toInt()
            } else {
                if (DesignUtils.isNightMode()) 0xFF134886.toInt() else 0xFFDCEBFF.toInt()
            }
            else -> manualRightColor
        }

        fun resolveLeftColor(): Int = when {
            ios27Bubbles -> if (DesignUtils.isNightMode()) 0xFF202224.toInt() else 0xFFFFFFFF.toInt()
            miuixBubbles -> if (DesignUtils.isNightMode()) 0xFF222428.toInt() else 0xFFF4F6FA.toInt()
            else -> manualLeftColor
        }

        val dateWrapper = loadBallonDateDrawable(classLoader)
        dateWrapper.hook {
            after {
                val drawable = result as? Drawable ?: return@after
                val position = args[0] as Int
                val color = if (position == 3) resolveRightColor() else resolveLeftColor()
                if (color == 0) return@after
                drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }

        val babblon = loadBallonBorderDrawable(classLoader)
        babblon.hook {
            after {
                val drawable = result as? Drawable ?: return@after
                val position = args[1] as Int
                val color = if (position == 3) resolveRightColor() else resolveLeftColor()
                if (color == 0) return@after
                drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }

        val bubbleDrawableMethod = loadBubbleDrawableMethod(classLoader)
        bubbleDrawableMethod.hook {
            after {
                val position = args[0] as Int
                val draw = result as? Drawable ?: return@after
                val color = if (position == 3) resolveRightColor() else resolveLeftColor()
                if (color == 0) return@after
                draw.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    override fun getPluginName(): String = "Bubble Colors"
}
