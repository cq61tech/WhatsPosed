package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingBottomBar(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val CORNER_RADIUS_DP = 24
        private const val ELEVATION_DP = 5f
        private const val BACKGROUND_ALPHA = 0.90f
        private const val STROKE_ALPHA = 0.40f
        private const val FAB_GAP_DP = 8f
        private val FAB_RESOURCE_NAMES = arrayOf("fab", "fab_second", "extended_mini_fab")
    }

    private class Style(
        val radiusDp: Int,
        val bottomMarginDp: Int,
        val sideMarginDp: Int,
        val padVDp: Int,
        val padHDp: Int,
        val iconSizeDp: Int,
        val ovalIndicator: Boolean,
        val indicatorColor: Int,
        val selectedColor: Int,
        val unselectedColor: Int,
        val hideLabels: Boolean,
        val textSizeSp: Int,
        val labelGapDp: Int,
        val glass: Boolean,
        val liquid: Boolean,
        val materialPill: Boolean,
        val miuix: Boolean,
        val glassAlpha: Int,
        val shadow: Boolean,
        val hideOnScroll: Boolean,
        val fade: Boolean,
    ) {
        fun signature(): Int {
            var h = 17
            for (v in intArrayOf(
                iconSizeDp, if (ovalIndicator) 1 else 0, indicatorColor, selectedColor,
                unselectedColor, if (hideLabels) 1 else 0, textSizeSp, labelGapDp,
                if (liquid) 1 else 0, if (materialPill) 1 else 0, if (miuix) 1 else 0,
            )) h = 31 * h + v
            return h
        }
    }

    private class WindowTouchHub {
        var scrollHider: ScrollHider? = null
        var liquidController: LiquidGlassController? = null
    }

    private val processedBars = WeakHashMap<ViewGroup, Boolean>()
    private val setupAttempts = WeakHashMap<ViewGroup, Int>()
    private val basePadding = WeakHashMap<ViewGroup, IntArray>()
    private val paddingGuarded = WeakHashMap<ViewGroup, Boolean>()
    private val barStyles = WeakHashMap<ViewGroup, Style>()
    private val barColors = WeakHashMap<ViewGroup, Int>()
    private val glassDrawables = WeakHashMap<ViewGroup, GlassDrawable>()
    private val liquidControllers = WeakHashMap<ViewGroup, LiquidGlassController>()
    private val styledViews = WeakHashMap<View, Int>()
    private val touchHubs = WeakHashMap<Activity, WindowTouchHub>()

    private fun readStyle(): Style {
        val preset = xprefs.getString("fbb_preset", "classic") ?: "classic"
        val isLiquid = preset == "liquid"
        val isMaterialPill = preset == "material_pill"
        val isMiuix = preset == "miuix"
        return Style(
            radiusDp = xprefs.getInt("floating_bottom_bar_radius", CORNER_RADIUS_DP),
            bottomMarginDp = xprefs.getInt("fbb_bottom_margin", 16),
            sideMarginDp = xprefs.getInt("fbb_side_margin", 12),
            padVDp = xprefs.getInt("fbb_pad_v", 0),
            padHDp = xprefs.getInt("fbb_pad_h", 0),
            iconSizeDp = xprefs.getInt("fbb_icon_size", 0),
            ovalIndicator = isLiquid || (!isMaterialPill && !isMiuix && xprefs.getString("fbb_indicator", "default") == "oval"),
            indicatorColor = xprefs.getInt("fbb_indicator_color", 0),
            selectedColor = xprefs.getInt("fbb_icon_selected", 0),
            unselectedColor = xprefs.getInt("fbb_icon_unselected", 0),
            hideLabels = xprefs.getBoolean("fbb_hide_labels", false),
            textSizeSp = xprefs.getInt("fbb_text_size", 0),
            labelGapDp = xprefs.getInt("fbb_label_gap", 0),
            glass = isLiquid || isMiuix || (!isMaterialPill && xprefs.getBoolean("fbb_glass", false)),
            liquid = isLiquid,
            materialPill = isMaterialPill,
            miuix = isMiuix,
            glassAlpha = xprefs.getInt("fbb_glass_alpha", if (isLiquid) 22 else if (isMiuix) 28 else 35),
            shadow = xprefs.getBoolean("fbb_shadow", true),
            hideOnScroll = xprefs.getBoolean("fbb_hide_scroll", false),
            fade = xprefs.getString("fbb_hide_mode", "slide") == "fade",
        )
    }

    override fun doHook() {
        if (!xprefs.getBoolean("floating_bottom_bar", false)) return

        val bottomNavId = Utils.getID("bottom_nav", "id")
        if (bottomNavId <= 0) return
        val fabIds = FAB_RESOURCE_NAMES.mapNotNull { name ->
            Utils.getID(name, "id").takeIf { id -> id > 0 }
        }.toSet()

        ViewHookDispatcher.onAttachedToWindow(this) { view ->
            if (view.id == bottomNavId) {
                val bar = view as? ViewGroup ?: return@onAttachedToWindow
                scheduleSetup(bar)
                return@onAttachedToWindow
            }
            if (view.id in fabIds) {
                view.post { positionFabAboveCurrentBar(view, bottomNavId) }
            }
        }

        ViewHookDispatcher.onDetachedFromWindow(this) { view ->
            if (view.id != bottomNavId) return@onDetachedFromWindow
            val bar = view as? ViewGroup ?: return@onDetachedFromWindow
            setupAttempts.remove(bar)
            processedBars.remove(bar)
            glassDrawables[bar]?.detach()
            liquidControllers.remove(bar)?.detach()
        }
    }

    private fun scheduleSetup(bar: ViewGroup) {
        if (processedBars.containsKey(bar)) {
            ensureBarOverlay(bar)
            glassDrawables[bar]?.attach()
            liquidControllers[bar]?.syncLayout()
            return
        }

        bar.post {
            if (setupFloatingBar(bar)) {
                processedBars[bar] = true
                setupAttempts.remove(bar)
                return@post
            }
            retrySetup(bar)
        }
    }

    private fun retrySetup(bar: ViewGroup) {
        val attempt = setupAttempts[bar] ?: 0
        if (attempt >= 3) return
        setupAttempts[bar] = attempt + 1
        bar.postDelayed({
            if (processedBars.containsKey(bar)) return@postDelayed
            if (setupFloatingBar(bar)) {
                processedBars[bar] = true
                setupAttempts.remove(bar)
            } else {
                retrySetup(bar)
            }
        }, 100L)
    }

    private fun ensureBarOverlay(bar: ViewGroup) {
        val container = bar.parent as? ViewGroup ?: return
        if (container.parent !is FrameLayout) {
            bar.post { setupFloatingBar(bar) }
        }
    }

    private fun styleOf(bar: ViewGroup): Style = barStyles.getOrPut(bar) { readStyle() }

    private fun setupFloatingBar(bar: ViewGroup): Boolean {
        try {
            val container = bar.parent as? ViewGroup ?: return false
            val rootView = findRootView(bar) ?: return false
            val style = styleOf(bar)
            if (container.parent === rootView) {
                updateOverlayLayout(rootView, container, bar, style)
                applyTransparentShadowStyle(rootView, container, bar, style)
                positionFabsAboveBar(rootView, container)
                return true
            }

            (container.parent as? ViewGroup)?.removeView(container)

            val rootParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = navigationBarInset(rootView) + Utils.dipToPixels(style.bottomMarginDp.toFloat())
            }
            rootView.addView(container, rootParams)

            applyTransparentShadowStyle(rootView, container, bar, style)
            positionFabsAboveBar(rootView, container)
            installTouchHub(bar, container, style)

            logDebug("FloatingBottomBar applied as overlay")
            return true
        } catch (e: Throwable) {
            log(e)
            return false
        }
    }

    private fun updateOverlayLayout(
        rootView: FrameLayout,
        container: ViewGroup,
        bar: ViewGroup,
        style: Style,
    ) {
        val params = container.layoutParams as? FrameLayout.LayoutParams ?: return
        val bottomMargin = navigationBarInset(rootView) + Utils.dipToPixels(style.bottomMarginDp.toFloat())
        if (params.gravity != Gravity.BOTTOM
            || params.width != ViewGroup.LayoutParams.MATCH_PARENT
            || params.height != ViewGroup.LayoutParams.WRAP_CONTENT
            || params.bottomMargin != bottomMargin
        ) {
            params.gravity = Gravity.BOTTOM
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.bottomMargin = bottomMargin
            container.layoutParams = params
        }
        val barParams = bar.layoutParams
        if (barParams.width != ViewGroup.LayoutParams.MATCH_PARENT
            || barParams.height != ViewGroup.LayoutParams.WRAP_CONTENT
        ) {
            barParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            barParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            bar.layoutParams = barParams
        }
    }

    private fun navigationBarInset(view: View): Int {
        return ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.bottom ?: 0
    }

    private fun findRootView(startView: View): FrameLayout? {
        var current: View? = startView
        var lastFrameLayout: FrameLayout? = null
        while (current != null) {
            if (current is FrameLayout) {
                lastFrameLayout = current
            }
            current = current.parent as? View
        }
        return lastFrameLayout
    }

    private fun applyTransparentShadowStyle(
        rootView: FrameLayout,
        container: ViewGroup,
        bar: ViewGroup,
        style: Style,
    ) {
        container.setBackgroundColor(Color.TRANSPARENT)

        val parent = container.parent as? ViewGroup
        parent?.clipChildren = false
        parent?.clipToPadding = false
        container.clipChildren = false
        container.clipToPadding = false

        val dividerId = Utils.getID("bottom_nav_divider", "id")
        if (dividerId > 0) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.id == dividerId) {
                    child.visibility = View.GONE
                    break
                }
            }
        }

        val barColor = barColors.getOrPut(bar) { resolveBarColor(bar) }
        val light = isLightColor(barColor)
        val radius = Utils.dipToPixels(style.radiusDp.toFloat()).toFloat()

        if (style.materialPill) {
            val mp = resolveMaterialPillPalette(bar.context, light, style)
            val pillRadius = max(radius, Utils.dipToPixels(36f).toFloat())
            bar.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = pillRadius
                setColor(mp.dockBg)
                setStroke(Utils.dipToPixels(1.25f).coerceAtLeast(1), mp.dockStroke)
            }
        } else if (style.glass) {
            if (bar.background !is GlassDrawable) {
                val glass = GlassDrawable(
                    container, bar, radius, barColor or (0xFF shl 24),
                    style.glassAlpha, light, Utils.dipToPixels(if (style.liquid) 1.35f else 1.2f).toFloat(),
                    style.liquid,
                )
                bar.background = glass
                glassDrawables[bar] = glass
                glass.attach()
            }
        } else {
            val transparentColor = (barColor and 0x00FFFFFF) or
                ((BACKGROUND_ALPHA * 255).toInt() shl 24)
            val strokeColor = if (light) {
                Color.argb((STROKE_ALPHA * 255).toInt(), 0, 0, 0)
            } else {
                Color.argb((STROKE_ALPHA * 255).toInt(), 255, 255, 255)
            }
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    radius, radius, radius, radius,
                    radius, radius, radius, radius
                )
                setColor(transparentColor)
                setStroke(Utils.dipToPixels(1.5f), strokeColor)
            }
            bar.background = background
        }
        bar.outlineProvider = ViewOutlineProvider.BACKGROUND
        bar.elevation = if (style.shadow) Utils.dipToPixels(ELEVATION_DP).toFloat() else 0f

        val sideMargin = Utils.dipToPixels(style.sideMarginDp.toFloat())
        val params = bar.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null && (params.width != ViewGroup.LayoutParams.MATCH_PARENT
                || params.height != ViewGroup.LayoutParams.WRAP_CONTENT
                || params.leftMargin != sideMargin || params.topMargin != 0
                || params.rightMargin != sideMargin || params.bottomMargin != 0)
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.setMargins(sideMargin, 0, sideMargin, 0)
            bar.layoutParams = params
        }

        enforcePadding(bar, style)
        styleItems(bar, style, light)
        installPaddingGuard(bar)

        if (style.liquid) {
            val controller = liquidControllers.getOrPut(bar) {
                LiquidGlassController(
                    rootView = rootView,
                    container = container,
                    bar = bar,
                    light = light,
                    tintColor = barColor or (0xFF shl 24),
                )
            }
            controller.attach()
            installTouchHub(bar, container, style)
        }
    }

    private fun enforcePadding(bar: ViewGroup, style: Style) {
        val base = basePadding.getOrPut(bar) {
            intArrayOf(
                bar.paddingTop,
                bar.paddingStart,
                bar.paddingEnd,
                (bar.paddingBottom - navigationBarInset(bar)).coerceAtLeast(0),
            )
        }
        val v = Utils.dipToPixels(style.padVDp.toFloat())
        val h = Utils.dipToPixels(style.padHDp.toFloat())
        val top = base[0] + v
        val start = base[1] + h
        val end = base[2] + h
        val bottom = base[3] + v
        if (bar.paddingTop != top || bar.paddingStart != start
            || bar.paddingEnd != end || bar.paddingBottom != bottom
        ) {
            bar.setPaddingRelative(start, top, end, bottom)
        }
    }

    private fun installPaddingGuard(bar: ViewGroup) {
        if (paddingGuarded.put(bar, true) != null) return
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            enforcePadding(view as ViewGroup, styleOf(view))
            liquidControllers[view]?.syncLayout()
            insets
        }
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val style = styleOf(bar)
            enforcePadding(bar, style)
            val params = bar.layoutParams
            if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                bar.layoutParams = params
            }
            styleItems(bar, style, isLightColor(barColors[bar] ?: 0xFF121212.toInt()))
            liquidControllers[bar]?.syncLayout()
        }
    }

    private fun callApi(target: Any, name: String, type: Class<*>, arg: Any): Boolean = try {
        target.javaClass.getMethod(name, type).invoke(target, arg)
        true
    } catch (_: Throwable) {
        false
    }

    private data class PillPalette(
        val dockBg: Int,
        val dockStroke: Int,
        val activePill: Int,
        val activeFg: Int,
        val inactiveFg: Int,
    )

    private fun resolveSystemColor(context: Context, name: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
        val id = context.resources.getIdentifier(name, "color", "android")
        if (id == 0) return 0
        return runCatching { ContextCompat.getColor(context, id) }.getOrDefault(0)
    }

    private fun resolveMaterialPillPalette(context: Context, light: Boolean, style: Style): PillPalette {
        val acc200 = resolveSystemColor(context, "system_accent1_200")
        val acc300 = resolveSystemColor(context, "system_accent1_300")
        val acc500 = resolveSystemColor(context, "system_accent1_500")
        val acc600 = resolveSystemColor(context, "system_accent1_600")
        val acc800 = resolveSystemColor(context, "system_accent1_800")
        val acc900 = resolveSystemColor(context, "system_accent1_900")
        val neu100 = resolveSystemColor(context, "system_neutral1_100")
        val neu900 = resolveSystemColor(context, "system_neutral1_900")

        return if (!light) {
            val baseDark = if (acc900 != 0 && neu900 != 0) {
                ColorUtils.blendARGB(acc900, neu900, 0.35f)
            } else {
                0xFF07213E.toInt()
            }
            val dockBg = ColorUtils.setAlphaComponent(baseDark, 242)
            val dockStroke = ColorUtils.setAlphaComponent(
                if (acc300 != 0) acc300 else 0xFF5A9BFF.toInt(),
                68
            )
            val defaultPill = when {
                acc600 != 0 -> acc600
                acc500 != 0 -> acc500
                else -> 0xFF0A66C2.toInt()
            }
            val pill = if (style.indicatorColor != 0) style.indicatorColor else defaultPill
            val activeFg = if (style.selectedColor != 0) {
                style.selectedColor
            } else if (ColorUtils.calculateLuminance(pill) > 0.55) {
                0xFF0A1420.toInt()
            } else {
                Color.WHITE
            }
            val inactiveFg = if (style.unselectedColor != 0) {
                style.unselectedColor
            } else if (acc200 != 0) {
                ColorUtils.setAlphaComponent(acc200, 224)
            } else {
                0xFFA8C4E6.toInt()
            }
            PillPalette(dockBg, dockStroke, pill, activeFg, inactiveFg)
        } else {
            val baseLight = if (neu100 != 0 && acc200 != 0) {
                ColorUtils.blendARGB(neu100, acc200, 0.22f)
            } else {
                0xFFEAF2FC.toInt()
            }
            val dockBg = ColorUtils.setAlphaComponent(baseLight, 245)
            val dockStroke = ColorUtils.setAlphaComponent(
                if (acc500 != 0) acc500 else 0xFF1A73E8.toInt(),
                64
            )
            val defaultPill = if (acc600 != 0) acc600 else 0xFF1A73E8.toInt()
            val pill = if (style.indicatorColor != 0) style.indicatorColor else defaultPill
            val activeFg = if (style.selectedColor != 0) {
                style.selectedColor
            } else if (ColorUtils.calculateLuminance(pill) > 0.55) {
                0xFF0E1B2A.toInt()
            } else {
                Color.WHITE
            }
            val inactiveFg = if (style.unselectedColor != 0) {
                style.unselectedColor
            } else if (acc800 != 0) {
                ColorUtils.setAlphaComponent(acc800, 195)
            } else {
                0xFF3A5270.toInt()
            }
            PillPalette(dockBg, dockStroke, pill, activeFg, inactiveFg)
        }
    }

    private fun applyMaterialPillTabs(bar: ViewGroup, mp: PillPalette) {
        val menuGroup = if (bar.childCount == 1 && bar.getChildAt(0) is ViewGroup) {
            bar.getChildAt(0) as ViewGroup
        } else {
            bar
        }
        val insetH = Utils.dipToPixels(3f)
        val insetV = Utils.dipToPixels(2f)
        for (i in 0 until menuGroup.childCount) {
            val tab = menuGroup.getChildAt(i)
            val activeShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10000f
                setColor(mp.activePill)
            }
            val activeInset = InsetDrawable(activeShape, insetH, insetV, insetH, insetV)
            val sld = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), activeInset)
                addState(intArrayOf(android.R.attr.state_checked), activeInset)
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            tab.background = sld
        }
    }

    private fun tintList(selected: Int, unselected: Int): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(selected, selected, unselected),
    )

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), action)
        }
    }

    private fun styleItems(bar: ViewGroup, style: Style, light: Boolean) {
        var count = 0
        walk(bar) { count++ }
        val sig = style.signature() * 31 + count
        if (styledViews[bar] == sig && !itemsNeedWork(bar, style)) return
        styledViews[bar] = sig

        val mp = if (style.materialPill) resolveMaterialPillPalette(bar.context, light, style) else null
        val miuixAccent = if (style.miuix) {
            if (xprefs.getString("miuix_accent_mode", "blue") == "green") {
                if (light) 0xFF1DB954.toInt() else 0xFF2CE072.toInt()
            } else {
                if (light) 0xFF0D84FF.toInt() else 0xFF4DA6FF.toInt()
            }
        } else 0
        val autoSelected = mp?.activeFg ?: if (miuixAccent != 0) miuixAccent else if (light) Color.BLACK else Color.WHITE
        val autoUnselected = mp?.inactiveFg ?: if (style.miuix) {
            if (light) 0xB822262E.toInt() else 0xC8E4E9F2.toInt()
        } else ((autoSelected and 0x00FFFFFF) or (0x99 shl 24))
        val customColors = mp != null || style.miuix || style.selectedColor != 0 || style.unselectedColor != 0
        val selected = mp?.activeFg ?: if (style.selectedColor != 0) style.selectedColor else autoSelected
        val unselected = mp?.inactiveFg ?: if (style.unselectedColor != 0) style.unselectedColor else autoUnselected
        val csl = tintList(selected, unselected)
        val iconPx = if (style.iconSizeDp > 0) Utils.dipToPixels(style.iconSizeDp.toFloat()) else 0

        var apiColors = true
        var apiSize = true
        var apiLabels = true
        if (customColors) {
            apiColors = callApi(bar, "setItemIconTintList", ColorStateList::class.java, csl) and
                callApi(bar, "setItemTextColor", ColorStateList::class.java, csl)
        }
        if (iconPx > 0) apiSize = callApi(bar, "setItemIconSize", Int::class.javaPrimitiveType!!, iconPx)
        if (style.hideLabels) {
            apiLabels = callApi(bar, "setLabelVisibilityMode", Int::class.javaPrimitiveType!!, 2)
        }
        if (mp != null) {
            callApi(bar, "setItemActiveIndicatorEnabled", Boolean::class.javaPrimitiveType!!, false)
            applyMaterialPillTabs(bar, mp)
        } else if (style.miuix) {
            callApi(bar, "setItemActiveIndicatorEnabled", Boolean::class.javaPrimitiveType!!, false)
            applyMiuixPillTabs(bar, light, style)
        } else if (style.indicatorColor != 0) {
            callApi(
                bar, "setItemActiveIndicatorColor", ColorStateList::class.java,
                ColorStateList.valueOf(style.indicatorColor),
            )
        }

        val indicatorId = Utils.getID("navigation_bar_item_active_indicator_view", "id")
        val metrics = bar.resources.displayMetrics
        for (i in 0 until bar.childCount) {
            walk(bar.getChildAt(i)) { v ->
                when {
                    indicatorId > 0 && v.id == indicatorId -> styleIndicator(v, style, light)
                    v is ImageView -> {
                        if (customColors && !apiColors) v.imageTintList = csl
                        if (iconPx > 0 && !apiSize) {
                            val lp = v.layoutParams
                            if (lp != null && (lp.width != iconPx || lp.height != iconPx)) {
                                lp.width = iconPx
                                lp.height = iconPx
                                v.layoutParams = lp
                            }
                        }
                    }
                    v is TextView -> {
                        if (customColors && !apiColors) v.setTextColor(csl)
                        if (style.hideLabels && !apiLabels && v.visibility != View.GONE) {
                            v.visibility = View.GONE
                        }
                        if (style.textSizeSp > 0) {
                            val px = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP, style.textSizeSp.toFloat(), metrics,
                            )
                            if (abs(v.textSize - px) > 1f) {
                                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp.toFloat())
                            }
                        }
                        val gap = Utils.dipToPixels(style.labelGapDp.toFloat()).toFloat()
                        if (v.translationY != gap) v.translationY = gap
                    }
                }
            }
        }
    }

    private fun itemsNeedWork(bar: ViewGroup, style: Style): Boolean {
        if (!style.hideLabels && style.textSizeSp == 0 && style.labelGapDp == 0) return false
        var work = false
        for (i in 0 until bar.childCount) {
            walk(bar.getChildAt(i)) { v ->
                if (v is TextView) {
                    if (style.hideLabels && v.visibility != View.GONE) work = true
                    val gap = Utils.dipToPixels(style.labelGapDp.toFloat()).toFloat()
                    if (v.translationY != gap) work = true
                }
            }
        }
        return work
    }

    private fun applyMiuixPillTabs(bar: ViewGroup, light: Boolean, style: Style) {
        val menuGroup = if (bar.childCount == 1 && bar.getChildAt(0) is ViewGroup) {
            bar.getChildAt(0) as ViewGroup
        } else {
            bar
        }
        val fill = if (style.indicatorColor != 0) {
            style.indicatorColor
        } else if (light) {
            0x8CFFFFFF.toInt()
        } else {
            0x33FFFFFF.toInt()
        }
        val stroke = if (light) 0xAAFFFFFF.toInt() else 0x42FFFFFF.toInt()
        val hPad = Utils.dipToPixels(6f)
        val vPad = Utils.dipToPixels(4f)
        for (i in 0 until menuGroup.childCount) {
            val child = menuGroup.getChildAt(i) as? ViewGroup ?: continue
            val selectedDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = Utils.dipToPixels(24f).toFloat()
                setColor(fill)
                setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), stroke)
            }
            val states = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), selectedDrawable)
                addState(intArrayOf(android.R.attr.state_checked), selectedDrawable)
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            child.background = states
            if (child.paddingLeft < hPad || child.paddingTop < vPad) {
                child.setPadding(hPad, vPad, hPad, vPad)
            }
        }
    }

    private fun styleIndicator(view: View, style: Style, light: Boolean) {
        if (style.materialPill || style.miuix) {
            view.background = null
            view.alpha = 0f
            return
        }
        if (style.ovalIndicator) {
            val fill = if (style.indicatorColor != 0) {
                style.indicatorColor
            } else if (light) {
                Color.argb(if (style.liquid) 30 else 46, 0, 0, 0)
            } else {
                Color.argb(if (style.liquid) 42 else 56, 255, 255, 255)
            }
            if (view.background !is GradientDrawable) {
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10000f
                    setColor(fill)
                }
            }
        } else if (style.indicatorColor != 0) {
            view.backgroundTintList = ColorStateList.valueOf(style.indicatorColor)
        }
    }

    private fun installTouchHub(bar: ViewGroup, container: ViewGroup, style: Style) {
        if (!style.hideOnScroll && !style.liquid) return
        val activity = findActivity(bar.context) ?: return
        val existing = touchHubs[activity]
        if (existing != null) {
            if (style.hideOnScroll && existing.scrollHider == null) {
                existing.scrollHider = ScrollHider(container, bar, style.fade, liquidControllers[bar])
            }
            if (style.liquid) {
                existing.liquidController = liquidControllers[bar]
                existing.scrollHider?.attachLiquid(liquidControllers[bar])
            }
            return
        }

        val window = activity.window ?: return
        val original = window.callback ?: return
        val hub = WindowTouchHub().apply {
            if (style.liquid) liquidController = liquidControllers[bar]
            if (style.hideOnScroll) scrollHider = ScrollHider(container, bar, style.fade, liquidController)
        }
        touchHubs[activity] = hub

        val handler = InvocationHandler { _, method, args ->
            if (method.name == "dispatchTouchEvent" && args != null && args.isNotEmpty()) {
                val ev = args[0] as? MotionEvent
                if (ev != null) {
                    val liquid = hub.liquidController
                    if (liquid != null && liquid.onDispatchTouchEvent(ev, original)) {
                        return@InvocationHandler true
                    }
                    hub.scrollHider?.onTouch(ev)
                }
            }
            try {
                method.invoke(original, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
        val proxy = Proxy.newProxyInstance(
            original.javaClass.classLoader,
            arrayOf(Window.Callback::class.java),
            handler,
        ) as Window.Callback
        window.callback = proxy
    }

    private fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun positionFabsAboveBar(rootView: ViewGroup, container: ViewGroup) {
        val barHeight = container.height
        if (barHeight <= 0) {
            container.postDelayed({ positionFabsAboveBar(rootView, container) }, 100L)
            return
        }

        val offset = -(barHeight + Utils.dipToPixels(FAB_GAP_DP)).toFloat()
        for (name in FAB_RESOURCE_NAMES) {
            val id = Utils.getID(name, "id")
            if (id <= 0) continue
            rootView.findViewById<View>(id)?.let { fab ->
                fab.translationY = offset
                bringToFrontIfNeeded(fab)
            }
        }
    }

    private fun positionFabAboveCurrentBar(fab: View, bottomNavId: Int) {
        val bottomNav = fab.rootView.findViewById<View>(bottomNavId) ?: return
        val container = bottomNav.parent as? ViewGroup ?: return
        if (container.parent !is FrameLayout) return
        positionFab(fab, container)
    }

    private fun positionFab(fab: View, container: ViewGroup) {
        val barHeight = container.height
        if (barHeight <= 0) {
            container.postDelayed({ positionFab(fab, container) }, 100L)
            return
        }

        fab.translationY = -(barHeight + Utils.dipToPixels(FAB_GAP_DP)).toFloat()
        bringToFrontIfNeeded(fab)
    }

    private fun bringToFrontIfNeeded(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        if (parent.indexOfChild(view) != parent.childCount - 1) view.bringToFront()
    }

    private fun resolveBarColor(bar: ViewGroup): Int {
        val currentBg = bar.background
        if (currentBg is ColorDrawable) {
            return currentBg.color
        }
        return try {
            val typedValue = TypedValue()
            bar.context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            typedValue.data
        } catch (_: Throwable) {
            0xFF121212.toInt()
        }
    }

    private fun isLightColor(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return (red * 299 + green * 587 + blue * 114) / 1000 > 180
    }

    override fun getPluginName(): String {
        return "Floating Bottom Bar"
    }

    private class ScrollHider(
        private val container: ViewGroup,
        private val bar: ViewGroup,
        private val fade: Boolean,
        private var liquid: LiquidGlassController? = null,
    ) {
        private var lastY = 0f
        private var accumulated = 0f
        private var tracking = false
        private var hidden = false

        fun attachLiquid(controller: LiquidGlassController?) {
            liquid = controller
        }

        fun onTouch(e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracking = !insideBar(e)
                    lastY = e.rawY
                    accumulated = 0f
                }

                MotionEvent.ACTION_MOVE -> if (tracking) {
                    val dy = e.rawY - lastY
                    lastY = e.rawY
                    if (dy * accumulated < 0f) accumulated = 0f
                    accumulated += dy
                    val threshold = container.resources.displayMetrics.density * 24f
                    if (accumulated < -threshold && !hidden) {
                        setHidden(true)
                    } else if (accumulated > threshold && hidden) {
                        setHidden(false)
                    }
                }
            }
        }

        private fun insideBar(e: MotionEvent): Boolean {
            val loc = IntArray(2)
            bar.getLocationOnScreen(loc)
            return e.rawX >= loc[0] && e.rawX <= loc[0] + bar.width &&
                e.rawY >= loc[1] && e.rawY <= loc[1] + bar.height
        }

        private fun setHidden(value: Boolean) {
            hidden = value
            container.animate().cancel()
            val overlay = liquid?.overlayView
            overlay?.animate()?.cancel()
            if (value) {
                liquid?.cancelDragImmediate()
            }
            if (fade) {
                if (!value) {
                    container.visibility = View.VISIBLE
                    overlay?.visibility = View.VISIBLE
                }
                container.animate().alpha(if (value) 0f else 1f).setDuration(180L)
                    .withEndAction { if (hidden) container.visibility = View.INVISIBLE }
                    .start()
                overlay?.animate()?.alpha(if (value) 0f else 1f)?.setDuration(180L)
                    ?.withEndAction { if (hidden) overlay.visibility = View.INVISIBLE }
                    ?.start()
            } else {
                val margin = (container.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                val ty = if (value) (container.height + margin).toFloat() else 0f
                container.animate().translationY(ty).setDuration(220L).start()
                overlay?.animate()?.translationY(ty)?.setDuration(220L)?.start()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object LiquidAgsl {
        private const val DOCK_SHADER = """
            uniform shader content;
            uniform float2 center;
            uniform float2 halfSize;
            uniform float radius;
            uniform float2 texSize;
            uniform float refractPx;
            uniform float dispersion;
            uniform float bezel;
            uniform float isDark;

            float sdRoundedBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + float2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            half4 main(float2 fragCoord) {
                float2 p = fragCoord - center;
                float d = sdRoundedBox(p, halfSize, radius);
                if (d > 1.5) {
                    return half4(0.0, 0.0, 0.0, 0.0);
                }
                float alpha = 1.0 - smoothstep(-0.8, 0.7, d);
                float edgeDist = max(-d, 0.0);

                float2 grad = float2(
                    sdRoundedBox(p + float2(1.0, 0.0), halfSize, radius) - sdRoundedBox(p - float2(1.0, 0.0), halfSize, radius),
                    sdRoundedBox(p + float2(0.0, 1.0), halfSize, radius) - sdRoundedBox(p - float2(0.0, 1.0), halfSize, radius)
                );
                float2 n = grad / max(length(grad), 0.0001);
                float rim = 1.0 - smoothstep(0.0, max(bezel, 1.0), edgeDist);
                float rim2 = rim * rim;

                float2 baseCoord = fragCoord - n * (rim2 * refractPx);
                float2 disp = n * (rim2 * dispersion);
                float2 lo = float2(1.0, 1.0);
                float2 hi = max(texSize - float2(1.0, 1.0), lo);

                half4 cR = content.eval(clamp(baseCoord - disp, lo, hi));
                half4 cG = content.eval(clamp(baseCoord, lo, hi));
                half4 cB = content.eval(clamp(baseCoord + disp, lo, hi));
                half3 rgb = half3(cR.r, cG.g, cB.b);

                float2 light1 = normalize(float2(-0.35, -0.94));
                float2 light2 = normalize(float2(0.35, 0.94));
                float rimBand = 1.0 - smoothstep(0.0, max(bezel * 0.45, 2.0), edgeDist);
                float spec1 = pow(max(dot(-n, light1), 0.0), 2.2) * rimBand;
                float spec2 = pow(max(dot(-n, light2), 0.0), 3.0) * rimBand * 0.48;
                float crisp = 1.0 - smoothstep(0.0, 2.2, edgeDist);
                float normY = clamp((p.y + halfSize.y) / max(halfSize.y * 2.0, 1.0), 0.0, 1.0);
                float sheen = pow(1.0 - normY, 2.5) * (isDark > 0.5 ? 0.14 : 0.22);

                half h = half(spec1 * 0.46 + spec2 * 0.28 + crisp * (isDark > 0.5 ? 0.26 : 0.36) + sheen);
                rgb = clamp(rgb + half3(h, h, h), half3(0.0, 0.0, 0.0), half3(1.0, 1.0, 1.0));
                return half4(rgb * half(alpha), half(alpha));
            }
        """

        private const val LENS_SHADER = """
            uniform shader content;
            uniform float2 center;
            uniform float2 halfSize;
            uniform float radius;
            uniform float2 texSize;
            uniform float refractPx;
            uniform float dispersion;
            uniform float magnify;
            uniform float bezel;
            uniform float isDark;
            uniform float strength;
            uniform float velTilt;

            float sdRoundedBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + float2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            half4 main(float2 fragCoord) {
                float2 p = fragCoord - center;
                float d = sdRoundedBox(p, halfSize, radius);
                if (d > 2.0) {
                    return half4(0.0, 0.0, 0.0, 0.0);
                }
                float alpha = (1.0 - smoothstep(-1.0, 0.8, d)) * strength;
                float edgeDist = max(-d, 0.0);

                float2 grad = float2(
                    sdRoundedBox(p + float2(1.0, 0.0), halfSize, radius) - sdRoundedBox(p - float2(1.0, 0.0), halfSize, radius),
                    sdRoundedBox(p + float2(0.0, 1.0), halfSize, radius) - sdRoundedBox(p - float2(0.0, 1.0), halfSize, radius)
                );
                float2 n = grad / max(length(grad), 0.0001);

                float rim = 1.0 - smoothstep(0.0, max(bezel, 1.0), edgeDist);
                float rimCurve = rim * rim;

                float2 magOffset = -p * (magnify * strength);
                float2 refractOffset = -n * (rimCurve * refractPx * strength);
                float2 baseCoord = fragCoord + magOffset + refractOffset;

                float2 disp = n * (rimCurve * dispersion * strength);
                float2 lo = float2(1.0, 1.0);
                float2 hi = max(texSize - float2(1.0, 1.0), lo);

                half4 cR = content.eval(clamp(baseCoord - disp, lo, hi));
                half4 cG = content.eval(clamp(baseCoord, lo, hi));
                half4 cB = content.eval(clamp(baseCoord + disp, lo, hi));
                half3 rgb = half3(cR.r, cG.g, cB.b);

                float2 light1 = normalize(float2(-0.34 + velTilt * 0.45, -0.94));
                float2 light2 = normalize(float2(0.34 - velTilt * 0.45, 0.94));
                float rimBand = 1.0 - smoothstep(0.0, max(bezel * 0.50, 2.0), edgeDist);
                float spec1 = pow(max(dot(-n, light1), 0.0), 1.9) * rimBand;
                float spec2 = pow(max(dot(-n, light2), 0.0), 2.5) * rimBand * 0.62;
                float crispEdge = 1.0 - smoothstep(0.0, 2.6, edgeDist);

                float normY = clamp((p.y + halfSize.y) / max(halfSize.y * 2.0, 1.0), 0.0, 1.0);
                float topSheen = pow(1.0 - normY, 2.2) * (isDark > 0.5 ? 0.14 : 0.22);

                half3 tint = isDark > 0.5 ? half3(0.88, 0.95, 1.0) : half3(1.0, 1.0, 1.0);
                float tintMix = (isDark > 0.5 ? 0.06 : 0.12) * strength;
                rgb = mix(rgb, tint, half(tintMix));

                half h = half((spec1 * 0.68 + spec2 * 0.40 + crispEdge * (isDark > 0.5 ? 0.42 : 0.52) + topSheen) * strength);
                rgb = clamp(rgb + half3(h, h, h), half3(0.0, 0.0, 0.0), half3(1.0, 1.0, 1.0));
                return half4(rgb * half(alpha), half(alpha));
            }
        """

        fun createDockShader(): RuntimeShader = RuntimeShader(DOCK_SHADER)

        fun createLensShader(): RuntimeShader = RuntimeShader(LENS_SHADER)

        fun updateDockShader(
            shader: Any,
            content: BitmapShader,
            rect: RectF,
            radius: Float,
            density: Float,
            dark: Boolean,
        ): Shader {
            val rs = shader as RuntimeShader
            rs.setInputShader("content", content)
            rs.setFloatUniform("center", rect.centerX(), rect.centerY())
            rs.setFloatUniform("halfSize", rect.width() * 0.5f, rect.height() * 0.5f)
            rs.setFloatUniform("radius", min(radius, min(rect.width(), rect.height()) * 0.5f))
            rs.setFloatUniform("texSize", rect.width(), rect.height())
            rs.setFloatUniform("refractPx", 11f * density)
            rs.setFloatUniform("dispersion", 2.2f * density)
            rs.setFloatUniform("bezel", 18f * density)
            rs.setFloatUniform("isDark", if (dark) 1f else 0f)
            return rs
        }

        fun updateLensShader(
            shader: Any,
            content: BitmapShader,
            cx: Float,
            cy: Float,
            halfW: Float,
            halfH: Float,
            radius: Float,
            texW: Float,
            texH: Float,
            density: Float,
            dark: Boolean,
            strength: Float,
            velTilt: Float,
        ): Shader {
            val rs = shader as RuntimeShader
            rs.setInputShader("content", content)
            rs.setFloatUniform("center", cx, cy)
            rs.setFloatUniform("halfSize", halfW, halfH)
            rs.setFloatUniform("radius", min(radius, min(halfW, halfH)))
            rs.setFloatUniform("texSize", texW, texH)
            rs.setFloatUniform("refractPx", 22f * density)
            rs.setFloatUniform("dispersion", 3.4f * density)
            rs.setFloatUniform("magnify", 0.16f)
            rs.setFloatUniform("bezel", 24f * density)
            rs.setFloatUniform("isDark", if (dark) 1f else 0f)
            rs.setFloatUniform("strength", strength)
            rs.setFloatUniform("velTilt", velTilt.coerceIn(-1f, 1f))
            return rs
        }
    }

    private class LiquidGlassController(
        private val rootView: FrameLayout,
        private val container: ViewGroup,
        private val bar: ViewGroup,
        private val light: Boolean,
        private val tintColor: Int,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val touchSlop = ViewConfiguration.get(bar.context).scaledTouchSlop.toFloat()
        private var tracking = false
        private var dragging = false
        private var dispatchingSynthetic = false
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastHoveredIdx = -1
        private var cachedTabs: List<View> = emptyList()

        val overlayView = LiquidLensView(
            context = bar.context,
            rootView = rootView,
            container = container,
            bar = bar,
            light = light,
            tintColor = tintColor,
        )

        private val holdRunnable = Runnable {
            if (tracking && !dragging) {
                enterDragMode(lastRawX, lastCallback)
            }
        }

        private var lastCallback: Window.Callback? = null

        fun attach() {
            if (overlayView.parent !== rootView) {
                (overlayView.parent as? ViewGroup)?.removeView(overlayView)
                rootView.addView(
                    overlayView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM,
                    ),
                )
            }
            overlayView.bringToFront()
            syncLayout()
        }

        fun detach() {
            handler.removeCallbacks(holdRunnable)
            overlayView.stopAnimations()
            (overlayView.parent as? ViewGroup)?.removeView(overlayView)
        }

        fun cancelDragImmediate() {
            handler.removeCallbacks(holdRunnable)
            tracking = false
            dragging = false
            lastCallback = null
            overlayView.hideImmediate()
        }

        fun syncLayout() {
            bar.post {
                if (!bar.isAttachedToWindow || bar.width <= 0 || bar.height <= 0) return@post
                val tabs = findTabViews(bar)
                cachedTabs = tabs
                overlayView.syncBounds(tabs, findSelectedIndex(tabs))
                overlayView.bringToFront()
            }
        }

        fun onDispatchTouchEvent(ev: MotionEvent, original: Window.Callback): Boolean {
            if (dispatchingSynthetic) return false
            if (!bar.isAttachedToWindow || bar.visibility != View.VISIBLE ||
                container.visibility != View.VISIBLE || container.translationY > 12f
            ) {
                return false
            }

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(holdRunnable)
                    dragging = false
                    tracking = isInsideBar(ev)
                    if (tracking) {
                        lastCallback = original
                        downRawX = ev.rawX
                        downRawY = ev.rawY
                        lastRawX = ev.rawX
                        cachedTabs = findTabViews(bar)
                        if (cachedTabs.size >= 2) {
                            overlayView.syncBounds(cachedTabs, findSelectedIndex(cachedTabs))
                            handler.postDelayed(holdRunnable, 115L)
                        } else {
                            tracking = false
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return false
                    lastRawX = ev.rawX
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging) {
                        if (abs(dy) > touchSlop * 1.4f && abs(dy) > abs(dx) * 1.3f) {
                            handler.removeCallbacks(holdRunnable)
                            tracking = false
                            return false
                        }
                        if (abs(dx) > touchSlop * 0.50f && abs(dx) > abs(dy)) {
                            handler.removeCallbacks(holdRunnable)
                            enterDragMode(ev.rawX, original)
                        }
                    }
                    if (dragging) {
                        val hoverIdx = overlayView.onDragMove(ev.rawX)
                        if (hoverIdx != lastHoveredIdx && hoverIdx in cachedTabs.indices) {
                            lastHoveredIdx = hoverIdx
                            bar.performHapticFeedback(
                                HapticFeedbackConstants.CLOCK_TICK,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        return true
                    }
                    return false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdRunnable)
                    if (!tracking) return false
                    tracking = false
                    lastCallback = null
                    if (dragging) {
                        dragging = false
                        val targetIdx = overlayView.onDragEnd(ev.rawX)
                        if (ev.actionMasked == MotionEvent.ACTION_UP && targetIdx in cachedTabs.indices) {
                            val tab = cachedTabs[targetIdx]
                            tab.performClick()
                            bar.postDelayed({
                                val tabs = findTabViews(bar)
                                cachedTabs = tabs
                                overlayView.syncBounds(tabs, findSelectedIndex(tabs))
                            }, 85L)
                        }
                        return true
                    }
                    return false
                }
            }
            return false
        }

        private fun enterDragMode(rawX: Float, original: Window.Callback?) {
            if (dragging || cachedTabs.size < 2) return
            dragging = true
            lastHoveredIdx = overlayView.onDragStart(rawX)
            bar.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
            if (original != null) {
                val now = SystemClock.uptimeMillis()
                val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, rawX, downRawY, 0)
                dispatchingSynthetic = true
                try {
                    original.dispatchTouchEvent(cancel)
                } catch (_: Throwable) {
                } finally {
                    dispatchingSynthetic = false
                    cancel.recycle()
                }
            }
        }

        private fun isInsideBar(e: MotionEvent): Boolean {
            val loc = IntArray(2)
            bar.getLocationOnScreen(loc)
            val pad = bar.resources.displayMetrics.density * 6f
            return e.rawX >= loc[0] - pad && e.rawX <= loc[0] + bar.width + pad &&
                e.rawY >= loc[1] - pad && e.rawY <= loc[1] + bar.height + pad
        }

        private fun findTabViews(root: ViewGroup): List<View> {
            val menuGroup = if (root.childCount == 1 && root.getChildAt(0) is ViewGroup) {
                root.getChildAt(0) as ViewGroup
            } else {
                findHorizontalMenuGroup(root) ?: root
            }
            val list = mutableListOf<View>()
            for (i in 0 until menuGroup.childCount) {
                val c = menuGroup.getChildAt(i)
                if (c.visibility == View.VISIBLE && c.width > 0) {
                    list.add(c)
                }
            }
            list.sortBy { it.left }
            return list
        }

        private fun findHorizontalMenuGroup(group: ViewGroup): ViewGroup? {
            var visibleCount = 0
            for (i in 0 until group.childCount) {
                val c = group.getChildAt(i)
                if (c.visibility == View.VISIBLE && c.width > 0) visibleCount++
            }
            if (visibleCount in 2..6) {
                val first = group.getChildAt(0)
                val second = group.getChildAt(1)
                if (abs(second.left - first.left) > abs(second.top - first.top)) {
                    return group
                }
            }
            for (i in 0 until group.childCount) {
                val c = group.getChildAt(i) as? ViewGroup ?: continue
                val found = findHorizontalMenuGroup(c)
                if (found != null) return found
            }
            return null
        }

        private fun findSelectedIndex(tabs: List<View>): Int {
            tabs.forEachIndexed { idx, v ->
                if (v.isSelected || hasSelectedDescendant(v)) return idx
            }
            return 0
        }

        private fun hasSelectedDescendant(v: View): Boolean {
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val c = v.getChildAt(i)
                    if (c.isSelected || hasSelectedDescendant(c)) return true
                }
            }
            return false
        }
    }

    private class LiquidLensView(
        context: Context,
        private val rootView: FrameLayout,
        private val container: ViewGroup,
        private val bar: ViewGroup,
        private val light: Boolean,
        private val tintColor: Int,
    ) : View(context) {

        private val density = context.resources.displayMetrics.density
        private val padV = (26f * density).toInt()

        private val barWinLoc = IntArray(2)
        private val overlayWinLoc = IntArray(2)
        private val rootWinLoc = IntArray(2)
        private val tabCenters = mutableListOf<Float>()
        private val tabWidths = mutableListOf<Float>()
        private var tabHeight = 0f
        private var barCenterY = 0f
        private var initialized = false

        private var curX = 0f
        private var velX = 0f
        private var targetX = 0f

        private var curW = 0f
        private var velW = 0f
        private var targetW = 0f

        private var dragProgress = 0f
        private var velDrag = 0f
        private var targetDrag = 0f

        private var stretch = 0f
        private var velStretch = 0f
        private var tilt = 0f
        private var lastPointerX = 0f
        private var lastPointerTimeMs = 0L
        private var pointerVelX = 0f

        private var isDragging = false
        private var animating = false
        private var lastFrameNanos = 0L

        private var snapshotBmp: Bitmap? = null
        private var snapshotShader: BitmapShader? = null
        private val shaderMatrix = Matrix()
        private var agslShader: Any? = null

        private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val fallbackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fallbackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val drawRect = RectF()

        private val frameRunner = Runnable { stepFrame() }

        init {
            isClickable = false
            isFocusable = false
            setWillNotDraw(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                agslShader = runCatching { LiquidAgsl.createLensShader() }.getOrNull()
            }
        }

        fun stopAnimations() {
            animating = false
            removeCallbacks(frameRunner)
        }

        fun hideImmediate() {
            isDragging = false
            targetDrag = 0f
            dragProgress = 0f
            velDrag = 0f
            stretch = 0f
            velStretch = 0f
            tilt = 0f
            animating = false
            removeCallbacks(frameRunner)
            invalidate()
        }

        fun syncBounds(tabs: List<View>, activeIdx: Int) {
            if (bar.width <= 0 || bar.height <= 0) return

            val containerMargin = (container.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            val desiredH = bar.height + padV * 2
            val desiredBottom = containerMargin - padV

            val lp = layoutParams as? FrameLayout.LayoutParams
            if (lp != null && (lp.gravity != Gravity.BOTTOM ||
                    lp.width != FrameLayout.LayoutParams.MATCH_PARENT ||
                    lp.height != desiredH ||
                    lp.bottomMargin != desiredBottom)
            ) {
                lp.gravity = Gravity.BOTTOM
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = desiredH
                lp.bottomMargin = desiredBottom
                layoutParams = lp
            }
            if (abs(translationY - container.translationY) > 0.5f && !isDragging) {
                translationY = container.translationY
            }

            rootView.getLocationInWindow(rootWinLoc)
            bar.getLocationInWindow(barWinLoc)

            tabCenters.clear()
            tabWidths.clear()
            val tabLoc = IntArray(2)
            for (t in tabs) {
                t.getLocationInWindow(tabLoc)
                val cx = (tabLoc[0] - rootWinLoc[0]) + t.width * 0.5f
                tabCenters.add(cx)
                val minPillW = bar.height * 1.18f
                tabWidths.add(max(t.width * 0.94f, minPillW))
            }
            tabHeight = max(bar.height * 0.80f, 44f * density)
            barCenterY = desiredH * 0.5f

            val clampedIdx = activeIdx.coerceIn(0, (tabCenters.size - 1).coerceAtLeast(0))
            if (tabCenters.isNotEmpty()) {
                if (!initialized) {
                    initialized = true
                    curX = tabCenters[clampedIdx]
                    targetX = curX
                    curW = tabWidths[clampedIdx]
                    targetW = curW
                } else if (!isDragging && dragProgress <= 0.01f) {
                    curX = tabCenters[clampedIdx]
                    targetX = curX
                    curW = tabWidths[clampedIdx]
                    targetW = curW
                }
            }
        }

        private fun rawXToLocal(rawX: Float): Float {
            getLocationOnScreen(overlayWinLoc)
            val desired = rawX - overlayWinLoc[0]
            val minX = if (tabCenters.isNotEmpty()) tabCenters.first() else 48f * density
            val maxX = if (tabCenters.isNotEmpty()) tabCenters.last() else (width - 48f * density)
            return when {
                desired < minX -> minX - (minX - desired) * 0.22f
                desired > maxX -> maxX + (desired - maxX) * 0.22f
                else -> desired
            }
        }

        private fun nearestTabIndex(localX: Float): Int {
            if (tabCenters.isEmpty()) return 0
            var best = 0
            var bestDist = Float.MAX_VALUE
            for (i in tabCenters.indices) {
                val d = abs(tabCenters[i] - localX)
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            return best
        }

        fun onDragStart(rawX: Float): Int {
            isDragging = true
            val localX = rawXToLocal(rawX)
            val idx = nearestTabIndex(localX)
            curX = localX
            targetX = localX
            velX = 0f
            if (idx in tabWidths.indices) {
                curW = tabWidths[idx]
                targetW = tabWidths[idx]
            }
            velW = 0f
            stretch = -0.08f
            velStretch = 1.6f
            pointerVelX = 0f
            lastPointerX = localX
            lastPointerTimeMs = SystemClock.uptimeMillis()
            targetDrag = 1f
            captureSnapshot()
            startPhysics()
            return idx
        }

        fun onDragMove(rawX: Float): Int {
            val localX = rawXToLocal(rawX)
            val nowMs = SystemClock.uptimeMillis()
            val dtSec = ((nowMs - lastPointerTimeMs).coerceAtLeast(4L)) / 1000f
            val instVel = ((localX - lastPointerX) / dtSec).coerceIn(-6000f * density, 6000f * density)
            pointerVelX = pointerVelX * 0.45f + instVel * 0.55f
            lastPointerX = localX
            lastPointerTimeMs = nowMs

            targetX = localX
            val idx = nearestTabIndex(localX)
            if (idx in tabWidths.indices) targetW = tabWidths[idx]
            startPhysics()
            return idx
        }

        fun onDragEnd(rawX: Float): Int {
            isDragging = false
            targetDrag = 0f
            val projected = rawXToLocal(rawX) + (velX * 0.045f)
            val idx = nearestTabIndex(projected)
            if (idx in tabCenters.indices) {
                targetX = tabCenters[idx]
                targetW = tabWidths[idx]
            }
            pointerVelX = 0f
            startPhysics()
            return idx
        }

        private fun startPhysics() {
            if (animating) return
            animating = true
            lastFrameNanos = System.nanoTime()
            postOnAnimation(frameRunner)
        }

        private fun stepFrame() {
            if (!animating || !isAttachedToWindow) {
                animating = false
                return
            }
            val now = System.nanoTime()
            val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.032f)
            lastFrameNanos = now

            pointerVelX *= 0.86f

            val xStiff = if (isDragging) 340f else 280f
            val xDamp = if (isDragging) 21f else 19f
            val ax = (targetX - curX) * xStiff - velX * xDamp
            velX += ax * dt
            curX += velX * dt

            val aw = (targetW - curW) * 320f - velW * 22f
            velW += aw * dt
            curW += velW * dt

            val dStiff = if (isDragging) 420f else 320f
            val dDamp = if (isDragging) 22f else 24f
            val ad = (targetDrag - dragProgress) * dStiff - velDrag * dDamp
            velDrag += ad * dt
            dragProgress = (dragProgress + velDrag * dt).coerceIn(0f, 1.18f)

            val combinedVel = if (isDragging) {
                if (abs(pointerVelX) > abs(velX)) pointerVelX else velX
            } else {
                velX
            }
            val speedNorm = (abs(combinedVel) / (density * 620f)).coerceIn(0f, 1f)
            val targetStretch = speedNorm * 0.42f * dragProgress.coerceIn(0f, 1f)
            val asq = (targetStretch - stretch) * 230f - velStretch * 12f
            velStretch += asq * dt
            stretch = (stretch + velStretch * dt).coerceIn(-0.14f, 0.48f)

            val targetTilt = (combinedVel / (density * 700f)).coerceIn(-1f, 1f)
            tilt += (targetTilt - tilt) * (dt * 14f).coerceAtMost(1f)

            invalidate()

            val settled = !isDragging &&
                dragProgress <= 0.008f && abs(velDrag) < 0.05f &&
                abs(stretch) < 0.008f && abs(velStretch) < 0.05f

            if (settled) {
                dragProgress = 0f
                velDrag = 0f
                stretch = 0f
                velStretch = 0f
                velX = 0f
                velW = 0f
                tilt = 0f
                animating = false
                invalidate()
            } else {
                postOnAnimation(frameRunner)
            }
        }

        private fun captureSnapshot() {
            try {
                val w = width
                val h = height
                if (w <= 0 || h <= 0 || !isAttachedToWindow) return
                val bmp = snapshotBmp?.takeIf { it.width == w && it.height == h }
                    ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { snapshotBmp = it }
                val c = Canvas(bmp)
                c.drawColor(tintColor)

                getLocationInWindow(overlayWinLoc)
                rootView.getLocationInWindow(rootWinLoc)
                bar.getLocationInWindow(barWinLoc)

                val rootDx = (rootWinLoc[0] - overlayWinLoc[0]).toFloat()
                val rootDy = (rootWinLoc[1] - overlayWinLoc[1]).toFloat()

                c.save()
                c.translate(rootDx, rootDy)
                for (i in 0 until rootView.childCount) {
                    val child = rootView.getChildAt(i)
                    if (child === this || child === container || child.visibility != View.VISIBLE) continue
                    c.save()
                    c.translate(child.left + child.translationX, child.top + child.translationY)
                    try {
                        child.draw(c)
                    } catch (_: Throwable) {
                    }
                    c.restore()
                }
                c.restore()

                val barDx = (barWinLoc[0] - overlayWinLoc[0]).toFloat()
                val barDy = (barWinLoc[1] - overlayWinLoc[1]).toFloat()
                c.save()
                c.translate(barDx, barDy)
                try {
                    bar.draw(c)
                } catch (_: Throwable) {
                }
                c.restore()

                snapshotShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            } catch (_: Throwable) {
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val p = dragProgress.coerceIn(0f, 1.18f)
            if (!initialized || width <= 0 || height <= 0 || curW <= 0f || p <= 0.008f) return

            val alphaStrength = p.coerceIn(0f, 1f)
            val popScale = 0.78f + 0.22f * p
            val cy = if (barCenterY > 0f) barCenterY else height * 0.5f

            val w = curW * popScale * (1.06f + stretch)
            val h = tabHeight * popScale * (1.20f - stretch * 0.52f).coerceAtLeast(0.76f)
            val halfW = w * 0.5f
            val halfH = h * 0.5f
            val radius = halfH

            drawRect.set(curX - halfW, cy - halfH, curX + halfW, cy + halfH)

            shadowPaint.color = Color.argb((42 * alphaStrength).toInt(), 0, 0, 0)
            shadowPaint.setShadowLayer(
                14f * density * alphaStrength,
                0f,
                4f * density * alphaStrength,
                Color.argb((96 * alphaStrength).toInt(), 0, 0, 0),
            )
            canvas.drawRoundRect(drawRect, radius, radius, shadowPaint)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                val bmp = snapshotBmp
                val bShader = snapshotShader
                val rs = agslShader
                if (bmp != null && bShader != null && rs != null) {
                    shaderMatrix.reset()
                    if (bmp.width != width || bmp.height != height) {
                        shaderMatrix.setScale(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                    }
                    bShader.setLocalMatrix(shaderMatrix)
                    lensPaint.shader = LiquidAgsl.updateLensShader(
                        shader = rs,
                        content = bShader,
                        cx = curX,
                        cy = cy,
                        halfW = halfW,
                        halfH = halfH,
                        radius = radius,
                        texW = width.toFloat(),
                        texH = height.toFloat(),
                        density = density,
                        dark = !light,
                        strength = alphaStrength,
                        velTilt = tilt,
                    )
                    lensPaint.alpha = 255
                    canvas.drawRoundRect(drawRect, radius, radius, lensPaint)
                    return
                }
            }

            fallbackFillPaint.color = if (light) {
                Color.argb((54 * alphaStrength).toInt(), 255, 255, 255)
            } else {
                Color.argb((46 * alphaStrength).toInt(), 255, 255, 255)
            }
            canvas.drawRoundRect(drawRect, radius, radius, fallbackFillPaint)
            fallbackStrokePaint.shader = LinearGradient(
                drawRect.left, drawRect.top, drawRect.left, drawRect.bottom,
                intArrayOf(
                    Color.argb((190 * alphaStrength).toInt(), 255, 255, 255),
                    Color.argb((55 * alphaStrength).toInt(), 255, 255, 255),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
            val inset = fallbackStrokePaint.strokeWidth * 0.5f
            canvas.drawRoundRect(
                drawRect.left + inset, drawRect.top + inset,
                drawRect.right - inset, drawRect.bottom - inset,
                radius, radius, fallbackStrokePaint,
            )
        }
    }

    private class GlassDrawable(
        private val container: ViewGroup,
        private val bar: ViewGroup,
        private val radius: Float,
        private val tint: Int,
        private val opacityPercent: Int,
        private val light: Boolean,
        private val edgeWidth: Float,
        private val liquidMode: Boolean = false,
    ) : Drawable() {

        private val density = bar.resources.displayMetrics.density
        private val rect = RectF()
        private val matrix = Matrix()
        private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(if (liquidMode) 1.6f else 1.45f) })
        }
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = edgeWidth
        }
        private var dockShader: Any? = null
        private var work: Bitmap? = null
        private var shown: Bitmap? = null
        private var shader: BitmapShader? = null
        private var lastHash = 0
        private var lastRun = 0L
        private var pending = false
        private var observer: ViewTreeObserver? = null
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener { request() }

        init {
            if (liquidMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                dockShader = runCatching { LiquidAgsl.createDockShader() }.getOrNull()
            }
        }

        fun attach() {
            detach()
            observer = bar.viewTreeObserver
            observer?.addOnScrollChangedListener(scrollListener)
            bar.postDelayed({ capture() }, 150L)
            bar.postDelayed({ capture() }, 600L)
        }

        fun detach() {
            observer?.let { if (it.isAlive) it.removeOnScrollChangedListener(scrollListener) }
            observer = null
        }

        private fun request() {
            val now = SystemClock.uptimeMillis()
            val interval = if (liquidMode) 55L else 80L
            if (now - lastRun >= interval) {
                lastRun = now
                capture()
            } else if (!pending) {
                pending = true
                bar.postDelayed({
                    pending = false
                    lastRun = SystemClock.uptimeMillis()
                    capture()
                }, interval)
            }
        }

        private fun capture() {
            try {
                val w = bar.width
                val h = bar.height
                if (w <= 0 || h <= 0 || !bar.isAttachedToWindow) return
                val root = container.parent as? ViewGroup ?: return
                val scale = if (liquidMode) 4 else 8
                val sw = (w / scale).coerceAtLeast(2)
                val sh = (h / scale).coerceAtLeast(2)
                val bmp = work?.takeIf { it.width == sw && it.height == sh }
                    ?: Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { work = it }
                val canvas = Canvas(bmp)
                canvas.drawColor(tint)
                val barLoc = IntArray(2)
                val rootLoc = IntArray(2)
                bar.getLocationInWindow(barLoc)
                root.getLocationInWindow(rootLoc)
                canvas.scale(1f / scale, 1f / scale)
                canvas.translate(
                    -(barLoc[0] - rootLoc[0]).toFloat(),
                    -(barLoc[1] - rootLoc[1]).toFloat(),
                )
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    if (child === container || child is LiquidLensView || child.visibility != View.VISIBLE) continue
                    canvas.save()
                    canvas.translate(
                        child.left + child.translationX,
                        child.top + child.translationY,
                    )
                    try {
                        child.draw(canvas)
                    } catch (_: Throwable) {
                    }
                    canvas.restore()
                }
                val pixels = IntArray(sw * sh)
                bmp.getPixels(pixels, 0, sw, 0, 0, sw, sh)
                var hash = 1
                var k = 0
                val step = if (liquidMode) 9 else 5
                while (k < pixels.size) {
                    hash = 31 * hash + pixels[k]
                    k += step
                }
                if (hash == lastHash && shown != null) return
                lastHash = hash
                val a = pixels
                val b = IntArray(pixels.size)
                val passes = if (liquidMode) 2 else 3
                repeat(passes) {
                    boxPass(a, b, sw, sh, 2, true)
                    boxPass(b, a, sw, sh, 2, false)
                }
                val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
                out.setPixels(a, 0, sw, 0, 0, sw, sh)
                shown = out
                shader = BitmapShader(out, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                invalidateSelf()
            } catch (_: Throwable) {
            }
        }

        private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            val div = 2 * r + 1
            for (line in 0 until lines) {
                var sr = 0
                var sg = 0
                var sb = 0
                for (i in -r..r) {
                    val idx = i.coerceIn(0, len - 1)
                    val p = if (horizontal) src[line * w + idx] else src[idx * w + line]
                    sr += (p shr 16) and 0xFF
                    sg += (p shr 8) and 0xFF
                    sb += p and 0xFF
                }
                for (i in 0 until len) {
                    val o = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                    if (horizontal) dst[line * w + i] = o else dst[i * w + line] = o
                    val addIdx = (i + r + 1).coerceIn(0, len - 1)
                    val remIdx = (i - r).coerceIn(0, len - 1)
                    val add = if (horizontal) src[line * w + addIdx] else src[addIdx * w + line]
                    val rem = if (horizontal) src[line * w + remIdx] else src[remIdx * w + line]
                    sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                    sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                    sb += (add and 0xFF) - (rem and 0xFF)
                }
            }
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            rect.set(bounds)
            sheenPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(
                    Color.argb(if (liquidMode) 82 else 70, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP,
            )
            edgePaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(
                    Color.argb(if (light) 135 else 170, 255, 255, 255),
                    Color.argb(if (light) 35 else 52, 255, 255, 255),
                ),
                null, Shader.TileMode.CLAMP,
            )
        }

        override fun draw(canvas: Canvas) {
            if (rect.isEmpty) rect.set(bounds)
            val bmp = shown
            val sh = shader
            val useAgsl = canvas.isHardwareAccelerated &&
                liquidMode &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                dockShader != null
            if (bmp != null && sh != null) {
                matrix.reset()
                matrix.setScale(rect.width() / bmp.width, rect.height() / bmp.height)
                matrix.postTranslate(rect.left, rect.top)
                sh.setLocalMatrix(matrix)
                if (useAgsl) {
                    backdropPaint.shader = LiquidAgsl.updateDockShader(
                        shader = dockShader!!,
                        content = sh,
                        rect = rect,
                        radius = radius,
                        density = density,
                        dark = !light,
                    )
                } else {
                    backdropPaint.shader = sh
                }
                canvas.drawRoundRect(rect, radius, radius, backdropPaint)
            }
            tintPaint.color = (tint and 0x00FFFFFF) or ((opacityPercent.coerceIn(0, 100) * 255 / 100) shl 24)
            canvas.drawRoundRect(rect, radius, radius, tintPaint)
            if (!useAgsl) {
                canvas.drawRoundRect(rect, radius, radius, sheenPaint)
            }
            val inset = edgeWidth / 2f
            canvas.drawRoundRect(
                rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset,
                radius, radius, edgePaint,
            )
        }

        override fun getOutline(outline: Outline) {
            outline.setRoundRect(bounds, radius)
            outline.alpha = 0.45f
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
