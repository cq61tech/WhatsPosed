package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.core.WppCore
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class IOS27Theme(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private data class IosPalette(
        val dark: Boolean,
        val canvas: Int,
        val surface: Int,
        val surfaceElevated: Int,
        val glassTop: Int,
        val glassBottom: Int,
        val glassBorder: Int,
        val glassSpecular: Int,
        val accentGreen: Int,
        val accentBlue: Int,
        val divider: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val outgoingBubble: Int,
        val incomingBubble: Int,
        val ripple: Int,
    )

    private data class Config(
        val oledCanvas: Boolean,
        val largeHeaderAndSearch: Boolean,
        val glassOverlays: Boolean,
        val glassFabs: Boolean,
        val iosBubbles: Boolean,
        val iosTypography: Boolean,
        val contactGlassCards: Boolean,
    )

    private val idNameCache = ConcurrentHashMap<Int, String>()
    private val styledViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val styledTypefaceViews = Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())

    override fun getPluginName(): String = "IOS27Theme"

    override fun doHook() {
        val activeTheme = xprefs.getString("active_ui_theme", "") ?: ""
        val enabled = activeTheme == "ios27" || xprefs.getBoolean("ios27_enabled", false)
        if (!enabled) return

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
        oledCanvas = xprefs.getBoolean("ios27_oled_canvas", true),
        largeHeaderAndSearch = xprefs.getBoolean("ios27_header_search", true),
        glassOverlays = xprefs.getBoolean("ios27_glass_overlays", true),
        glassFabs = xprefs.getBoolean("ios27_glass_fabs", true),
        iosBubbles = xprefs.getBoolean("ios27_bubbles", true),
        iosTypography = xprefs.getBoolean("ios27_typography", true),
        contactGlassCards = xprefs.getBoolean("ios27_contact_cards", true),
    )

    private fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun resolvePalette(context: Context, config: Config): IosPalette {
        val dark = isDark(context)
        return if (dark) {
            IosPalette(
                dark = true,
                canvas = if (config.oledCanvas) 0xFF000000.toInt() else 0xFF0B0D10.toInt(),
                surface = 0xFF1C1C1E.toInt(),
                surfaceElevated = 0xFF2C2C2E.toInt(),
                glassTop = 0xD827292E.toInt(),
                glassBottom = 0xCC18191D.toInt(),
                glassBorder = 0x4DFFFFFF,
                glassSpecular = 0x33FFFFFF,
                accentGreen = 0xFF30D158.toInt(),
                accentBlue = 0xFF0A84FF.toInt(),
                divider = 0x24FFFFFF,
                textPrimary = 0xFFFFFFFF.toInt(),
                textSecondary = 0x99EBEBF5.toInt(),
                outgoingBubble = 0xFF005C4B.toInt(),
                incomingBubble = 0xFF1F2225.toInt(),
                ripple = 0x26FFFFFF,
            )
        } else {
            IosPalette(
                dark = false,
                canvas = 0xFFFFFFFF.toInt(),
                surface = 0xFFF2F2F7.toInt(),
                surfaceElevated = 0xFFFFFFFF.toInt(),
                glassTop = 0xE6FFFFFF.toInt(),
                glassBottom = 0xD9F2F4F8.toInt(),
                glassBorder = 0x66FFFFFF,
                glassSpecular = 0x80FFFFFF.toInt(),
                accentGreen = 0xFF25D366.toInt(),
                accentBlue = 0xFF007AFF.toInt(),
                divider = 0x1E000000,
                textPrimary = 0xFF000000.toInt(),
                textSecondary = 0x993C3C43.toInt(),
                outgoingBubble = 0xFFD9FDD3.toInt(),
                incomingBubble = 0xFFFFFFFF.toInt(),
                ripple = 0x1A000000,
            )
        }
    }

    private fun styleActivityWindow(activity: Activity, config: Config) {
        if (activity.isFinishing || activity.isDestroyed) return
        val palette = resolvePalette(activity, config)
        val isConv = isConversationActivity(activity)
        val isInfo = isContactOrGroupInfoActivity(activity)
        val isSettings = isSettingsLikeActivity(activity)

        if (!isConv) {
            activity.window?.let { w ->
                val bg = if (isInfo || isSettings) {
                    if (palette.dark) palette.canvas else palette.surface
                } else {
                    palette.canvas
                }
                w.setBackgroundDrawable(ColorDrawable(bg))
                runCatching {
                    w.statusBarColor = Color.TRANSPARENT
                    if (!xprefs.getBoolean("floating_bottom_bar", false)) {
                        w.navigationBarColor = bg
                    }
                }
            }
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val pass = {
            if (!activity.isFinishing && !activity.isDestroyed) {
                walkAndStyle(content, activity, config, palette, 0)
            }
        }
        content.post { pass() }
        content.postDelayed({ pass() }, 220L)
    }

    private fun walkAndStyle(
        view: View,
        activity: Activity,
        config: Config,
        palette: IosPalette,
        depth: Int
    ) {
        if (depth > 15) return
        if (view.tag?.toString()?.startsWith("wp_chat_floating") == true) return
        val entry = getEntryName(view)
        if (entry == "bottom_nav") return

        val isConv = isConversationActivity(activity)
        val isHome = isHomeActivity(activity)
        val isInfo = isContactOrGroupInfoActivity(activity)
        val isSettings = isSettingsLikeActivity(activity)

        // 1) Hairline iOS dividers
        if (!isConv && isLikelyDivider(view, entry)) {
            view.setBackgroundColor(palette.divider)
            view.alpha = 1f
            return
        }

        // 2) Home / non-conv top bar & iOS large title
        if (config.largeHeaderAndSearch && !isConv && isTopBarContainer(view, entry)) {
            view.elevation = 0f
            view.stateListAnimator = null
            view.setBackgroundColor(
                if (isInfo || isSettings) {
                    if (palette.dark) palette.canvas else palette.surface
                } else {
                    palette.canvas
                }
            )
            if (isHome && view is ViewGroup) {
                styleHomeLargeTitle(view, palette, config)
            }
        }

        // 3) Search pill (iOS 27 rounded frosted pill)
        if (config.largeHeaderAndSearch && !isConv) {
            if (entry in SEARCH_WRAPPER_NAMES) {
                view.setBackgroundColor(Color.TRANSPARENT)
                view.elevation = 0f
            } else if (entry in SEARCH_PILL_NAMES) {
                styleIosSearchPill(view, palette)
            }
        }

        // 4) Main lists background
        if (!isConv && (view is ListView || isRecyclerView(view))) {
            val bg = if (isInfo || isSettings) {
                if (palette.dark) palette.canvas else palette.surface
            } else {
                palette.canvas
            }
            view.setBackgroundColor(bg)
            if (view is ListView) {
                view.divider = ColorDrawable(palette.divider)
                view.dividerHeight = 1
            }
        }

        // 5) Contact / Group Info Liquid Glass action tiles & iOS InsetGrouped cards
        if (config.contactGlassCards && (isInfo || isSettings)) {
            if (entry in INFO_ACTION_TILE_NAMES) {
                applyGlassPill(view, palette, 18f, isCircle = false)
            } else if (view is ScrollView && view.childCount > 0) {
                val child = view.getChildAt(0)
                if (child is LinearLayout && child.orientation == LinearLayout.VERTICAL) {
                    styleIosGroupedColumn(child, palette)
                }
            }
        }

        // 6) Floating glass FABs & Scroll-to-bottom button
        if (config.glassFabs && isFabOrScrollDown(view, entry, isConv)) {
            styleGlassFab(view, palette, isConv)
        }

        // 7) iOS message bubbles in conversation
        if (config.iosBubbles && isConv && isBubbleContainer(view, entry)) {
            styleIosBubble(view, entry, palette)
        }

        // 8) Clean iOS-like typography (SF Pro inspired weight & tracking)
        if (config.iosTypography && view is TextView && !isConv) {
            applyIosTypography(view, entry, isHome)
        } else if (config.iosTypography && isConv && view is TextView &&
            (entry == "conversation_contact_name" || entry == "conversation_contact_status" || entry == "message_text")
        ) {
            applyIosTypography(view, entry, false)
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
        if (entry == "bottom_nav") return
        val isConv = isConversationActivity(activity)
        val palette by lazy { resolvePalette(view.context, config) }

        // Liquid Glass overlays: reaction tray, dialogs, bottom sheets, context/overflow menus
        if (config.glassOverlays && isGlassOverlay(view, entry)) {
            val isReaction = entry.contains("reaction", ignoreCase = true)
            val topOnly = entry.contains("bottom_sheet", ignoreCase = true) ||
                entry.contains("attach", ignoreCase = true)
            val radius = when {
                isReaction -> 32f
                topOnly -> 30f
                else -> 26f
            }
            view.post {
                runCatching {
                    applyGlassSheet(view, palette, radius, topOnly)
                    tryApplyWindowBlur(view)
                }
            }
            return
        }

        if (config.glassFabs && isFabOrScrollDown(view, entry, isConv)) {
            view.post { runCatching { styleGlassFab(view, palette, isConv) } }
            return
        }

        if (config.largeHeaderAndSearch && !isConv && entry in SEARCH_PILL_NAMES) {
            view.post { runCatching { styleIosSearchPill(view, palette) } }
            return
        }

        if (config.iosBubbles && isConv && isBubbleContainer(view, entry)) {
            view.post { runCatching { styleIosBubble(view, entry, palette) } }
            return
        }
    }

    private fun styleHomeLargeTitle(toolbar: ViewGroup, palette: IosPalette, config: Config) {
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView && !child.text.isNullOrBlank()) {
                val t = child.text.toString()
                if (t.length in 2..28) {
                    child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    child.letterSpacing = -0.025f
                    child.setTextColor(palette.textPrimary)
                    if (config.iosTypography) {
                        setWeightTypeface(child, 700)
                    } else {
                        child.setTypeface(child.typeface, Typeface.BOLD)
                    }
                    break
                }
            }
        }
    }

    private fun styleIosSearchPill(view: View, palette: IosPalette) {
        val fill = if (palette.dark) 0xCC1C1C1E.toInt() else 0xCCF0F1F5.toInt()
        val stroke = if (palette.dark) 0x26FFFFFF else 0x16000000
        val bg = buildGlassDrawable(
            context = view.context,
            topColor = fill,
            bottomColor = ColorUtils.blendARGB(fill, palette.canvas, 0.14f),
            borderColor = stroke,
            specularColor = if (palette.dark) 0x1FFFFFFF else 0x55FFFFFF,
            radiusDp = 22f,
            topOnly = false
        )
        view.background = bg
        view.elevation = 0f
        view.clipToOutline = true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val c = view.getChildAt(i)
                if (c !is EditText) {
                    c.background = null
                }
            }
        }
    }

    private fun styleGlassFab(view: View, palette: IosPalette, isConv: Boolean) {
        val isScrollBtn = getEntryName(view).contains("scroll", ignoreCase = true) || isConv
        val topCol = if (isScrollBtn) {
            palette.glassTop
        } else {
            if (palette.dark) 0xE625D366.toInt() else 0xF025D366.toInt()
        }
        val botCol = if (isScrollBtn) {
            palette.glassBottom
        } else {
            if (palette.dark) 0xDD1CB854.toInt() else 0xEB1AB856.toInt()
        }
        val borderCol = if (isScrollBtn) palette.glassBorder else 0x66FFFFFF
        val glass = buildGlassDrawable(
            context = view.context,
            topColor = topCol,
            bottomColor = botCol,
            borderColor = borderCol,
            specularColor = 0x4DFFFFFF,
            radiusDp = 28f,
            topOnly = false
        )
        view.backgroundTintList = null
        view.background = RippleDrawable(ColorStateList.valueOf(palette.ripple), glass, null)
        view.elevation = dp(view.context, 6f).toFloat()
        view.clipToOutline = true

        if (view is ImageView) {
            view.imageTintList = ColorStateList.valueOf(
                if (isScrollBtn) palette.textPrimary else Color.WHITE
            )
        }
    }

    private fun applyGlassPill(view: View, palette: IosPalette, radiusDp: Float, isCircle: Boolean) {
        if (!styledViews.add(view)) return
        val bg = buildGlassDrawable(
            context = view.context,
            topColor = palette.glassTop,
            bottomColor = palette.glassBottom,
            borderColor = palette.glassBorder,
            specularColor = palette.glassSpecular,
            radiusDp = if (isCircle) 40f else radiusDp,
            topOnly = false
        )
        view.background = RippleDrawable(ColorStateList.valueOf(palette.ripple), bg, null)
        view.clipToOutline = true
        view.elevation = dp(view.context, 3f).toFloat()
    }

    private fun applyGlassSheet(view: View, palette: IosPalette, radiusDp: Float, topOnly: Boolean) {
        val bg = buildGlassDrawable(
            context = view.context,
            topColor = palette.glassTop,
            bottomColor = palette.glassBottom,
            borderColor = palette.glassBorder,
            specularColor = palette.glassSpecular,
            radiusDp = radiusDp,
            topOnly = topOnly
        )
        view.background = bg
        view.clipToOutline = true
        view.elevation = dp(view.context, 12f).toFloat()
    }

    private fun tryApplyWindowBlur(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val root = view.rootView ?: return
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        if (lp.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW ||
            (lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
        ) {
            runCatching {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = dp(view.context, 22f)
                lp.dimAmount = 0.22f
                wm.updateViewLayout(root, lp)
            }
        }
    }

    private fun styleIosGroupedColumn(column: LinearLayout, palette: IosPalette) {
        val cardColor = if (palette.dark) palette.surface else 0xFFFFFFFF.toInt()
        val stroke = if (palette.dark) 0x1FFFFFFF else 0x12000000
        val padH = dp(column.context, 14f)
        if (styledViews.add(column)) {
            column.setPadding(
                column.paddingLeft.coerceAtLeast(padH),
                column.paddingTop,
                column.paddingRight.coerceAtLeast(padH),
                column.paddingBottom
            )
        }
        for (i in 0 until column.childCount) {
            val child = column.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child is ViewGroup && child.childCount > 0 && styledViews.add(child)) {
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(column.context, 18f).toFloat()
                    setColor(cardColor)
                    setStroke(dp(column.context, 0.75f).coerceAtLeast(1), stroke)
                }
                child.background = bg
                child.clipToOutline = true
                (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let { mlp ->
                    if (mlp.bottomMargin < dp(column.context, 10f)) {
                        mlp.bottomMargin = dp(column.context, 10f)
                        child.layoutParams = mlp
                    }
                }
            }
        }
    }

    private fun styleIosBubble(view: View, entry: String, palette: IosPalette) {
        if (!styledViews.add(view)) return
        val outgoing = entry.contains("out", ignoreCase = true) ||
            entry.contains("sent", ignoreCase = true)
        val fill = if (outgoing) palette.outgoingBubble else palette.incomingBubble
        val stroke = if (palette.dark) {
            if (outgoing) 0x29FFFFFF else 0x1FFFFFFF
        } else {
            0x14000000
        }
        val r = dp(view.context, 18f).toFloat()
        val tail = dp(view.context, 5f).toFloat()
        val radii = if (outgoing) {
            floatArrayOf(r, r, r, r, tail, tail, r, r)
        } else {
            floatArrayOf(r, r, r, r, r, r, tail, tail)
        }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(fill)
            setStroke(1, stroke)
        }
        view.background = bg
    }

    private fun applyIosTypography(tv: TextView, entry: String, isHome: Boolean) {
        if (!styledTypefaceViews.add(tv)) return
        val isTitle = entry.contains("contact_name", ignoreCase = true) ||
            entry.contains("title", ignoreCase = true) ||
            entry.contains("header", ignoreCase = true) ||
            (isHome && tv.textSize >= dp(tv.context, 16f))
        val weight = when {
            isTitle -> 600
            tv.typeface?.isBold == true -> 600
            else -> 400
        }
        setWeightTypeface(tv, weight)
        if (isTitle) {
            tv.letterSpacing = -0.015f
        } else if (tv.letterSpacing == 0f) {
            tv.letterSpacing = -0.006f
        }
    }

    private fun setWeightTypeface(tv: TextView, weight: Int) {
        val italic = tv.typeface?.isItalic == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            tv.typeface = Typeface.create(Typeface.SANS_SERIF, weight, italic)
        } else {
            val style = when {
                weight >= 600 && italic -> Typeface.BOLD_ITALIC
                weight >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            tv.setTypeface(Typeface.SANS_SERIF, style)
        }
    }

    private fun buildGlassDrawable(
        context: Context,
        topColor: Int,
        bottomColor: Int,
        borderColor: Int,
        specularColor: Int,
        radiusDp: Float,
        topOnly: Boolean
    ): Drawable {
        val r = dp(context, radiusDp).toFloat()
        val radii = if (topOnly) {
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        } else {
            floatArrayOf(r, r, r, r, r, r, r, r)
        }
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setStroke(dp(context, 1f).coerceAtLeast(1), borderColor)
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(specularColor, Color.TRANSPARENT)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
        }
        val inset = InsetDrawable(sheen, dp(context, 1f), dp(context, 1f), dp(context, 1f), dp(context, 14f))
        return LayerDrawable(arrayOf(base, inset))
    }

    private fun isGlassOverlay(view: View, entry: String): Boolean {
        if (entry in GLASS_OVERLAY_NAMES) return true
        val lower = entry.lowercase()
        if (lower.contains("reaction_tray") ||
            lower.contains("reactions_tray") ||
            lower.contains("reaction_bar") ||
            lower.contains("design_bottom_sheet") ||
            lower.contains("attach_popup") ||
            lower.contains("menu_popup")
        ) return true
        val cls = view.javaClass.name
        return cls.contains("DropDownListView") || cls.contains("MenuPopup")
    }

    private fun isFabOrScrollDown(view: View, entry: String, isConv: Boolean): Boolean {
        if (view.tag?.toString()?.startsWith("wp_chat_floating") == true) return false
        if (entry == "fab" || entry == "fab_second" || entry == "fab_aux" ||
            entry == "floating_action_button" || entry == "scroll_bottom" ||
            entry == "scroll_to_bottom"
        ) return true
        if (isConv && view is ImageButton && entry.contains("scroll", ignoreCase = true)) return true
        return view.javaClass.name.contains("FloatingActionButton")
    }

    private fun isBubbleContainer(view: View, entry: String): Boolean {
        if (view !is ViewGroup) return false
        return entry in BUBBLE_NAMES
    }

    private fun isTopBarContainer(view: View, entry: String): Boolean {
        if (entry in TOP_BAR_NAMES) return true
        val cls = view.javaClass.name
        return cls.endsWith(".Toolbar") || cls.contains("AppBarLayout")
    }

    private fun isLikelyDivider(view: View, entry: String): Boolean {
        val lower = entry.lowercase()
        if (lower.contains("divider") || lower.contains("separator") || lower.contains("hairline")) {
            return view !is ViewGroup || view.childCount == 0
        }
        val h = if (view.height > 0) view.height else (view.layoutParams?.height ?: 0)
        val w = if (view.width > 0) view.width else (view.layoutParams?.width ?: 0)
        val wide = w == ViewGroup.LayoutParams.MATCH_PARENT || w > dp(view.context, 100f)
        return view !is ViewGroup && view !is TextView && view !is ImageView && h in 1..3 && wide
    }

    private fun isRecyclerView(view: View): Boolean =
        view.javaClass.name.contains("RecyclerView")

    private fun isHomeActivity(activity: Activity?): Boolean =
        activity?.javaClass?.simpleName?.contains("HomeActivity", ignoreCase = true) == true

    private fun isConversationActivity(activity: Activity?): Boolean =
        activity?.javaClass?.simpleName?.equals("Conversation", ignoreCase = true) == true

    private fun isContactOrGroupInfoActivity(activity: Activity?): Boolean {
        val n = activity?.javaClass?.simpleName ?: return false
        return n.contains("ContactInfo", ignoreCase = true) ||
            n.contains("GroupChatInfo", ignoreCase = true) ||
            n.contains("ChatInfo", ignoreCase = true)
    }

    private fun isSettingsLikeActivity(activity: Activity?): Boolean {
        val n = activity?.javaClass?.simpleName ?: return false
        return n.contains("Settings", ignoreCase = true) ||
            n.contains("Account", ignoreCase = true) ||
            n.contains("Privacy", ignoreCase = true)
    }

    private fun isExcludedActivity(activity: Activity?): Boolean {
        val n = activity?.javaClass?.simpleName ?: return true
        return n.contains("Camera", ignoreCase = true) ||
            n.contains("MediaView", ignoreCase = true) ||
            n.contains("StatusPlayback", ignoreCase = true) ||
            n.contains("Voip", ignoreCase = true) ||
            n.contains("Call", ignoreCase = true) ||
            n.contains("Gallery", ignoreCase = true)
    }

    private fun activityOf(context: Context): Activity? {
        var cur = context
        var depth = 0
        while (cur is ContextWrapper && depth < 8) {
            if (cur is Activity) return cur
            cur = cur.baseContext
            depth++
        }
        return cur as? Activity
    }

    private fun getEntryName(view: View): String {
        val id = view.id
        if (id == View.NO_ID || id <= 0) return ""
        return idNameCache.computeIfAbsent(id) {
            runCatching { view.resources.getResourceEntryName(id) }.getOrDefault("")
        }
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private val TOP_BAR_NAMES = setOf(
            "toolbar", "header", "app_bar", "appbar", "title_bar", "action_bar"
        )
        private val SEARCH_WRAPPER_NAMES = setOf(
            "search_holder", "search_bar_container", "my_search_bar_container"
        )
        private val SEARCH_PILL_NAMES = setOf(
            "search_bar", "search_bar_inner", "my_search_bar", "search_pill"
        )
        private val GLASS_OVERLAY_NAMES = setOf(
            "design_bottom_sheet", "parentPanel", "alertTitle", "customPanel",
            "reactions_tray_layout", "reaction_tray", "popup_window", "attach_sheet_container"
        )
        private val INFO_ACTION_TILE_NAMES = setOf(
            "action_call", "action_videocall", "action_search", "action_message",
            "action_pay", "action_share", "contact_info_action_btn"
        )
        private val BUBBLE_NAMES = setOf(
            "main_layout", "conversation_row_bubble", "bubble_container"
        )
    }
}
