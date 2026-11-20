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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.WeakHashMap

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
            )) h = 31 * h + v
            return h
        }
    }

    private val processedBars = WeakHashMap<ViewGroup, Boolean>()
    private val setupAttempts = WeakHashMap<ViewGroup, Int>()
    private val basePadding = WeakHashMap<ViewGroup, IntArray>()
    private val paddingGuarded = WeakHashMap<ViewGroup, Boolean>()
    private val barStyles = WeakHashMap<ViewGroup, Style>()
    private val barColors = WeakHashMap<ViewGroup, Int>()
    private val glassDrawables = WeakHashMap<ViewGroup, GlassDrawable>()
    private val styledViews = WeakHashMap<View, Int>()
    private val scrollHooked = WeakHashMap<Activity, Boolean>()

    private fun readStyle(): Style = Style(
        radiusDp = xprefs.getInt("floating_bottom_bar_radius", CORNER_RADIUS_DP),
        bottomMarginDp = xprefs.getInt("fbb_bottom_margin", 16),
        sideMarginDp = xprefs.getInt("fbb_side_margin", 12),
        padVDp = xprefs.getInt("fbb_pad_v", 0),
        padHDp = xprefs.getInt("fbb_pad_h", 0),
        iconSizeDp = xprefs.getInt("fbb_icon_size", 0),
        ovalIndicator = xprefs.getString("fbb_indicator", "default") == "oval",
        indicatorColor = xprefs.getInt("fbb_indicator_color", 0),
        selectedColor = xprefs.getInt("fbb_icon_selected", 0),
        unselectedColor = xprefs.getInt("fbb_icon_unselected", 0),
        hideLabels = xprefs.getBoolean("fbb_hide_labels", false),
        textSizeSp = xprefs.getInt("fbb_text_size", 0),
        labelGapDp = xprefs.getInt("fbb_label_gap", 0),
        glass = xprefs.getBoolean("fbb_glass", false),
        glassAlpha = xprefs.getInt("fbb_glass_alpha", 35),
        shadow = xprefs.getBoolean("fbb_shadow", true),
        hideOnScroll = xprefs.getBoolean("fbb_hide_scroll", false),
        fade = xprefs.getString("fbb_hide_mode", "slide") == "fade",
    )

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
        }
    }

    private fun scheduleSetup(bar: ViewGroup) {
        if (processedBars.containsKey(bar)) {
            ensureBarOverlay(bar)
            glassDrawables[bar]?.attach()
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
                applyTransparentShadowStyle(container, bar, style)
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

            applyTransparentShadowStyle(container, bar, style)
            positionFabsAboveBar(rootView, container)
            installScrollHider(bar, container, style)

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

    private fun applyTransparentShadowStyle(container: ViewGroup, bar: ViewGroup, style: Style) {
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

        if (style.glass) {
            if (bar.background !is GlassDrawable) {
                val glass = GlassDrawable(
                    container, bar, radius, barColor or (0xFF shl 24),
                    style.glassAlpha, light, Utils.dipToPixels(1.2f).toFloat(),
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
        }
    }

    private fun callApi(target: Any, name: String, type: Class<*>, arg: Any): Boolean = try {
        target.javaClass.getMethod(name, type).invoke(target, arg)
        true
    } catch (_: Throwable) {
        false
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

        val autoSelected = if (light) Color.BLACK else Color.WHITE
        val autoUnselected = (autoSelected and 0x00FFFFFF) or (0x99 shl 24)
        val customColors = style.selectedColor != 0 || style.unselectedColor != 0
        val selected = if (style.selectedColor != 0) style.selectedColor else autoSelected
        val unselected = if (style.unselectedColor != 0) style.unselectedColor else autoUnselected
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
        if (style.indicatorColor != 0) {
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
                            if (Math.abs(v.textSize - px) > 1f) {
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

    private fun styleIndicator(view: View, style: Style, light: Boolean) {
        if (style.ovalIndicator) {
            val fill = if (style.indicatorColor != 0) {
                style.indicatorColor
            } else if (light) {
                Color.argb(46, 0, 0, 0)
            } else {
                Color.argb(56, 255, 255, 255)
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

    private fun installScrollHider(bar: ViewGroup, container: ViewGroup, style: Style) {
        if (!style.hideOnScroll) return
        val activity = findActivity(bar.context) ?: return
        if (scrollHooked.put(activity, true) != null) return
        val window = activity.window ?: return
        val original = window.callback ?: return
        val hider = ScrollHider(container, bar, style.fade)
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "dispatchTouchEvent" && args != null && args.isNotEmpty()) {
                (args[0] as? MotionEvent)?.let { hider.onTouch(it) }
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
    ) {
        private var lastY = 0f
        private var accumulated = 0f
        private var tracking = false
        private var hidden = false

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
            if (fade) {
                if (!value) container.visibility = View.VISIBLE
                container.animate().alpha(if (value) 0f else 1f).setDuration(180L)
                    .withEndAction { if (hidden) container.visibility = View.INVISIBLE }
                    .start()
            } else {
                val margin = (container.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                container.animate()
                    .translationY(if (value) (container.height + margin).toFloat() else 0f)
                    .setDuration(220L)
                    .start()
            }
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
    ) : Drawable() {

        private val rect = RectF()
        private val matrix = Matrix()
        private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.45f) })
        }
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = edgeWidth
        }
        private var work: Bitmap? = null
        private var shown: Bitmap? = null
        private var shader: BitmapShader? = null
        private var lastHash = 0
        private var lastRun = 0L
        private var pending = false
        private var observer: ViewTreeObserver? = null
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener { request() }

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
            if (now - lastRun >= 80L) {
                lastRun = now
                capture()
            } else if (!pending) {
                pending = true
                bar.postDelayed({
                    pending = false
                    lastRun = SystemClock.uptimeMillis()
                    capture()
                }, 80L)
            }
        }

        private fun capture() {
            try {
                val w = bar.width
                val h = bar.height
                if (w <= 0 || h <= 0 || !bar.isAttachedToWindow) return
                val root = container.parent as? ViewGroup ?: return
                val scale = 8
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
                    if (child === container || child.visibility != View.VISIBLE) continue
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
                while (k < pixels.size) {
                    hash = 31 * hash + pixels[k]
                    k += 5
                }
                if (hash == lastHash && shown != null) return
                lastHash = hash
                var a = pixels
                var b = IntArray(pixels.size)
                repeat(3) {
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

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            rect.set(bounds)
            sheenPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP,
            )
            edgePaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(
                    Color.argb(if (light) 120 else 150, 255, 255, 255),
                    Color.argb(if (light) 30 else 40, 255, 255, 255),
                ),
                null, Shader.TileMode.CLAMP,
            )
        }

        override fun draw(canvas: Canvas) {
            if (rect.isEmpty) rect.set(bounds)
            val bmp = shown
            val sh = shader
            if (bmp != null && sh != null) {
                matrix.reset()
                matrix.setScale(rect.width() / bmp.width, rect.height() / bmp.height)
                matrix.postTranslate(rect.left, rect.top)
                sh.setLocalMatrix(matrix)
                backdropPaint.shader = sh
                canvas.drawRoundRect(rect, radius, radius, backdropPaint)
            }
            tintPaint.color = (tint and 0x00FFFFFF) or ((opacityPercent.coerceIn(0, 100) * 255 / 100) shl 24)
            canvas.drawRoundRect(rect, radius, radius, tintPaint)
            canvas.drawRoundRect(rect, radius, radius, sheenPaint)
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
