package com.wmods.wppenhacer.xposed.features.customization

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

class ChatFloatingTools(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private enum class IconKind { PLUS, CLOSE, BOMBER }

    private data class ToolEntry(
        val id: String,
        val labelEn: String,
        val labelTr: String,
        val icon: IconKind,
        val action: (Activity) -> Unit
    )

    private data class Palette(
        val accent: Int,
        val accentRunning: Int,
        val surface: Int,
        val surfaceStroke: Int,
        val fieldBg: Int,
        val fieldStroke: Int,
        val tonalBg: Int,
        val tonalIcon: Int,
        val closeBg: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val scrim: Int
    )

    private class ActivityState(
        val handler: Handler = Handler(Looper.getMainLooper()),
        var bombing: Boolean = false,
        var expanded: Boolean = false,
        var collapsedX: Float = 0f,
        var collapsedY: Float = 0f,
        var positioned: Boolean = false,
        var morphAnimator: ValueAnimator? = null
    )

    private val states = WeakHashMap<Activity, ActivityState>()

    override fun getPluginName(): String = "ChatFloatingTools"

    override fun doHook() {
        if (!xprefs.getBoolean("chat_floating_tools", false)) return

        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName != "Conversation") return@addListenerActivity
            when (state) {
                WppCore.ActivityChangeState.ChangeType.RESUMED -> {
                    activity.runOnUiThread { attachBubble(activity) }
                }
                WppCore.ActivityChangeState.ChangeType.PAUSED -> {
                    activity.runOnUiThread {
                        stopBomber(activity)
                        collapseImmediate(activity)
                    }
                }
                WppCore.ActivityChangeState.ChangeType.DESTROYED -> {
                    activity.runOnUiThread { detachBubble(activity) }
                }
                else -> Unit
            }
        }
    }

    private fun isTurkish(activity: Activity): Boolean {
        val locales = activity.resources.configuration.locales
        val lang = if (!locales.isEmpty) locales[0]?.language else Locale.getDefault().language
        return lang.equals("tr", ignoreCase = true)
    }

    private fun isDark(activity: Activity): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun paletteFor(activity: Activity): Palette = if (isDark(activity)) {
        Palette(
            accent = 0xFF1DAA61.toInt(),
            accentRunning = 0xFFD9383A.toInt(),
            surface = 0xFF17211D.toInt(),
            surfaceStroke = 0xFF2B3E36.toInt(),
            fieldBg = 0xFF22302A.toInt(),
            fieldStroke = 0xFF344C42.toInt(),
            tonalBg = 0xFF1F3D2F.toInt(),
            tonalIcon = 0xFF6EF0A8.toInt(),
            closeBg = 0xFF25332D.toInt(),
            textPrimary = 0xFFEDF6F1.toInt(),
            textSecondary = 0xFF9BB4A8.toInt(),
            scrim = 0x55000000
        )
    } else {
        Palette(
            accent = 0xFF179C56.toInt(),
            accentRunning = 0xFFD32F2F.toInt(),
            surface = 0xFFF3FAF6.toInt(),
            surfaceStroke = 0xFFD0E5D9.toInt(),
            fieldBg = 0xFFE3F1E9.toInt(),
            fieldStroke = 0xFFBCD9C8.toInt(),
            tonalBg = 0xFFCDEFD9.toInt(),
            tonalIcon = 0xFF0E683B.toInt(),
            closeBg = 0xFFE1EEE6.toInt(),
            textPrimary = 0xFF13221B.toInt(),
            textSecondary = 0xFF4F6B5D.toInt(),
            scrim = 0x38000000
        )
    }

    private fun buildTools(): List<ToolEntry> = listOf(
        ToolEntry(
            id = "bomber",
            labelEn = "Bomber",
            labelTr = "Bomber",
            icon = IconKind.BOMBER
        ) { showBomberDialog(it) }
    )

    private fun dp(activity: Activity, value: Float): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun dpf(activity: Activity, value: Float): Float =
        value * activity.resources.displayMetrics.density

    private fun stateOf(activity: Activity): ActivityState =
        states.getOrPut(activity) { ActivityState() }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubble(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (root.findViewWithTag<View>(BUBBLE_TAG) != null) return

        val state = stateOf(activity)
        val palette = paletteFor(activity)
        val size = dp(activity, 50f)
        val slop = dpf(activity, 9f)

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = size / 2f
            setColor(palette.accent)
        }

        val iconView = ImageView(activity).apply {
            tag = ICON_TAG
            setImageDrawable(VectorIconDrawable(IconKind.PLUS, Color.WHITE, dpf(activity, 2.4f)))
            val pad = dp(activity, 13f)
            setPadding(pad, pad, pad, pad)
        }

        val countView = TextView(activity).apply {
            tag = COUNT_TAG
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        val bubble = FrameLayout(activity).apply {
            tag = BUBBLE_TAG
            background = bg
            elevation = dpf(activity, 8f)
            clipToOutline = true
            clipChildren = true
            isClickable = true
            addView(
                iconView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                countView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        bubble.setOnTouchListener { v, event ->
            if (state.expanded) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90L).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110L).start()
                    }
                    if (dragging) {
                        val edge = dpf(activity, 6f)
                        val maxX = (root.width - v.width - edge).coerceAtLeast(edge)
                        val maxY = (root.height - v.height - dpf(activity, 64f)).coerceAtLeast(dpf(activity, 64f))
                        v.x = (startX + dx).coerceIn(edge, maxX)
                        v.y = (startY + dy).coerceIn(dpf(activity, 64f), maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                    if (!dragging) {
                        v.performClick()
                        onBubbleClick(activity)
                    } else {
                        snapToEdge(activity, root, v, state)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                    true
                }
                else -> false
            }
        }

        val params = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        root.addView(bubble, params)

        val placeBubble = {
            val edge = dpf(activity, 12f)
            val w = root.width
            val h = root.height
            if (w > 0 && h > 0) {
                if (!state.positioned) {
                    state.collapsedX = (w - size - edge).coerceAtLeast(edge)
                    state.collapsedY = ((h - size) * 0.46f).coerceIn(dpf(activity, 72f), (h - size - dpf(activity, 88f)).coerceAtLeast(dpf(activity, 72f)))
                    state.positioned = true
                }
                bubble.x = state.collapsedX.coerceIn(edge, (w - size - edge).coerceAtLeast(edge))
                bubble.y = state.collapsedY.coerceIn(dpf(activity, 64f), (h - size - dpf(activity, 72f)).coerceAtLeast(dpf(activity, 64f)))
            }
        }

        if (root.width > 0 && root.height > 0) {
            placeBubble()
        } else {
            root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (root.width > 0 && root.height > 0) {
                        root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        placeBubble()
                    }
                }
            })
        }
    }

    private fun snapToEdge(activity: Activity, root: ViewGroup, bubble: View, state: ActivityState) {
        val edge = dpf(activity, 12f)
        val centerX = bubble.x + bubble.width / 2f
        val targetX = if (centerX < root.width / 2f) {
            edge
        } else {
            (root.width - bubble.width - edge).coerceAtLeast(edge)
        }
        val minY = dpf(activity, 64f)
        val maxY = (root.height - bubble.height - dpf(activity, 76f)).coerceAtLeast(minY)
        val targetY = bubble.y.coerceIn(minY, maxY)
        state.collapsedX = targetX
        state.collapsedY = targetY
        bubble.animate()
            .x(targetX)
            .y(targetY)
            .setDuration(260L)
            .setInterpolator(OvershootInterpolator(0.85f))
            .start()
    }

    private fun onBubbleClick(activity: Activity) {
        val state = stateOf(activity)
        if (state.bombing) {
            stopBomber(activity)
            Toast.makeText(
                activity,
                if (isTurkish(activity)) "Durduruldu" else "Stopped",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (state.expanded) {
            collapsePanel(activity)
        } else {
            expandPanel(activity)
        }
    }

    private fun expandPanel(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bubble = root.findViewWithTag<FrameLayout>(BUBBLE_TAG) ?: return
        val iconView = bubble.findViewWithTag<View>(ICON_TAG) ?: return
        val bg = bubble.background as? GradientDrawable ?: return
        val state = stateOf(activity)
        if (state.expanded) return

        state.morphAnimator?.cancel()
        state.expanded = true
        state.collapsedX = bubble.x
        state.collapsedY = bubble.y

        val palette = paletteFor(activity)
        val tools = buildTools()
        val cols = if (tools.size <= 1) 1 else if (tools.size <= 4) 2 else 3
        val rows = ceil(tools.size / cols.toDouble()).toInt().coerceAtLeast(1)

        val expandedW = when (cols) {
            1 -> dp(activity, 148f)
            2 -> dp(activity, 206f)
            else -> dp(activity, 272f)
        }
        val expandedH = dp(activity, 52f + rows * 94f)

        val edge = dpf(activity, 14f)
        val minY = dpf(activity, 68f)
        val maxX = (root.width - expandedW - edge).coerceAtLeast(edge)
        val maxY = (root.height - expandedH - dpf(activity, 76f)).coerceAtLeast(minY)

        val bubbleCenterX = state.collapsedX + bubble.width / 2f
        val preferRight = bubbleCenterX >= root.width / 2f
        val targetX = (if (preferRight) {
            state.collapsedX + bubble.width - expandedW
        } else {
            state.collapsedX
        }).coerceIn(edge, maxX)
        val targetY = (state.collapsedY + bubble.height / 2f - expandedH / 2f).coerceIn(minY, maxY)

        root.findViewWithTag<View>(SCRIM_TAG)?.let { root.removeView(it) }
        val scrim = View(activity).apply {
            tag = SCRIM_TAG
            setBackgroundColor(palette.scrim)
            alpha = 0f
            setOnClickListener { collapsePanel(activity) }
        }
        val bubbleIdx = root.indexOfChild(bubble).coerceAtLeast(0)
        root.addView(
            scrim,
            bubbleIdx,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        scrim.animate().alpha(1f).setDuration(220L).start()

        bubble.findViewWithTag<View>(PANEL_TAG)?.let { bubble.removeView(it) }
        val panelContent = buildPanelContent(activity, tools, cols, palette).apply {
            tag = PANEL_TAG
            alpha = 0f
            scaleX = 0.84f
            scaleY = 0.84f
        }
        bubble.addView(
            panelContent,
            FrameLayout.LayoutParams(expandedW, expandedH, Gravity.CENTER)
        )

        val startW = bubble.width.coerceAtLeast(dp(activity, 50f))
        val startH = bubble.height.coerceAtLeast(dp(activity, 50f))
        val startX = state.collapsedX
        val startY = state.collapsedY
        val startRadius = startW / 2f
        val endRadius = dpf(activity, 28f)
        val strokePx = dp(activity, 1f).coerceAtLeast(1)
        val argb = ArgbEvaluator()
        val lp = bubble.layoutParams

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { va ->
                val f = va.animatedValue as Float
                val c = f.coerceIn(0f, 1f)
                lp.width = (startW + (expandedW - startW) * f).toInt().coerceAtLeast(startW)
                lp.height = (startH + (expandedH - startH) * f).toInt().coerceAtLeast(startH)
                bubble.x = startX + (targetX - startX) * f
                bubble.y = startY + (targetY - startY) * f
                bg.cornerRadius = (startRadius + (endRadius - startRadius) * c).coerceAtLeast(0f)
                bg.setColor(argb.evaluate(c, palette.accent, palette.surface) as Int)
                bg.setStroke(strokePx, argb.evaluate(c, Color.TRANSPARENT, palette.surfaceStroke) as Int)
                iconView.alpha = (1f - c * 2.6f).coerceIn(0f, 1f)
                iconView.rotation = c * 45f
                val contentT = ((c - 0.22f) / 0.78f).coerceIn(0f, 1f)
                panelContent.alpha = contentT
                panelContent.scaleX = 0.84f + 0.16f * contentT
                panelContent.scaleY = 0.84f + 0.16f * contentT
                bubble.requestLayout()
                bubble.invalidateOutline()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    iconView.visibility = View.GONE
                    panelContent.alpha = 1f
                    panelContent.scaleX = 1f
                    panelContent.scaleY = 1f
                }
            })
        }
        state.morphAnimator = animator
        animator.start()
    }

    private fun collapsePanel(activity: Activity, onCollapsed: (() -> Unit)? = null) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bubble = root.findViewWithTag<FrameLayout>(BUBBLE_TAG) ?: return
        val iconView = bubble.findViewWithTag<View>(ICON_TAG) ?: return
        val bg = bubble.background as? GradientDrawable ?: return
        val state = stateOf(activity)
        if (!state.expanded) {
            onCollapsed?.invoke()
            return
        }

        state.morphAnimator?.cancel()
        state.expanded = false

        val palette = paletteFor(activity)
        val panelContent = bubble.findViewWithTag<View>(PANEL_TAG)
        val scrim = root.findViewWithTag<View>(SCRIM_TAG)

        scrim?.animate()?.alpha(0f)?.setDuration(180L)?.withEndAction {
            root.removeView(scrim)
        }?.start()

        val endSize = dp(activity, 50f)
        val startW = bubble.width
        val startH = bubble.height
        val startX = bubble.x
        val startY = bubble.y
        val targetX = state.collapsedX
        val targetY = state.collapsedY
        val startRadius = dpf(activity, 28f)
        val endRadius = endSize / 2f
        val strokePx = dp(activity, 1f).coerceAtLeast(1)
        val targetColor = if (state.bombing) palette.accentRunning else palette.accent
        val argb = ArgbEvaluator()
        val lp = bubble.layoutParams

        iconView.visibility = if (state.bombing) View.GONE else View.VISIBLE

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 230L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val c = (va.animatedValue as Float).coerceIn(0f, 1f)
                lp.width = (startW + (endSize - startW) * c).toInt().coerceAtLeast(endSize)
                lp.height = (startH + (endSize - startH) * c).toInt().coerceAtLeast(endSize)
                bubble.x = startX + (targetX - startX) * c
                bubble.y = startY + (targetY - startY) * c
                bg.cornerRadius = startRadius + (endRadius - startRadius) * c
                bg.setColor(argb.evaluate(c, palette.surface, targetColor) as Int)
                bg.setStroke(strokePx, argb.evaluate(c, palette.surfaceStroke, Color.TRANSPARENT) as Int)
                panelContent?.let {
                    it.alpha = (1f - c * 2.5f).coerceIn(0f, 1f)
                    val s = 1f - 0.14f * c
                    it.scaleX = s
                    it.scaleY = s
                }
                if (!state.bombing) {
                    iconView.alpha = ((c - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    iconView.rotation = (1f - c) * 45f
                }
                bubble.requestLayout()
                bubble.invalidateOutline()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    panelContent?.let { bubble.removeView(it) }
                    bg.setStroke(0, Color.TRANSPARENT)
                    bg.cornerRadius = endRadius
                    lp.width = endSize
                    lp.height = endSize
                    bubble.x = targetX
                    bubble.y = targetY
                    iconView.alpha = 1f
                    iconView.rotation = 0f
                    bubble.requestLayout()
                    bubble.invalidateOutline()
                    onCollapsed?.invoke()
                }
            })
        }
        state.morphAnimator = animator
        animator.start()
    }

    private fun collapseImmediate(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val state = states[activity] ?: return
        state.morphAnimator?.cancel()
        root.findViewWithTag<View>(SCRIM_TAG)?.let { root.removeView(it) }
        val bubble = root.findViewWithTag<FrameLayout>(BUBBLE_TAG) ?: return
        bubble.findViewWithTag<View>(PANEL_TAG)?.let { bubble.removeView(it) }
        if (!state.expanded) return
        state.expanded = false
        val size = dp(activity, 50f)
        val palette = paletteFor(activity)
        (bubble.background as? GradientDrawable)?.apply {
            cornerRadius = size / 2f
            setColor(palette.accent)
            setStroke(0, Color.TRANSPARENT)
        }
        bubble.layoutParams.width = size
        bubble.layoutParams.height = size
        bubble.x = state.collapsedX
        bubble.y = state.collapsedY
        bubble.findViewWithTag<View>(ICON_TAG)?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            rotation = 0f
        }
        bubble.requestLayout()
        bubble.invalidateOutline()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanelContent(
        activity: Activity,
        tools: List<ToolEntry>,
        cols: Int,
        palette: Palette
    ): LinearLayout {
        val tr = isTurkish(activity)
        val padH = dp(activity, 14f)
        val padV = dp(activity, 12f)

        val rootCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(activity).apply {
            text = if (tr) "Araçlar" else "Tools"
            setTextColor(palette.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeSize = dp(activity, 28f)
        val closeBtn = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.closeBg)
            }
            addView(
                ImageView(activity).apply {
                    setImageDrawable(VectorIconDrawable(IconKind.CLOSE, palette.textSecondary, dpf(activity, 1.9f)))
                    val p = dp(activity, 7f)
                    setPadding(p, p, p, p)
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setOnClickListener { collapsePanel(activity) }
        }
        header.addView(title)
        header.addView(closeBtn, LinearLayout.LayoutParams(closeSize, closeSize))
        rootCol.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 10f)
            }
        )

        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val rows = tools.chunked(cols)
        rows.forEachIndexed { rowIdx, rowItems ->
            val rowLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowItems.forEach { item ->
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(activity, 6f), dp(activity, 4f), dp(activity, 6f), dp(activity, 4f))
                }

                val circleSize = dp(activity, 56f)
                val circle = FrameLayout(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(palette.tonalBg)
                    }
                    addView(
                        ImageView(activity).apply {
                            setImageDrawable(VectorIconDrawable(item.icon, palette.tonalIcon, dpf(activity, 2.2f)))
                            val p = dp(activity, 15f)
                            setPadding(p, p, p, p)
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    setOnTouchListener { v, ev ->
                        when (ev.actionMasked) {
                            MotionEvent.ACTION_DOWN ->
                                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90L).start()
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.animate().scaleX(1f).scaleY(1f).setDuration(150L)
                                    .setInterpolator(OvershootInterpolator(1.2f)).start()
                        }
                        false
                    }
                    setOnClickListener {
                        collapsePanel(activity) {
                            item.action(activity)
                        }
                    }
                }

                val label = TextView(activity).apply {
                    text = if (tr) item.labelTr else item.labelEn
                    setTextColor(palette.textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }

                cell.addView(circle, LinearLayout.LayoutParams(circleSize, circleSize))
                cell.addView(
                    label,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(activity, 6f)
                    }
                )
                rowLayout.addView(
                    cell,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            grid.addView(
                rowLayout,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIdx > 0) topMargin = dp(activity, 6f)
                }
            )
        }

        rootCol.addView(grid)
        return rootCol
    }

    private fun makeFieldLabel(activity: Activity, text: String, palette: Palette): TextView =
        TextView(activity).apply {
            this.text = text
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(activity, 4f), 0, 0, dp(activity, 5f))
        }

    private fun makeExpressiveInput(
        activity: Activity,
        hintText: String,
        palette: Palette,
        numeric: Boolean = false,
        initial: String = ""
    ): EditText = EditText(activity).apply {
        hint = hintText
        if (initial.isNotEmpty()) setText(initial)
        setTextColor(palette.textPrimary)
        setHintTextColor(palette.textSecondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(activity, 16f)
            setColor(palette.fieldBg)
            setStroke(dp(activity, 1f).coerceAtLeast(1), palette.fieldStroke)
        }
        val ph = dp(activity, 14f)
        val pv = dp(activity, 11f)
        setPadding(ph, pv, ph, pv)
    }

    private fun showBomberDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val tr = isTurkish(activity)
        val palette = paletteFor(activity)

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(activity, 28f)
                setColor(palette.surface)
                setStroke(dp(activity, 1f).coerceAtLeast(1), palette.surfaceStroke)
            }
            val p = dp(activity, 20f)
            setPadding(p, p, p, dp(activity, 16f))
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeSize = dp(activity, 40f)
        val badge = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.tonalBg)
            }
            addView(
                ImageView(activity).apply {
                    setImageDrawable(VectorIconDrawable(IconKind.BOMBER, palette.tonalIcon, dpf(activity, 2f)))
                    val p = dp(activity, 10f)
                    setPadding(p, p, p, p)
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val titleView = TextView(activity).apply {
            text = "Message Bomber"
            setTextColor(palette.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(activity, 12f), 0, 0, 0)
        }

        headerRow.addView(badge, LinearLayout.LayoutParams(badgeSize, badgeSize))
        headerRow.addView(titleView)
        card.addView(
            headerRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 16f)
            }
        )

        val messageInput = makeExpressiveInput(
            activity,
            if (tr) "Gönderilecek metin" else "Text to send",
            palette
        )
        card.addView(makeFieldLabel(activity, if (tr) "Mesaj" else "Message", palette))
        card.addView(
            messageInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 12f)
            }
        )

        val numbersRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val countCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val countInput = makeExpressiveInput(activity, "5", palette, numeric = true)
        countCol.addView(makeFieldLabel(activity, if (tr) "Adet" else "Count", palette))
        countCol.addView(countInput)

        val delayCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val delayInput = makeExpressiveInput(activity, "250", palette, numeric = true, initial = "250")
        delayCol.addView(makeFieldLabel(activity, if (tr) "Gecikme (ms)" else "Delay (ms)", palette))
        delayCol.addView(delayInput)

        numbersRow.addView(
            countCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 6f)
            }
        )
        numbersRow.addView(
            delayCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 6f)
            }
        )
        card.addView(
            numbersRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 18f)
            }
        )

        val buttonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = TextView(activity).apply {
            text = if (tr) "İptal" else "Cancel"
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(activity, 22f)
                setColor(palette.closeBg)
            }
            setPadding(dp(activity, 18f), dp(activity, 10f), dp(activity, 18f), dp(activity, 10f))
            setOnClickListener { dialog.dismiss() }
        }

        val startBtn = TextView(activity).apply {
            text = if (tr) "Başlat" else "Start"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(activity, 22f)
                setColor(palette.accent)
            }
            setPadding(dp(activity, 22f), dp(activity, 10f), dp(activity, 22f), dp(activity, 10f))
            setOnClickListener {
                val text = messageInput.text?.toString()?.trim().orEmpty()
                val count = countInput.text?.toString()?.toIntOrNull() ?: 0
                val delay = (delayInput.text?.toString()?.toLongOrNull() ?: 250L).coerceAtLeast(80L)
                if (text.isEmpty() || count <= 0) {
                    Toast.makeText(
                        activity,
                        if (tr) "Geçerli mesaj ve adet girin" else "Enter valid message and count",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                startBomber(activity, text, count, delay)
            }
        }

        buttonsRow.addView(
            cancelBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(activity, 8f)
            }
        )
        buttonsRow.addView(startBtn)
        card.addView(buttonsRow)

        dialog.setContentView(card)
        val screenW = activity.resources.displayMetrics.widthPixels
        val dialogW = min(dp(activity, 316f), screenW - dp(activity, 36f))
        dialog.window?.setLayout(dialogW, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()

        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(OvershootInterpolator(0.95f))
            .start()
    }

    private fun startBomber(activity: Activity, text: String, total: Int, delayMs: Long) {
        val entryId = activity.resources.getIdentifier("entry", "id", activity.packageName)
        val sendId = activity.resources.getIdentifier("send", "id", activity.packageName)
        if (entryId == 0 || sendId == 0 || activity.findViewById<EditText>(entryId) == null) {
            Toast.makeText(
                activity,
                if (isTurkish(activity)) "Mesaj kutusu bulunamadı" else "Message input not found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val state = stateOf(activity)
        state.handler.removeCallbacksAndMessages(null)
        state.bombing = true
        updateBubbleRunning(activity, true, total)
        runBomberStep(activity, state, entryId, sendId, text, total, delayMs)
    }

    private fun runBomberStep(
        activity: Activity,
        state: ActivityState,
        entryId: Int,
        sendId: Int,
        text: String,
        remaining: Int,
        delayMs: Long
    ) {
        if (!state.bombing || remaining <= 0 || activity.isFinishing || activity.isDestroyed) {
            stopBomber(activity)
            return
        }
        val input = activity.findViewById<EditText>(entryId)
        if (input == null) {
            stopBomber(activity)
            return
        }
        updateBubbleRunning(activity, true, remaining)
        input.setText(text)
        input.setSelection(text.length)
        state.handler.postDelayed({
            if (!state.bombing || activity.isFinishing || activity.isDestroyed) return@postDelayed
            val sendBtn = activity.findViewById<View>(sendId)
            if (sendBtn != null && sendBtn.visibility == View.VISIBLE) {
                sendBtn.performClick()
                pulseBubble(activity)
            }
            val next = remaining - 1
            if (next > 0 && state.bombing) {
                state.handler.postDelayed({
                    runBomberStep(activity, state, entryId, sendId, text, next, delayMs)
                }, delayMs)
            } else {
                stopBomber(activity)
            }
        }, 60L)
    }

    private fun pulseBubble(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bubble = root.findViewWithTag<View>(BUBBLE_TAG) ?: return
        bubble.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(75L)
            .withEndAction {
                bubble.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            .start()
    }

    private fun stopBomber(activity: Activity) {
        states[activity]?.let {
            it.bombing = false
            it.handler.removeCallbacksAndMessages(null)
        }
        updateBubbleRunning(activity, false, 0)
    }

    private fun detachBubble(activity: Activity) {
        stopBomber(activity)
        states[activity]?.morphAnimator?.cancel()
        states.remove(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(SCRIM_TAG)?.let { root.removeView(it) }
        root.findViewWithTag<View>(BUBBLE_TAG)?.let { root.removeView(it) }
    }

    private fun updateBubbleRunning(activity: Activity, running: Boolean, remaining: Int) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bubble = root.findViewWithTag<FrameLayout>(BUBBLE_TAG) ?: return
        val palette = paletteFor(activity)
        val state = states[activity]
        if (state?.expanded != true) {
            (bubble.background as? GradientDrawable)?.setColor(
                if (running) palette.accentRunning else palette.accent
            )
            bubble.findViewWithTag<View>(ICON_TAG)?.visibility =
                if (running) View.GONE else View.VISIBLE
            bubble.findViewWithTag<TextView>(COUNT_TAG)?.apply {
                visibility = if (running) View.VISIBLE else View.GONE
                if (running) text = remaining.toString()
            }
        }
    }

    private class VectorIconDrawable(
        private val kind: IconKind,
        color: Int,
        strokeWidth: Float
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()

            when (kind) {
                IconKind.PLUS -> {
                    val r = min(w, h) * 0.34f
                    canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
                    canvas.drawLine(cx, cy - r, cx, cy + r, strokePaint)
                }
                IconKind.CLOSE -> {
                    val r = min(w, h) * 0.30f
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, strokePaint)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, strokePaint)
                }
                IconKind.BOMBER -> {
                    val l = b.left.toFloat()
                    val t = b.top.toFloat()
                    val path = Path().apply {
                        moveTo(l + w * 0.88f, t + h * 0.14f)
                        lineTo(l + w * 0.14f, t + h * 0.44f)
                        lineTo(l + w * 0.45f, t + h * 0.56f)
                        lineTo(l + w * 0.57f, t + h * 0.86f)
                        close()
                    }
                    canvas.drawPath(path, strokePaint)
                    canvas.drawLine(
                        l + w * 0.45f,
                        t + h * 0.56f,
                        l + w * 0.86f,
                        t + h * 0.16f,
                        strokePaint
                    )
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val BUBBLE_TAG = "wp_chat_floating_tools"
        private const val SCRIM_TAG = "wp_chat_floating_scrim"
        private const val PANEL_TAG = "wp_chat_floating_panel"
        private const val ICON_TAG = "wp_chat_floating_icon"
        private const val COUNT_TAG = "wp_chat_floating_count"
    }
}
