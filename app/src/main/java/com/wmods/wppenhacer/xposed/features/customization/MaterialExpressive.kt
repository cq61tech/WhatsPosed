package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.core.WppCore
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class MaterialExpressive(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private data class ExpressivePalette(
        val canvas: Int,
        val card: Int,
        val cardHigh: Int,
        val primary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val outline: Int,
        val ripple: Int
    )

    private data class Config(
        val colorSource: String,
        val intensity: Float,
        val cardStyle: String,
        val cornerRadiusDp: Int,
        val cardGapDp: Int,
        val cardOutline: Boolean,
        val hideDividers: Boolean,
        val chatsCards: Boolean,
        val settingsCards: Boolean,
        val searchPill: Boolean,
        val chatComposer: Boolean,
        val expressiveFab: Boolean,
        val styleDialogs: Boolean,
        val customPrimary: Int,
        val customBg: Int,
        val floatingBarActive: Boolean
    )

    private val idNameCache = ConcurrentHashMap<Int, String>()
    private val paddedViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val styledViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val pendingContainers = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())

    override fun getPluginName(): String = "MaterialExpressive"

    override fun doHook() {
        if (!xprefs.getBoolean("me_enabled", false)) return

        val config = loadConfig()

        WppCore.addListenerActivity { activity, state ->
            if (state != WppCore.ActivityChangeState.ChangeType.RESUMED &&
                state != WppCore.ActivityChangeState.ChangeType.STARTED
            ) return@addListenerActivity
            if (isExcludedActivity(activity)) return@addListenerActivity
            activity.runOnUiThread {
                runCatching { styleActivityWindow(activity, config) }
            }
        }

        ViewHookDispatcher.onAttachedToWindow(this) { view ->
            runCatching { onViewAttached(view, config) }
        }
    }

    private fun loadConfig(): Config = Config(
        colorSource = xprefs.getString("me_color_source", "monet") ?: "monet",
        intensity = xprefs.getInt("me_monet_intensity", 70).coerceIn(0, 100) / 100f,
        cardStyle = xprefs.getString("me_card_style", "segmented") ?: "segmented",
        cornerRadiusDp = xprefs.getInt("me_corner_radius", 22).coerceIn(10, 30),
        cardGapDp = xprefs.getInt("me_card_gap", 3).coerceIn(0, 8),
        cardOutline = xprefs.getBoolean("me_card_outline", true),
        hideDividers = xprefs.getBoolean("me_hide_dividers", true),
        chatsCards = xprefs.getBoolean("me_chats_cards", true),
        settingsCards = xprefs.getBoolean("me_settings_cards", true),
        searchPill = xprefs.getBoolean("me_search_pill", true),
        chatComposer = xprefs.getBoolean("me_chat_composer", true),
        expressiveFab = xprefs.getBoolean("me_expressive_fab", true),
        styleDialogs = xprefs.getBoolean("me_dialogs", true),
        customPrimary = xprefs.getInt("primary_color", 0),
        customBg = xprefs.getInt("background_color", 0),
        floatingBarActive = xprefs.getBoolean("floating_bottom_bar", false) ||
            xprefs.getBoolean("native_floating_navigation", false)
    )

    private fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun resolveSystemColor(context: Context, name: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
        val id = context.resources.getIdentifier(name, "color", "android")
        if (id == 0) return 0
        return runCatching { ContextCompat.getColor(context, id) }.getOrDefault(0)
    }

    private fun resolvePalette(context: Context, config: Config): ExpressivePalette {
        val dark = isDark(context)
        val t = if (config.colorSource == "neutral") 0f else config.intensity

        val baseCanvas = if (dark) 0xFF0C1013.toInt() else 0xFFF4F6F8.toInt()
        val baseCard = if (dark) 0xFF171C20.toInt() else 0xFFFFFFFF.toInt()
        val baseHigh = if (dark) 0xFF21282D.toInt() else 0xFFE9EEF2.toInt()
        val baseAccent = if (dark) 0xFF25D366.toInt() else 0xFF128C4A.toInt()
        val baseContainer = if (dark) 0xFF1A3A2B.toInt() else 0xFFCDEFD9.toInt()
        val baseOnContainer = if (dark) 0xFFD9FBE6.toInt() else 0xFF0A4D2A.toInt()
        val baseOutline = if (dark) 0xFF2F383E.toInt() else 0xFFD6DEE4.toInt()

        var targetCanvas = baseCanvas
        var targetCard = baseCard
        var targetHigh = baseHigh
        var targetPrimary = baseAccent
        var targetContainer = baseContainer
        var targetOnContainer = baseOnContainer
        var targetOutline = baseOutline

        if (config.colorSource == "monet" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val a200 = resolveSystemColor(context, "system_accent1_200")
            val a300 = resolveSystemColor(context, "system_accent1_300")
            val a600 = resolveSystemColor(context, "system_accent1_600")
            val a700 = resolveSystemColor(context, "system_accent1_700")
            val a800 = resolveSystemColor(context, "system_accent1_800")
            val a100 = resolveSystemColor(context, "system_accent1_100")
            val a50 = resolveSystemColor(context, "system_accent1_50")
            val n900 = resolveSystemColor(context, "system_neutral1_900")
            val n800 = resolveSystemColor(context, "system_neutral1_800")
            val n700 = resolveSystemColor(context, "system_neutral1_700")
            val n10 = resolveSystemColor(context, "system_neutral1_10")
            val n50 = resolveSystemColor(context, "system_neutral1_50")
            val n100 = resolveSystemColor(context, "system_neutral1_100")
            val n200 = resolveSystemColor(context, "system_neutral1_200")

            if (dark) {
                if (n900 != 0 && a800 != 0) {
                    targetCanvas = ColorUtils.blendARGB(0xFF080B0E.toInt(), n900, 0.72f)
                    targetCanvas = ColorUtils.blendARGB(targetCanvas, a800, 0.14f)
                    targetCard = ColorUtils.blendARGB(n900, n800.takeIf { it != 0 } ?: n900, 0.45f)
                    targetCard = ColorUtils.blendARGB(targetCard, a800, 0.16f)
                    targetHigh = ColorUtils.blendARGB(n800.takeIf { it != 0 } ?: targetCard, a700.takeIf { it != 0 } ?: a800, 0.22f)
                    targetOutline = ColorUtils.blendARGB(n700.takeIf { it != 0 } ?: targetHigh, a300.takeIf { it != 0 } ?: a200, 0.28f)
                }
                if (a200 != 0) targetPrimary = a200
                if (a700 != 0) targetContainer = a700
                if (a100 != 0) targetOnContainer = a100
            } else {
                if (n10 != 0 && a50 != 0) {
                    targetCanvas = ColorUtils.blendARGB(n10, a50, 0.35f)
                    targetCard = ColorUtils.blendARGB(Color.WHITE, a50, 0.18f)
                    targetHigh = ColorUtils.blendARGB(n50.takeIf { it != 0 } ?: n100, a100.takeIf { it != 0 } ?: a50, 0.30f)
                    targetOutline = ColorUtils.blendARGB(n200.takeIf { it != 0 } ?: baseOutline, a600.takeIf { it != 0 } ?: baseAccent, 0.18f)
                }
                if (a600 != 0) targetPrimary = a600
                if (a100 != 0) targetContainer = a100
                if (a800 != 0) targetOnContainer = a800
            }
        } else if (config.colorSource == "custom") {
            val p = config.customPrimary
            val b = config.customBg
            if (p != 0) {
                targetPrimary = p
                targetContainer = if (dark) ColorUtils.blendARGB(0xFF14181C.toInt(), p, 0.36f) else ColorUtils.blendARGB(Color.WHITE, p, 0.22f)
                targetOnContainer = if (dark) ColorUtils.blendARGB(Color.WHITE, p, 0.15f) else ColorUtils.blendARGB(Color.BLACK, p, 0.35f)
                targetCard = ColorUtils.blendARGB(baseCard, p, if (dark) 0.12f else 0.06f)
                targetHigh = ColorUtils.blendARGB(baseHigh, p, if (dark) 0.18f else 0.11f)
                targetOutline = ColorUtils.blendARGB(baseOutline, p, 0.28f)
            }
            if (b != 0) {
                targetCanvas = b
            } else if (p != 0) {
                targetCanvas = ColorUtils.blendARGB(baseCanvas, p, if (dark) 0.08f else 0.04f)
            }
        }

        val canvas = ColorUtils.blendARGB(baseCanvas, targetCanvas, t)
        val card = ColorUtils.blendARGB(baseCard, targetCard, t)
        val cardHigh = ColorUtils.blendARGB(baseHigh, targetHigh, t)
        val primary = ColorUtils.blendARGB(baseAccent, targetPrimary, t)
        val primaryContainer = ColorUtils.blendARGB(baseContainer, targetContainer, t)
        val onPrimaryContainer = ColorUtils.blendARGB(baseOnContainer, targetOnContainer, t)
        val outlineRgb = ColorUtils.blendARGB(baseOutline, targetOutline, t)
        val outline = ColorUtils.setAlphaComponent(outlineRgb, if (dark) 62 else 82)
        val ripple = ColorUtils.setAlphaComponent(primary, if (dark) 44 else 32)

        return ExpressivePalette(
            canvas = canvas,
            card = card,
            cardHigh = cardHigh,
            primary = primary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            outline = outline,
            ripple = ripple
        )
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun dpf(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    private fun activityOf(context: Context?): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun isExcludedActivity(activity: Activity?): Boolean {
        val name = activity?.javaClass?.simpleName ?: return false
        return EXCLUDED_ACTIVITIES.any { name.contains(it, ignoreCase = true) }
    }

    private fun isConversationActivity(activity: Activity?): Boolean {
        val name = activity?.javaClass?.simpleName ?: return false
        return name == "Conversation" || name.endsWith("Conversation")
    }

    private fun isSettingsLikeActivity(activity: Activity?): Boolean {
        val name = activity?.javaClass?.simpleName ?: return false
        return SETTINGS_ACTIVITIES.any { name.contains(it, ignoreCase = true) }
    }

    private fun getEntryName(view: View): String {
        val id = view.id
        if (id == View.NO_ID || id <= 0) return ""
        return idNameCache.computeIfAbsent(id) {
            runCatching { view.resources.getResourceEntryName(it) }.getOrDefault("")
        }
    }

    private fun isRecyclerOrList(view: View?): Boolean {
        if (view == null) return false
        if (view is ListView) return true
        var cls: Class<*>? = view.javaClass
        var depth = 0
        while (cls != null && depth < 5) {
            if (cls.name.contains("RecyclerView", ignoreCase = true)) return true
            cls = cls.superclass
            depth++
        }
        return false
    }

    private fun isFabView(view: View, entryName: String): Boolean {
        if (view.tag?.toString()?.startsWith("wp_chat_floating") == true) return false
        if (entryName == "fab" || entryName == "fab_second" || entryName == "fab_aux" ||
            entryName == "floating_action_button"
        ) return true
        var cls: Class<*>? = view.javaClass
        var depth = 0
        while (cls != null && depth < 4) {
            if (cls.simpleName.contains("FloatingActionButton", ignoreCase = true)) return true
            cls = cls.superclass
            depth++
        }
        return false
    }

    private fun styleActivityWindow(activity: Activity, config: Config) {
        if (activity.isFinishing || activity.isDestroyed) return
        val palette = resolvePalette(activity, config)
        val isConv = isConversationActivity(activity)

        if (!isConv) {
            activity.window?.let { w ->
                w.setBackgroundDrawable(ColorDrawable(palette.canvas))
                runCatching {
                    w.statusBarColor = palette.canvas
                    if (!config.floatingBarActive) {
                        w.navigationBarColor = palette.canvas
                    }
                }
            }
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            walkAndStyle(content, activity, config, palette, 0)
        }
    }

    private fun walkAndStyle(
        view: View,
        activity: Activity,
        config: Config,
        palette: ExpressivePalette,
        depth: Int
    ) {
        if (depth > 14) return
        if (view.tag?.toString()?.startsWith("wp_chat_floating") == true) return
        val entry = getEntryName(view)
        if (entry == "bottom_nav" && config.floatingBarActive) return

        val isConv = isConversationActivity(activity)

        if (!isConv && view is ListView) {
            if (config.hideDividers) {
                view.divider = ColorDrawable(Color.TRANSPARENT)
                view.dividerHeight = 0
            }
            if (config.chatsCards || config.settingsCards) {
                view.setBackgroundColor(palette.canvas)
            }
        }

        if (!isConv && isRecyclerOrList(view) && (config.chatsCards || config.settingsCards)) {
            view.setBackgroundColor(palette.canvas)
            if (view is ViewGroup) {
                scheduleListRestyle(view, config, palette)
            }
        }

        if (config.settingsCards && isSettingsLikeActivity(activity) && view is ScrollView && view.childCount > 0) {
            val child = view.getChildAt(0)
            if (child is LinearLayout && child.orientation == LinearLayout.VERTICAL) {
                styleSettingsColumn(child, config, palette)
            }
        }

        if (config.searchPill && entry in TOOLBAR_NAMES) {
            if (!isConv) {
                view.setBackgroundColor(palette.canvas)
            } else if (config.chatComposer) {
                view.setBackgroundColor(palette.card)
            }
        }

        if (config.searchPill && entry in SEARCH_BAR_NAMES) {
            applyPillBackground(view, palette.cardHigh, palette.outline, 28f, config.cardOutline)
        }

        if (config.chatComposer && isConv && entry in COMPOSER_NAMES) {
            applyPillBackground(view, palette.cardHigh, palette.outline, 26f, config.cardOutline)
        }

        if (config.expressiveFab && isFabView(view, entry)) {
            styleFab(view, palette)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walkAndStyle(view.getChildAt(i), activity, config, palette, depth + 1)
            }
        }
    }

    private fun onViewAttached(view: View, config: Config) {
        if (view.tag?.toString()?.startsWith("wp_chat_floating") == true) return
        val activity = activityOf(view.context)
        if (isExcludedActivity(activity)) return

        val entry = getEntryName(view)
        if (entry == "bottom_nav" && config.floatingBarActive) return

        val isConv = isConversationActivity(activity)
        val palette by lazy { resolvePalette(view.context, config) }

        if (config.styleDialogs && entry in DIALOG_NAMES) {
            val topOnly = entry.contains("bottom_sheet", ignoreCase = true)
            applySheetBackground(view, palette.cardHigh, palette.outline, 28f, topOnly, config.cardOutline)
            return
        }

        if (config.expressiveFab && isFabView(view, entry)) {
            view.post { runCatching { styleFab(view, palette) } }
            return
        }

        if (config.searchPill && entry in SEARCH_BAR_NAMES) {
            view.post {
                runCatching {
                    applyPillBackground(view, palette.cardHigh, palette.outline, 28f, config.cardOutline)
                }
            }
            return
        }

        if (config.searchPill && entry in TOOLBAR_NAMES) {
            if (!isConv) {
                view.setBackgroundColor(palette.canvas)
            } else if (config.chatComposer) {
                view.setBackgroundColor(palette.card)
            }
            return
        }

        if (config.chatComposer && isConv && entry in COMPOSER_NAMES) {
            view.post {
                runCatching {
                    applyPillBackground(view, palette.cardHigh, palette.outline, 26f, config.cardOutline)
                }
            }
            return
        }

        if (config.searchPill && !isConv && entry in CHIP_NAMES) {
            view.post {
                runCatching {
                    val bgColor = if (view.isSelected) palette.primaryContainer else palette.cardHigh
                    applyPillBackground(view, bgColor, palette.outline, 20f, config.cardOutline)
                }
            }
            return
        }

        if (isConv) return

        if (config.hideDividers && isLikelyDivider(view, entry)) {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.alpha = 0f
            return
        }

        val parent = view.parent as? ViewGroup ?: return
        if (isRecyclerOrList(parent)) {
            val allow = if (isSettingsLikeActivity(activity)) config.settingsCards else config.chatsCards
            if (!allow) return
            if (parent is ListView && config.hideDividers) {
                parent.divider = ColorDrawable(Color.TRANSPARENT)
                parent.dividerHeight = 0
            }
            if (isStyleableListRow(view, entry)) {
                applyRowCard(view, config, palette, topRound = true, bottomRound = true)
                if (config.cardStyle == "segmented") {
                    scheduleListRestyle(parent, config, palette)
                }
            }
        } else if (config.settingsCards && isSettingsLikeActivity(activity) && parent is LinearLayout &&
            parent.orientation == LinearLayout.VERTICAL
        ) {
            if (parent.parent is ScrollView || getEntryName(parent).contains("container", ignoreCase = true)) {
                scheduleSettingsRestyle(parent, config, palette)
            }
        }
    }

    private fun isLikelyDivider(view: View, entry: String): Boolean {
        if (entry.contains("divider", ignoreCase = true) || entry.contains("separator", ignoreCase = true)) {
            return view !is ViewGroup || view.childCount == 0
        }
        val lp = view.layoutParams ?: return false
        val maxDividerPx = dp(view.context, 2f).coerceAtLeast(2)
        return (view !is ViewGroup) && (view !is TextView) && lp.height in 1..maxDividerPx &&
            (lp.width == ViewGroup.LayoutParams.MATCH_PARENT || lp.width > dp(view.context, 80f))
    }

    private fun isStyleableListRow(view: View, entry: String): Boolean {
        if (view !is ViewGroup) return false
        if (view.childCount == 0) return false
        if (entry.contains("header", ignoreCase = true) ||
            entry.contains("search", ignoreCase = true) ||
            entry.contains("filter", ignoreCase = true) ||
            entry.contains("footer", ignoreCase = true) ||
            entry.contains("banner", ignoreCase = true) ||
            entry.contains("divider", ignoreCase = true)
        ) return false
        if (view.layoutParams?.height in 1..dp(view.context, 36f)) return false
        return true
    }

    private fun scheduleListRestyle(list: ViewGroup, config: Config, palette: ExpressivePalette) {
        if (!pendingContainers.add(list)) return
        list.post {
            pendingContainers.remove(list)
            runCatching {
                val rows = mutableListOf<View>()
                for (i in 0 until list.childCount) {
                    val child = list.getChildAt(i)
                    if (child.visibility == View.VISIBLE && isStyleableListRow(child, getEntryName(child))) {
                        rows.add(child)
                    }
                }
                if (rows.isEmpty()) return@runCatching
                rows.forEachIndexed { idx, row ->
                    val isTop = idx == 0
                    val isBottom = idx == rows.lastIndex
                    applyRowCard(row, config, palette, topRound = isTop, bottomRound = isBottom)
                }
            }
        }
    }

    private fun scheduleSettingsRestyle(column: LinearLayout, config: Config, palette: ExpressivePalette) {
        if (!pendingContainers.add(column)) return
        column.post {
            pendingContainers.remove(column)
            runCatching { styleSettingsColumn(column, config, palette) }
        }
    }

    private fun styleSettingsColumn(column: LinearLayout, config: Config, palette: ExpressivePalette) {
        column.setBackgroundColor(palette.canvas)
        val group = mutableListOf<View>()

        fun flushGroup() {
            if (group.isEmpty()) return
            group.forEachIndexed { idx, row ->
                applyRowCard(
                    row,
                    config,
                    palette,
                    topRound = idx == 0,
                    bottomRound = idx == group.lastIndex
                )
            }
            group.clear()
        }

        for (i in 0 until column.childCount) {
            val child = column.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val entry = getEntryName(child)
            if (config.hideDividers && isLikelyDivider(child, entry)) {
                child.setBackgroundColor(Color.TRANSPARENT)
                child.alpha = 0f
                flushGroup()
                continue
            }
            if (child is ViewGroup && child.childCount > 0 && (child.isClickable || child.hasOnClickListeners())) {
                group.add(child)
            } else {
                flushGroup()
            }
        }
        flushGroup()
    }

    private fun outerRadiusPx(context: Context, config: Config): Float {
        val base = when (config.cardStyle) {
            "soft" -> (config.cornerRadiusDp * 0.68f).coerceIn(10f, 18f)
            else -> config.cornerRadiusDp.toFloat()
        }
        return dpf(context, base)
    }

    private fun innerRadiusPx(context: Context, config: Config): Float {
        return if (config.cardStyle == "segmented") {
            dpf(context, 5.5f)
        } else {
            outerRadiusPx(context, config)
        }
    }

    private fun applyRowCard(
        view: View,
        config: Config,
        palette: ExpressivePalette,
        topRound: Boolean,
        bottomRound: Boolean
    ) {
        val ctx = view.context
        val outer = outerRadiusPx(ctx, config)
        val inner = innerRadiusPx(ctx, config)
        val topR = if (topRound) outer else inner
        val botR = if (bottomRound) outer else inner
        val radii = floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR)

        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(palette.card)
            if (config.cardOutline) {
                setStroke(dp(ctx, 1f).coerceAtLeast(1), palette.outline)
            }
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(Color.WHITE)
        }
        val ripple = RippleDrawable(ColorStateList.valueOf(palette.ripple), shape, mask)

        val insetH = dp(ctx, 10f)
        val insetV = dp(ctx, (config.cardGapDp / 2f).coerceAtLeast(1f))
        val drawable: Drawable = InsetDrawable(ripple, insetH, insetV, insetH, insetV)

        val pStart = view.paddingStart
        val pTop = view.paddingTop
        val pEnd = view.paddingEnd
        val pBottom = view.paddingBottom
        view.background = drawable

        if (paddedViews.add(view)) {
            val extraH = dp(ctx, 12f)
            val extraV = dp(ctx, 2f)
            view.setPaddingRelative(pStart + extraH, pTop + extraV, pEnd + extraH, pBottom + extraV)
        } else {
            view.setPaddingRelative(pStart, pTop, pEnd, pBottom)
        }
        styledViews.add(view)
    }

    private fun applyPillBackground(
        view: View,
        fillColor: Int,
        outlineColor: Int,
        radiusDp: Float,
        showOutline: Boolean
    ) {
        val ctx = view.context
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(ctx, radiusDp)
            setColor(fillColor)
            if (showOutline) {
                setStroke(dp(ctx, 1f).coerceAtLeast(1), outlineColor)
            }
        }
        val pStart = view.paddingStart
        val pTop = view.paddingTop
        val pEnd = view.paddingEnd
        val pBottom = view.paddingBottom
        view.background = shape
        view.clipToOutline = true
        view.setPaddingRelative(pStart, pTop, pEnd, pBottom)
    }

    private fun applySheetBackground(
        view: View,
        fillColor: Int,
        outlineColor: Int,
        radiusDp: Float,
        topOnly: Boolean,
        showOutline: Boolean
    ) {
        val ctx = view.context
        val r = dpf(ctx, radiusDp)
        val radii = if (topOnly) {
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        } else {
            floatArrayOf(r, r, r, r, r, r, r, r)
        }
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(fillColor)
            if (showOutline) {
                setStroke(dp(ctx, 1f).coerceAtLeast(1), outlineColor)
            }
        }
        view.background = shape
        view.clipToOutline = true
    }

    private fun styleFab(view: View, palette: ExpressivePalette) {
        val ctx = view.context
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(ctx, 18f)
            setColor(palette.primaryContainer)
            setStroke(dp(ctx, 1f).coerceAtLeast(1), ColorUtils.setAlphaComponent(palette.primary, 96))
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(ctx, 18f)
            setColor(Color.WHITE)
        }
        view.background = RippleDrawable(ColorStateList.valueOf(palette.ripple), shape, mask)
        view.backgroundTintList = null
        view.clipToOutline = true
        if (view is android.widget.ImageView) {
            view.imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
        }
    }

    companion object {
        private val EXCLUDED_ACTIVITIES = listOf(
            "Camera", "MediaView", "StatusPlayback", "Gallery", "Video", "Voip", "CallActivity", "QrCode"
        )
        private val SETTINGS_ACTIVITIES = listOf(
            "Settings", "InfoActivity", "Info", "Privacy", "Account", "Profile", "Notifications", "Storage", "Help"
        )
        private val TOOLBAR_NAMES = setOf(
            "toolbar", "conversation_toolbar", "action_bar", "toolbar_holder"
        )
        private val SEARCH_BAR_NAMES = setOf(
            "my_search_bar", "search_bar", "search_holder", "search_container", "search_plate"
        )
        private val COMPOSER_NAMES = setOf(
            "input_layout", "input_layout_content", "entry_container"
        )
        private val CHIP_NAMES = setOf(
            "inbox_filter", "filter_chip", "chip"
        )
        private val DIALOG_NAMES = setOf(
            "design_bottom_sheet", "parentPanel"
        )
    }
}
