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
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
import kotlin.math.abs

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
    private val rowBasePadding = WeakHashMap<View, IntArray>()
    private val styledViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val pendingContainers = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())
    private val observedLists = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())

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
        val outline = ColorUtils.setAlphaComponent(outlineRgb, if (dark) 40 else 58)
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

    private fun classNameContains(view: View, token: String): Boolean {
        var cls: Class<*>? = view.javaClass
        var depth = 0
        while (cls != null && depth < 5) {
            if (cls.name.contains(token, ignoreCase = true)) return true
            cls = cls.superclass
            depth++
        }
        return false
    }

    private fun isRecyclerOrList(view: View?): Boolean {
        if (view == null) return false
        if (view is ListView) return true
        return classNameContains(view, "RecyclerView")
    }

    private fun isHorizontalContainer(view: View): Boolean {
        if (view is HorizontalScrollView) return true
        if (classNameContains(view, "HorizontalScroll") || classNameContains(view, "ViewPager")) return true
        val lmHorizontal = runCatching {
            val lm = view.javaClass.getMethod("getLayoutManager").invoke(view) ?: return@runCatching false
            val canH = lm.javaClass.getMethod("canScrollHorizontally").invoke(lm) as? Boolean ?: false
            val canV = lm.javaClass.getMethod("canScrollVertically").invoke(lm) as? Boolean ?: true
            canH && !canV
        }.getOrDefault(false)
        if (lmHorizontal) return true

        if (view is ViewGroup && view.childCount >= 2) {
            var first: View? = null
            var second: View? = null
            for (i in 0 until view.childCount) {
                val c = view.getChildAt(i)
                if (c.visibility == View.VISIBLE && c.width > 0 && c.height > 0) {
                    if (first == null) first = c else {
                        second = c
                        break
                    }
                }
            }
            if (first != null && second != null) {
                val dx = abs(second.left - first.left)
                val dy = abs(second.top - first.top)
                if (dx > dy && dx > dp(view.context, 16f)) return true
            }
        }
        return false
    }

    private fun isTopBarContainer(view: View, entry: String): Boolean {
        if (entry in TOP_BAR_NAMES) return true
        return classNameContains(view, "AppBarLayout") ||
            classNameContains(view, "CollapsingToolbar") ||
            classNameContains(view, "Toolbar")
    }

    private fun isFabView(view: View, entryName: String): Boolean {
        if (view.tag?.toString()?.startsWith("wp_chat_floating") == true) return false
        if (entryName == "fab" || entryName == "fab_second" || entryName == "fab_aux" ||
            entryName == "floating_action_button"
        ) return true
        return classNameContains(view, "FloatingActionButton")
    }

    private fun flattenTopBar(view: View, palette: ExpressivePalette, isConv: Boolean, config: Config) {
        view.elevation = 0f
        view.stateListAnimator = null
        if (!isConv) {
            view.setBackgroundColor(palette.canvas)
        } else if (config.chatComposer) {
            view.setBackgroundColor(palette.card)
        }
        if (view is ViewGroup) {
            clearTopDividers(view, 0)
        }
    }

    private fun clearTopDividers(group: ViewGroup, depth: Int) {
        if (depth > 3) return
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            val entry = getEntryName(child)
            if (isLikelyDivider(child, entry)) {
                child.setBackgroundColor(Color.TRANSPARENT)
                child.alpha = 0f
            } else if (child is ViewGroup) {
                if (entry in TOP_BAR_NAMES || classNameContains(child, "Toolbar")) {
                    child.elevation = 0f
                    child.stateListAnimator = null
                }
                clearTopDividers(child, depth + 1)
            }
        }
    }

    private fun hasSearchPillAncestor(view: View): Boolean {
        var cur = view.parent as? View
        var depth = 0
        while (cur != null && depth < 4) {
            if (getEntryName(cur) in SEARCH_PILL_NAMES) return true
            cur = cur.parent as? View
            depth++
        }
        return false
    }

    private fun stripNestedSearchBackgrounds(group: ViewGroup, palette: ExpressivePalette, depth: Int) {
        if (depth > 5) return
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            val cEntry = getEntryName(c)
            c.background = null
            c.elevation = 0f
            c.stateListAnimator = null
            if (c is ImageView) {
                val isSearchGlyph = cEntry.contains("search", ignoreCase = true) ||
                    cEntry.contains("mag", ignoreCase = true) ||
                    (cEntry.contains("icon", ignoreCase = true) &&
                        !cEntry.contains("ai", ignoreCase = true) &&
                        !cEntry.contains("meta", ignoreCase = true) &&
                        !cEntry.contains("avatar", ignoreCase = true))
                if (isSearchGlyph) {
                    c.imageTintList = ColorStateList.valueOf(palette.primary)
                }
            }
            if (c is ViewGroup) {
                stripNestedSearchBackgrounds(c, palette, depth + 1)
            }
        }
    }

    private fun styleSearchPillClean(view: View, palette: ExpressivePalette, config: Config) {
        if (hasSearchPillAncestor(view)) {
            view.background = null
            view.elevation = 0f
            return
        }
        var ancestor = view.parent as? ViewGroup
        var up = 0
        while (ancestor != null && up < 2) {
            val pEntry = getEntryName(ancestor)
            if (pEntry in SEARCH_WRAPPER_NAMES || pEntry.contains("search", ignoreCase = true)) {
                ancestor.setBackgroundColor(Color.TRANSPARENT)
                ancestor.elevation = 0f
                ancestor.stateListAnimator = null
            }
            ancestor = ancestor.parent as? ViewGroup
            up++
        }
        if (view is ViewGroup) {
            stripNestedSearchBackgrounds(view, palette, 0)
        }
        val ctx = view.context
        val fill = ColorUtils.blendARGB(palette.card, palette.cardHigh, 0.72f)
        val radius = dpf(ctx, 28f)
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (config.cardOutline) {
                setStroke(dp(ctx, 1f).coerceAtLeast(1), palette.outline)
            }
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        val ripple = RippleDrawable(ColorStateList.valueOf(palette.ripple), shape, mask)
        val insetH = dp(ctx, 6f)
        val insetV = dp(ctx, 4f)
        val pStart = view.paddingStart
        val pTop = view.paddingTop
        val pEnd = view.paddingEnd
        val pBottom = view.paddingBottom
        view.background = InsetDrawable(ripple, insetH, insetV, insetH, insetV)
        view.elevation = 0f
        view.stateListAnimator = null
        if (paddedViews.add(view)) {
            val addH = dp(ctx, 12f)
            val addV = dp(ctx, 2f)
            view.setPaddingRelative(pStart + addH, pTop + addV, pEnd + addH, pBottom + addV)
        } else {
            view.setPaddingRelative(pStart, pTop, pEnd, pBottom)
        }
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

        if (config.hideDividers && !isConv && isLikelyDivider(view, entry)) {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.alpha = 0f
            return
        }

        if (config.searchPill && isTopBarContainer(view, entry)) {
            flattenTopBar(view, palette, isConv, config)
        }

        if (config.searchPill && entry in SEARCH_WRAPPER_NAMES) {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.elevation = 0f
        } else if (config.searchPill && entry in SEARCH_PILL_NAMES) {
            styleSearchPillClean(view, palette, config)
        }

        if (config.chatComposer && isConv && entry in COMPOSER_NAMES) {
            applyPillBackground(view, palette.cardHigh, palette.outline, 26f, config.cardOutline)
        }

        if (!isConv && view is ListView) {
            if (config.hideDividers) {
                view.divider = ColorDrawable(Color.TRANSPARENT)
                view.dividerHeight = 0
            }
            if (config.chatsCards || config.settingsCards) {
                view.setBackgroundColor(palette.canvas)
            }
        }

        if (!isConv && isRecyclerOrList(view) && !isHorizontalContainer(view) &&
            (config.chatsCards || config.settingsCards)
        ) {
            val allow = if (isSettingsLikeActivity(activity)) config.settingsCards else config.chatsCards
            if (allow && view is ViewGroup) {
                view.setBackgroundColor(palette.canvas)
                observeVerticalList(view, config, palette)
                scheduleListRestyle(view, config, palette)
            }
        }

        if (config.settingsCards && isSettingsLikeActivity(activity) && view is ScrollView && view.childCount > 0) {
            val child = view.getChildAt(0)
            if (child is LinearLayout && child.orientation == LinearLayout.VERTICAL) {
                styleSettingsColumn(child, config, palette)
            }
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

    private fun observeVerticalList(list: ViewGroup, config: Config, palette: ExpressivePalette) {
        if (!observedLists.add(list)) return
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            scheduleListRestyle(list, config, palette)
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

        if (config.searchPill && isTopBarContainer(view, entry)) {
            view.post { runCatching { flattenTopBar(view, palette, isConv, config) } }
            return
        }

        if (config.searchPill && entry in SEARCH_WRAPPER_NAMES) {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.elevation = 0f
            return
        }

        if (config.searchPill && entry in SEARCH_PILL_NAMES) {
            view.post { runCatching { styleSearchPillClean(view, palette, config) } }
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

        if (isConv) return

        if (config.hideDividers && isLikelyDivider(view, entry)) {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.alpha = 0f
            return
        }

        val parent = view.parent as? ViewGroup ?: return
        if (isRecyclerOrList(parent)) {
            if (isHorizontalContainer(parent)) return
            val allow = if (isSettingsLikeActivity(activity)) config.settingsCards else config.chatsCards
            if (!allow) return
            if (parent is ListView && config.hideDividers) {
                parent.divider = ColorDrawable(Color.TRANSPARENT)
                parent.dividerHeight = 0
            }
            observeVerticalList(parent, config, palette)
            scheduleListRestyle(parent, config, palette)
        } else if (config.settingsCards && isSettingsLikeActivity(activity) && parent is LinearLayout &&
            parent.orientation == LinearLayout.VERTICAL
        ) {
            if (parent.parent is ScrollView || getEntryName(parent).contains("container", ignoreCase = true)) {
                scheduleSettingsRestyle(parent, config, palette)
            }
        }
    }

    private fun isLikelyDivider(view: View, entry: String): Boolean {
        if (entry.contains("divider", ignoreCase = true) ||
            entry.contains("separator", ignoreCase = true) ||
            entry.contains("hairline", ignoreCase = true) ||
            entry.contains("border_bottom", ignoreCase = true) ||
            entry.contains("shadow", ignoreCase = true)
        ) {
            return view !is ViewGroup || view.childCount == 0
        }
        val maxDividerPx = dp(view.context, 3f).coerceAtLeast(3)
        val lp = view.layoutParams
        val h = if (view.height > 0) view.height else (lp?.height ?: 0)
        val w = if (view.width > 0) view.width else (lp?.width ?: 0)
        val wide = w == ViewGroup.LayoutParams.MATCH_PARENT || w > dp(view.context, 100f)
        return (view !is ViewGroup) && (view !is TextView) && (view !is ImageView) && h in 1..maxDividerPx && wide
    }

    private fun containsForbiddenChild(view: View, depth: Int): Boolean {
        if (depth > 4) return false
        if (view is EditText || view is HorizontalScrollView) return true
        if (depth > 0 && isRecyclerOrList(view)) return true
        if (classNameContains(view, "ChipGroup") ||
            classNameContains(view, "ViewPager") ||
            classNameContains(view, "HorizontalScroll")
        ) return true
        val entry = getEntryName(view)
        if (entry in SEARCH_PILL_NAMES || entry in SEARCH_WRAPPER_NAMES ||
            entry.contains("inbox_filter", ignoreCase = true) ||
            entry.contains("filter_chip", ignoreCase = true) ||
            entry.contains("ig_status", ignoreCase = true) ||
            entry.contains("status_bar", ignoreCase = true)
        ) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsForbiddenChild(view.getChildAt(i), depth + 1)) return true
            }
        }
        return false
    }

    private fun hasNonEmptyVisibleText(view: View, depth: Int): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view is TextView && !view.text.isNullOrBlank()) return true
        if (view is ViewGroup && depth < 5) {
            for (i in 0 until view.childCount) {
                if (hasNonEmptyVisibleText(view.getChildAt(i), depth + 1)) return true
            }
        }
        return false
    }

    private fun isValidRenderedRow(view: View, entry: String): Boolean {
        if (view !is ViewGroup) return false
        if (view.visibility != View.VISIBLE || view.childCount == 0) return false
        if (FORBIDDEN_ROW_TOKENS.any { entry.contains(it, ignoreCase = true) }) return false

        val screenW = view.resources.displayMetrics.widthPixels
        val minW = (screenW * 0.78f).toInt()
        val w = view.width
        val lpW = view.layoutParams?.width ?: 0
        if (w > 0 && w < minW) return false
        if (w == 0 && lpW > 0 && lpW < minW) return false

        val minH = dp(view.context, 46f)
        val h = view.height
        val lpH = view.layoutParams?.height ?: 0
        if (h > 0 && h < minH) return false
        if (h == 0 && lpH in 1 until minH) return false

        if (containsForbiddenChild(view, 0)) return false
        if (!hasNonEmptyVisibleText(view, 0)) return false
        return true
    }

    private fun scheduleListRestyle(list: ViewGroup, config: Config, palette: ExpressivePalette) {
        if (!pendingContainers.add(list)) return
        list.post {
            pendingContainers.remove(list)
            runCatching {
                if (isHorizontalContainer(list)) {
                    for (i in 0 until list.childCount) {
                        val c = list.getChildAt(i)
                        if (styledViews.remove(c)) c.background = null
                    }
                    return@runCatching
                }

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

                for (i in 0 until list.childCount) {
                    val child = list.getChildAt(i)
                    if (child.visibility != View.VISIBLE) continue
                    val entry = getEntryName(child)
                    if (config.hideDividers && isLikelyDivider(child, entry)) {
                        child.setBackgroundColor(Color.TRANSPARENT)
                        child.alpha = 0f
                        flushGroup()
                        continue
                    }
                    if (isValidRenderedRow(child, entry)) {
                        group.add(child)
                    } else {
                        if (styledViews.remove(child)) {
                            child.background = null
                        }
                        if (config.hideDividers && child.height in 1..dp(list.context, 24f) &&
                            !hasNonEmptyVisibleText(child, 0)
                        ) {
                            child.setBackgroundColor(Color.TRANSPARENT)
                        }
                        flushGroup()
                    }
                }
                flushGroup()
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
            if (child is ViewGroup && child.childCount > 0 &&
                (child.isClickable || child.hasOnClickListeners()) &&
                !containsForbiddenChild(child, 0) &&
                hasNonEmptyVisibleText(child, 0)
            ) {
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

        val base = rowBasePadding.getOrPut(view) {
            intArrayOf(view.paddingStart, view.paddingTop, view.paddingEnd, view.paddingBottom)
        }
        view.background = drawable

        val addStart = if (base[0] < dp(ctx, 10f)) dp(ctx, 14f) else dp(ctx, 6f)
        val addEnd = if (base[2] < dp(ctx, 10f)) dp(ctx, 10f) else dp(ctx, 4f)
        val targetStart = base[0] + insetH + addStart
        val targetTop = base[1] + insetV + dp(ctx, 2f)
        val targetEnd = base[2] + insetH + addEnd
        val targetBottom = base[3] + insetV + dp(ctx, 2f)
        if (view.paddingStart != targetStart || view.paddingTop != targetTop ||
            view.paddingEnd != targetEnd || view.paddingBottom != targetBottom
        ) {
            view.setPaddingRelative(targetStart, targetTop, targetEnd, targetBottom)
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
            if (showOutline && outlineColor != Color.TRANSPARENT) {
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
        if (view is ImageView) {
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
        private val TOP_BAR_NAMES = setOf(
            "toolbar", "conversation_toolbar", "action_bar", "action_bar_container",
            "toolbar_holder", "header", "appbar", "app_bar", "collapsing_toolbar"
        )
        private val SEARCH_WRAPPER_NAMES = setOf(
            "search_holder", "search_container", "search_bar_container", "header_search_container"
        )
        private val SEARCH_PILL_NAMES = setOf(
            "my_search_bar", "search_bar", "search_plate"
        )
        private val COMPOSER_NAMES = setOf(
            "input_layout", "input_layout_content", "entry_container"
        )
        private val DIALOG_NAMES = setOf(
            "design_bottom_sheet", "parentPanel"
        )
        private val FORBIDDEN_ROW_TOKENS = listOf(
            "header", "search", "filter", "footer", "banner", "divider",
            "status", "story", "stories", "chip", "tab", "spacer", "empty", "ig_"
        )
    }
}
