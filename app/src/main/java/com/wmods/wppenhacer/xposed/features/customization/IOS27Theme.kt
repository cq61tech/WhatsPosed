package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
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
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.Collections
import java.util.Locale
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
        val chipBg: Int,
        val chipSelectedBg: Int,
        val ripple: Int,
    )

    private data class Config(
        val oledCanvas: Boolean,
        val largeHeaderAndSearch: Boolean,
        val glassOverlays: Boolean,
        val glassFabs: Boolean,
        val iosTypography: Boolean,
        val contactGlassCards: Boolean,
    )

    private val idNameCache = ConcurrentHashMap<Int, String>()
    private val styledViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val installedHeaders = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    @Volatile private var fontsLoaded = false
    @Volatile private var sfRegular: Typeface? = null
    @Volatile private var sfMedium: Typeface? = null
    @Volatile private var sfSemibold: Typeface? = null
    @Volatile private var sfBold: Typeface? = null

    override fun getPluginName(): String = "IOS27Theme"

    override fun doHook() {
        if (!ThemeCoordinator.isIos27(xprefs)) return

        val config = loadConfig()
        if (config.iosTypography) {
            ensureSfProFonts()
            hookGlobalSfProTypeface()
        }

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

    private fun ensureSfProFonts() {
        if (fontsLoaded) return
        synchronized(this) {
            if (fontsLoaded) return
            runCatching {
                val assets = FeatureLoader.moduleContext.assets
                sfRegular = runCatching { Typeface.createFromAsset(assets, "fonts/sf_pro_regular.otf") }.getOrNull()
                sfMedium = runCatching { Typeface.createFromAsset(assets, "fonts/sf_pro_medium.otf") }.getOrNull()
                sfSemibold = runCatching { Typeface.createFromAsset(assets, "fonts/sf_pro_semibold.otf") }.getOrNull()
                sfBold = runCatching { Typeface.createFromAsset(assets, "fonts/sf_pro_bold.otf") }.getOrNull()
            }
            fontsLoaded = true
        }
    }

    private fun resolveSfForTypeface(orig: Typeface?): Typeface? {
        ensureSfProFonts()
        val reg = sfRegular ?: return null
        if (orig === sfRegular || orig === sfMedium || orig === sfSemibold || orig === sfBold) return null
        if (orig === Typeface.MONOSPACE) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val w = orig?.weight ?: 400
            when {
                w >= 700 -> sfBold ?: sfSemibold ?: reg
                w >= 600 -> sfSemibold ?: sfBold ?: reg
                w >= 500 -> sfMedium ?: sfSemibold ?: reg
                else -> reg
            }
        } else {
            if (orig?.isBold == true) (sfBold ?: reg) else reg
        }
    }

    private fun hookGlobalSfProTypeface() {
        runCatching {
            TextView::class.java.resolve().firstMethod {
                name = "setTypeface"
                parameters(Typeface::class.java)
            }.hook {
                before {
                    val tv = instance as? TextView ?: return@before
                    val orig = args[0] as? Typeface
                    // Skip private-use icon fonts if any exist
                    val txt = tv.text
                    if (!txt.isNullOrEmpty() && txt.length <= 2 && txt[0].code in 0xE000..0xF8FF) {
                        return@before
                    }
                    val sf = resolveSfForTypeface(orig) ?: return@before
                    args[0] = sf
                }
            }
        }
    }

    private fun loadConfig(): Config = Config(
        oledCanvas = xprefs.getBoolean("ios27_oled_canvas", true),
        largeHeaderAndSearch = xprefs.getBoolean("ios27_header_search", true),
        glassOverlays = xprefs.getBoolean("ios27_glass_overlays", true),
        glassFabs = xprefs.getBoolean("ios27_glass_fabs", true),
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
                glassTop = 0xDD26282D.toInt(),
                glassBottom = 0xCE17181C.toInt(),
                glassBorder = 0x45FFFFFF,
                glassSpecular = 0x2EFFFFFF,
                accentGreen = 0xFF30D158.toInt(),
                accentBlue = 0xFF0A84FF.toInt(),
                divider = 0x1FFFFFFF,
                textPrimary = 0xFFFFFFFF.toInt(),
                textSecondary = 0x99EBEBF5.toInt(),
                chipBg = 0xFF1C1C1E.toInt(),
                chipSelectedBg = 0xFF0E331F.toInt(),
                ripple = 0x26FFFFFF,
            )
        } else {
            IosPalette(
                dark = false,
                canvas = 0xFFFFFFFF.toInt(),
                surface = 0xFFF2F2F7.toInt(),
                surfaceElevated = 0xFFFFFFFF.toInt(),
                glassTop = 0xECFFFFFF.toInt(),
                glassBottom = 0xDCF2F4F8.toInt(),
                glassBorder = 0x55000000.let { 0x66FFFFFF },
                glassSpecular = 0x80FFFFFF.toInt(),
                accentGreen = 0xFF25D366.toInt(),
                accentBlue = 0xFF007AFF.toInt(),
                divider = 0x18000000,
                textPrimary = 0xFF000000.toInt(),
                textSecondary = 0x993C3C43.toInt(),
                chipBg = 0xFFF0F0F5.toInt(),
                chipSelectedBg = 0xFFD9FDD3.toInt(),
                ripple = 0x1A000000,
            )
        }
    }

    private fun styleActivityWindow(activity: Activity, config: Config) {
        if (activity.isFinishing || activity.isDestroyed) return
        val palette = resolvePalette(activity, config)
        val isConv = isConversationActivity(activity)
        val isHome = isHomeActivity(activity)
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
                    w.statusBarColor = bg
                    if (!xprefs.getBoolean("floating_bottom_bar", false)) {
                        w.navigationBarColor = bg
                    }
                }
            }
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val pass = {
            if (!activity.isFinishing && !activity.isDestroyed) {
                if (isHome && config.largeHeaderAndSearch) {
                    installAuthenticIosHomeHeader(activity, palette, config)
                }
                walkAndStyle(content, activity, config, palette, 0)
            }
        }
        content.post { pass() }
        content.postDelayed({ pass() }, 240L)
        content.postDelayed({ pass() }, 650L)
    }

    /**
     * Builds the authentic 2-tier iOS WhatsApp Home header:
     * Row 1: Left frosted (...) button  |  Right frosted camera + bright green (+) button
     * Row 2: Large bold SF Pro Display title ("Sohbetler" / "Chats", dynamically synced with active tab)
     */
    private fun installAuthenticIosHomeHeader(activity: Activity, palette: IosPalette, config: Config) {
        val toolbarId = Utils.getID("toolbar", "id")
        val toolbar = if (toolbarId > 0) activity.findViewById<ViewGroup>(toolbarId) else null
        if (toolbar == null) return

        toolbar.elevation = 0f
        toolbar.stateListAnimator = null
        toolbar.setBackgroundColor(palette.canvas)

        // Hide original WhatsApp logo & small toolbar title
        val logoId = Utils.getID("toolbar_logo", "id")
        if (logoId > 0) {
            toolbar.findViewById<View>(logoId)?.visibility = View.GONE
        }
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            val tag = child.tag?.toString().orEmpty()
            if (tag.startsWith("wp_ios27_")) continue
            if (child is TextView) {
                child.visibility = View.GONE
            } else if (child is LinearLayout && child.childCount in 1..2 && child.getChildAt(0) is TextView) {
                child.visibility = View.GONE
            }
        }

        // Style existing ActionMenuView icons into frosted circular pills and tuck away right vertical 3-dots
        var overflowFallback: View? = null
        val actionMenu = findActionMenuView(toolbar)
        if (actionMenu != null) {
            for (i in 0 until actionMenu.childCount) {
                val item = actionMenu.getChildAt(i)
                val cls = item.javaClass.name
                val desc = item.contentDescription?.toString().orEmpty().lowercase()
                val isOverflow = cls.contains("Overflow", ignoreCase = true) ||
                    desc.contains("more") || desc.contains("diğer") || desc.contains("seçenek")
                if (isOverflow) {
                    overflowFallback = item
                    item.alpha = 0f
                    item.scaleX = 0.01f
                    item.scaleY = 0.01f
                    (item.layoutParams)?.let { lp ->
                        lp.width = 1
                        item.layoutParams = lp
                    }
                } else if (item.visibility == View.VISIBLE) {
                    styleIosCircleAction(item, palette, isGreen = false)
                }
            }
        }

        val btnSize = dp(activity, 34f)

        // Left circular (...) glass pill
        if (toolbar.findViewWithTag<View>("wp_ios27_more_btn") == null) {
            val moreBtn = ImageView(activity).apply {
                tag = "wp_ios27_more_btn"
                setImageDrawable(IosDotsDrawable(palette.textPrimary))
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = "Menu"
                background = makeCircleGlassDrawable(activity, palette, isGreen = false)
                clipToOutline = true
                setOnClickListener {
                    val opened = runCatching {
                        val m = toolbar.javaClass.getMethod("showOverflowMenu")
                        (m.invoke(toolbar) as? Boolean) == true
                    }.getOrDefault(false)
                    if (!opened) {
                        overflowFallback?.performClick()
                    }
                }
            }
            val lp = createToolbarLayoutParams(toolbar, btnSize, btnSize, Gravity.START or Gravity.CENTER_VERTICAL)
            (lp as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = dp(activity, 12f)
                it.marginEnd = dp(activity, 6f)
            }
            toolbar.addView(moreBtn, 0, lp)
        }

        // Right circular bright iOS green (+) button linked to HomeActivity's main FAB
        if (toolbar.findViewWithTag<View>("wp_ios27_plus_btn") == null) {
            val plusBtn = ImageView(activity).apply {
                tag = "wp_ios27_plus_btn"
                setImageDrawable(IosPlusDrawable(if (palette.dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()))
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = "New Chat"
                background = makeCircleGlassDrawable(activity, palette, isGreen = true)
                clipToOutline = true
                setOnClickListener {
                    findHomeFab(activity)?.performClick()
                }
            }
            val lp = createToolbarLayoutParams(toolbar, btnSize, btnSize, Gravity.END or Gravity.CENTER_VERTICAL)
            (lp as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = dp(activity, 6f)
                it.marginEnd = dp(activity, 12f)
            }
            toolbar.addView(plusBtn, lp)
        }

        // Row 2: Large bold SF Pro Display Title below toolbar
        val titleView = ensureLargeTitleView(activity, toolbar, palette, config)
        if (titleView != null) {
            syncLargeTitleWithBottomNav(activity, titleView)
        }

        // Hide bottom-right Android FAB on HomeActivity (its action now lives in the top-right green + button)
        findHomeFab(activity)?.let { fab ->
            fab.alpha = 0f
            fab.scaleX = 0.01f
            fab.scaleY = 0.01f
            fab.elevation = 0f
            fab.translationX = dp(activity, 200f).toFloat()
        }

        installedHeaders.add(activity)
    }

    private fun ensureLargeTitleView(
        activity: Activity,
        toolbar: ViewGroup,
        palette: IosPalette,
        config: Config
    ): TextView? {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val existing = root.findViewWithTag<TextView>("wp_ios27_large_title")
        if (existing != null) {
            existing.setTextColor(palette.textPrimary)
            existing.setBackgroundColor(palette.canvas)
            return existing
        }

        // Walk up from toolbar to find vertical LinearLayout / AppBarLayout
        var childBranch: View = toolbar
        var parent: ViewGroup? = toolbar.parent as? ViewGroup
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent is LinearLayout && parent.orientation == LinearLayout.VERTICAL) {
                break
            }
            childBranch = parent
            parent = parent.parent as? ViewGroup
            depth++
        }
        val targetContainer = parent as? LinearLayout ?: return null
        if (targetContainer.orientation != LinearLayout.VERTICAL) return null

        val idx = targetContainer.indexOfChild(childBranch)
        if (idx < 0) return null

        val defaultTitle = if (Locale.getDefault().language.equals("tr", ignoreCase = true)) {
            "Sohbetler"
        } else {
            "Chats"
        }

        val tv = TextView(activity).apply {
            tag = "wp_ios27_large_title"
            text = defaultTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setTextColor(palette.textPrimary)
            setBackgroundColor(palette.canvas)
            letterSpacing = -0.03f
            setPadding(dp(activity, 16f), dp(activity, 2f), dp(activity, 16f), dp(activity, 8f))
            typeface = if (config.iosTypography) {
                ensureSfProFonts()
                sfBold ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        targetContainer.addView(tv, idx + 1, lp)
        return tv
    }

    private fun syncLargeTitleWithBottomNav(activity: Activity, titleView: TextView) {
        val navId = Utils.getID("bottom_nav", "id")
        val nav = if (navId > 0) activity.findViewById<ViewGroup>(navId) else null
        if (nav == null) return

        val updateText = {
            val activeLabel = findSelectedTabLabel(nav)
            if (!activeLabel.isNullOrBlank() && titleView.text?.toString() != activeLabel) {
                titleView.text = activeLabel
            }
        }
        updateText()

        if (styledViews.add(nav)) {
            nav.viewTreeObserver.addOnGlobalLayoutListener {
                runCatching { updateText() }
            }
        }
    }

    private fun findSelectedTabLabel(group: ViewGroup): String? {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            if (c.isSelected) {
                val label = extractFirstText(c) ?: c.contentDescription?.toString()
                if (!label.isNullOrBlank()) return label.trim()
            }
            if (c is ViewGroup) {
                val nested = findSelectedTabLabel(c)
                if (!nested.isNullOrBlank()) return nested
            }
        }
        return null
    }

    private fun extractFirstText(view: View): String? {
        if (view is TextView) {
            val t = view.text?.toString()?.trim()
            if (!t.isNullOrBlank() && !t.all { it.isDigit() }) return t
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = extractFirstText(view.getChildAt(i))
                if (!found.isNullOrBlank()) return found
            }
        }
        return null
    }

    private fun findActionMenuView(toolbar: ViewGroup): ViewGroup? {
        for (i in 0 until toolbar.childCount) {
            val c = toolbar.getChildAt(i)
            if (c is ViewGroup && c.javaClass.simpleName.contains("ActionMenuView", ignoreCase = true)) {
                return c
            }
        }
        return null
    }

    private fun findHomeFab(activity: Activity): View? {
        val fabId = Utils.getID("fab", "id")
        if (fabId > 0) {
            activity.findViewById<View>(fabId)?.let { return it }
        }
        return null
    }

    private fun createToolbarLayoutParams(toolbar: ViewGroup, w: Int, h: Int, gravity: Int): ViewGroup.LayoutParams {
        return runCatching {
            val cls = Class.forName("${toolbar.javaClass.name}\$LayoutParams")
            val ctor = cls.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            ctor.newInstance(w, h, gravity) as ViewGroup.LayoutParams
        }.getOrElse {
            ViewGroup.MarginLayoutParams(w, h)
        }
    }

    private fun styleIosCircleAction(view: View, palette: IosPalette, isGreen: Boolean) {
        val sz = dp(view.context, 34f)
        view.background = makeCircleGlassDrawable(view.context, palette, isGreen)
        view.clipToOutline = true
        view.minimumWidth = sz
        view.minimumHeight = sz
        view.setPadding(dp(view.context, 6f), dp(view.context, 6f), dp(view.context, 6f), dp(view.context, 6f))
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { mlp ->
            mlp.width = sz
            mlp.height = sz
            mlp.marginStart = dp(view.context, 4f)
            mlp.marginEnd = dp(view.context, 4f)
            view.layoutParams = mlp
        }
    }

    private fun makeCircleGlassDrawable(context: Context, palette: IosPalette, isGreen: Boolean): Drawable {
        val fill = when {
            isGreen -> palette.accentGreen
            palette.dark -> 0xFF1C1C1E.toInt()
            else -> 0xFFF0F0F5.toInt()
        }
        val stroke = when {
            isGreen -> 0x44FFFFFF
            palette.dark -> 0x26FFFFFF
            else -> 0x14000000
        }
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(1, stroke)
        }
        return RippleDrawable(ColorStateList.valueOf(palette.ripple), shape, null)
    }

    private fun walkAndStyle(
        view: View,
        activity: Activity,
        config: Config,
        palette: IosPalette,
        depth: Int
    ) {
        if (depth > 15) return
        val tag = view.tag?.toString().orEmpty()
        if (tag.startsWith("wp_chat_floating") || tag.startsWith("wp_ios27_")) return
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

        // 2) Top bar background
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
        }

        // 3) Search pill (clean iOS frosted capsule)
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
                val div = ColorDrawable(palette.divider)
                view.divider = if (isHome) InsetDrawable(div, dp(view.context, 76f), 0, 0, 0) else div
                view.dividerHeight = 1
            }
        }

        // 5) Contact / Group Info Liquid Glass action tiles & iOS InsetGrouped cards
        if (config.contactGlassCards && (isInfo || isSettings)) {
            if (entry in INFO_ACTION_TILE_NAMES) {
                applyGlassPill(view, palette, 18f)
            } else if (view is ScrollView && view.childCount > 0) {
                val child = view.getChildAt(0)
                if (child is LinearLayout && child.orientation == LinearLayout.VERTICAL) {
                    styleIosGroupedColumn(child, palette)
                }
            }
        }

        // 6) Scroll-to-bottom / non-home FABs
        if (config.glassFabs && isFabOrScrollDown(view, entry, isConv)) {
            if (!(isHome && config.largeHeaderAndSearch && entry == "fab")) {
                styleGlassFab(view, palette, isConv)
            }
        }

        // 7) Enforce SF Pro on already-inflated TextViews
        if (config.iosTypography && view is TextView) {
            resolveSfForTypeface(view.typeface)?.let { sf ->
                view.typeface = sf
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walkAndStyle(view.getChildAt(i), activity, config, palette, depth + 1)
            }
        }
    }

    private fun onViewAttached(view: View, config: Config) {
        val tag = view.tag?.toString().orEmpty()
        if (tag.startsWith("wp_chat_floating") || tag.startsWith("wp_ios27_")) return
        val activity = activityOf(view.context)
        if (isExcludedActivity(activity)) return

        val entry = getEntryName(view)
        if (entry == "bottom_nav") return
        val isConv = isConversationActivity(activity)
        val isHome = isHomeActivity(activity)
        val palette by lazy { resolvePalette(view.context, config) }

        if (config.iosTypography && view is TextView) {
            resolveSfForTypeface(view.typeface)?.let { sf ->
                view.typeface = sf
            }
        }

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
            if (!(isHome && config.largeHeaderAndSearch && entry == "fab")) {
                view.post { runCatching { styleGlassFab(view, palette, isConv) } }
            }
            return
        }

        if (config.largeHeaderAndSearch && !isConv && entry in SEARCH_PILL_NAMES) {
            view.post { runCatching { styleIosSearchPill(view, palette) } }
            return
        }
    }

    private fun styleIosSearchPill(view: View, palette: IosPalette) {
        val fill = if (palette.dark) 0xFF1C1C1E.toInt() else 0xFFEFEFF4.toInt()
        val stroke = if (palette.dark) 0x1EFFFFFF else 0x12000000
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(view.context, 20f).toFloat()
            setColor(fill)
            setStroke(1, stroke)
        }
        view.background = bg
        view.elevation = 0f
        view.clipToOutline = true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val c = view.getChildAt(i)
                if (c !is EditText) c.background = null
            }
        }
    }

    private fun styleGlassFab(view: View, palette: IosPalette, isConv: Boolean) {
        val isScrollBtn = getEntryName(view).contains("scroll", ignoreCase = true) || isConv
        val topCol = if (isScrollBtn) palette.glassTop else palette.accentGreen
        val botCol = if (isScrollBtn) palette.glassBottom else ColorUtils.blendARGB(palette.accentGreen, Color.BLACK, 0.12f)
        val borderCol = if (isScrollBtn) palette.glassBorder else 0x55FFFFFF
        val glass = buildGlassDrawable(
            context = view.context,
            topColor = topCol,
            bottomColor = botCol,
            borderColor = borderCol,
            specularColor = 0x3DFFFFFF,
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

    private fun applyGlassPill(view: View, palette: IosPalette, radiusDp: Float) {
        if (!styledViews.add(view)) return
        val bg = buildGlassDrawable(
            context = view.context,
            topColor = palette.glassTop,
            bottomColor = palette.glassBottom,
            borderColor = palette.glassBorder,
            specularColor = palette.glassSpecular,
            radiusDp = radiusDp,
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
                lp.blurBehindRadius = dp(view.context, 24f)
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
                    setStroke(1, stroke)
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

    private class IosDotsDrawable(color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val r = (bounds.width() * 0.065f).coerceAtLeast(2.5f)
            val gap = r * 3.1f
            canvas.drawCircle(cx - gap, cy, r, paint)
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawCircle(cx + gap, cy, r, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class IosPlusDrawable(color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val arm = bounds.width() * 0.21f
            paint.strokeWidth = (bounds.width() * 0.075f).coerceAtLeast(3.5f)
            canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
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
    }
}
