package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.Spanned
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class MIUIxTheme(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private data class MiuixPalette(
        val dark: Boolean,
        val wallpaperActive: Boolean,
        val canvas: Int,
        val surface: Int,
        val surfaceSecondary: Int,
        val dialogBg: Int,
        val dialogBorder: Int,
        val dialogBtnSecondaryBg: Int,
        val dialogBtnSecondaryFg: Int,
        val dialogDanger: Int,
        val accent: Int,
        val accentSoft: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val chipBg: Int,
        val chipSelectedBg: Int,
        val chipStroke: Int,
        val inputBg: Int,
        val inputBorder: Int,
        val divider: Int,
    )

    private data class Config(
        val accentGreen: Boolean,
        val wallpaperActive: Boolean,
        val miSans: Boolean,
        val bottomDialogs: Boolean,
        val squircleAndInput: Boolean,
    )

    private val idNameCache = ConcurrentHashMap<Int, String>()
    private val styledViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val lockedButtonTextColors = WeakHashMap<TextView, Int>()
    @Volatile private var hasLockedButtonColors = false

    @Volatile private var fontsLoaded = false
    @Volatile private var miRegular: Typeface? = null
    @Volatile private var miMedium: Typeface? = null
    @Volatile private var miSemibold: Typeface? = null
    @Volatile private var miBold: Typeface? = null

    override fun getPluginName(): String = "MIUIxTheme"

    override fun doHook() {
        if (!ThemeCoordinator.isMiuix(xprefs)) return

        val config = loadConfig()

        if (config.miSans) {
            ensureMiSansFonts()
            hookGlobalMiSansTypeface()
        }

        if (config.bottomDialogs) {
            hookLockedDialogButtonColors()
            hookMiuiDialogs(config)
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

    private fun loadConfig(): Config = Config(
        accentGreen = xprefs.getString("miuix_accent_mode", "blue") == "green",
        wallpaperActive = ThemeCoordinator.isMiuixWallpaperActive(xprefs),
        miSans = xprefs.getBoolean("miuix_typography", true),
        bottomDialogs = xprefs.getBoolean("miuix_dialogs", true),
        squircleAndInput = xprefs.getBoolean("miuix_squircle_ui", true),
    )

    private fun palette(context: Context, config: Config): MiuixPalette {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val accent = if (config.accentGreen) {
            if (dark) 0xFF2CE072.toInt() else 0xFF25D366.toInt()
        } else {
            if (dark) 0xFF1F93FF.toInt() else 0xFF0D84FF.toInt()
        }
        val accentSoft = ColorUtils.setAlphaComponent(accent, if (dark) 52 else 34)
        return if (dark) {
            MiuixPalette(
                dark = true,
                wallpaperActive = config.wallpaperActive,
                canvas = 0xFF000000.toInt(),
                surface = if (config.wallpaperActive) 0x66161B24.toInt() else 0xFF17181B.toInt(),
                surfaceSecondary = if (config.wallpaperActive) 0x4D232A36.toInt() else 0xFF23252A.toInt(),
                dialogBg = 0xFF222226.toInt(),
                dialogBorder = 0x1FFFFFFF.toInt(),
                dialogBtnSecondaryBg = 0xFF36383E.toInt(),
                dialogBtnSecondaryFg = 0xFFF3F5F8.toInt(),
                dialogDanger = 0xFFF24848.toInt(),
                accent = accent,
                accentSoft = accentSoft,
                textPrimary = 0xFFF7F8FA.toInt(),
                textSecondary = 0xCCD5DAE3.toInt(),
                chipBg = if (config.wallpaperActive) 0x4AFFFFFF.toInt() else 0xFF1D1F24.toInt(),
                chipSelectedBg = if (config.wallpaperActive) 0x6EFFFFFF.toInt() else accentSoft,
                chipStroke = ColorUtils.setAlphaComponent(accent, 225),
                inputBg = 0xE61B1D22.toInt(),
                inputBorder = 0x33FFFFFF.toInt(),
                divider = 0x1AFFFFFF.toInt(),
            )
        } else {
            MiuixPalette(
                dark = false,
                wallpaperActive = config.wallpaperActive,
                canvas = 0xFFFFFFFF.toInt(),
                surface = if (config.wallpaperActive) 0x80FFFFFF.toInt() else 0xFFF5F6F8.toInt(),
                surfaceSecondary = if (config.wallpaperActive) 0x66F0F4FA.toInt() else 0xFFECEFF4.toInt(),
                dialogBg = 0xFFFFFFFF.toInt(),
                dialogBorder = 0x14000000.toInt(),
                dialogBtnSecondaryBg = 0xFFF0F2F5.toInt(),
                dialogBtnSecondaryFg = 0xFF1A1C20.toInt(),
                dialogDanger = 0xFFFA3E3E.toInt(),
                accent = accent,
                accentSoft = accentSoft,
                textPrimary = 0xFF121417.toInt(),
                textSecondary = 0xB8282E38.toInt(),
                chipBg = if (config.wallpaperActive) 0x70FFFFFF.toInt() else 0xFFF0F2F5.toInt(),
                chipSelectedBg = if (config.wallpaperActive) 0xB8FFFFFF.toInt() else accentSoft,
                chipStroke = accent,
                inputBg = 0xFFF3F5F8.toInt(),
                inputBorder = 0x20000000.toInt(),
                divider = 0x14000000.toInt(),
            )
        }
    }

    private fun ensureMiSansFonts() {
        if (fontsLoaded) return
        synchronized(this) {
            if (fontsLoaded) return
            runCatching {
                val assets = FeatureLoader.moduleContext.assets
                miRegular = runCatching { Typeface.createFromAsset(assets, "fonts/misans_regular.ttf") }.getOrNull()
                miMedium = runCatching { Typeface.createFromAsset(assets, "fonts/misans_medium.ttf") }.getOrNull()
                miSemibold = runCatching { Typeface.createFromAsset(assets, "fonts/misans_semibold.ttf") }.getOrNull()
                miBold = runCatching { Typeface.createFromAsset(assets, "fonts/misans_bold.ttf") }.getOrNull()
            }
            fontsLoaded = true
        }
    }

    private fun resolveMiSans(orig: Typeface?, styleInt: Int): Typeface? {
        ensureMiSansFonts()
        val reg = miRegular ?: return null
        if (orig === miRegular || orig === miMedium || orig === miSemibold || orig === miBold) return null
        if (orig === Typeface.MONOSPACE) return null

        val isBoldStyle = (styleInt and Typeface.BOLD) != 0 || (orig?.isBold == true)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val w = orig?.weight ?: if (isBoldStyle) 700 else 400
            when {
                w >= 700 || isBoldStyle -> miBold ?: miSemibold ?: reg
                w >= 600 -> miSemibold ?: miMedium ?: reg
                w >= 500 -> miMedium ?: reg
                else -> reg
            }
        } else {
            if (isBoldStyle) miBold ?: reg else reg
        }
    }

    private fun hookGlobalMiSansTypeface() {
        runCatching {
            TextView::class.java.resolve().method {
                name = "setTypeface"
            }.hookAll {
                before {
                    if (args.isEmpty()) return@before
                    val orig = args[0] as? Typeface
                    val styleInt = if (args.size >= 2) (args[1] as? Int) ?: 0 else 0
                    val replacement = resolveMiSans(orig, styleInt) ?: return@before
                    args[0] = replacement
                    if (args.size >= 2) {
                        args[1] = styleInt and Typeface.ITALIC
                    }
                }
            }
        }
    }

    private fun hookLockedDialogButtonColors() {
        runCatching {
            TextView::class.java.resolve().method {
                name = "setTextColor"
            }.hookAll {
                before {
                    if (!hasLockedButtonColors || args.isEmpty()) return@before
                    val tv = instance as? TextView ?: return@before
                    val locked = synchronized(lockedButtonTextColors) { lockedButtonTextColors[tv] } ?: return@before
                    when (args[0]) {
                        is Int -> if (args[0] != locked) args[0] = locked
                        is ColorStateList -> args[0] = ColorStateList.valueOf(locked)
                    }
                }
            }
        }
    }

    private fun lockButtonTextColor(tv: TextView, color: Int) {
        synchronized(lockedButtonTextColors) {
            lockedButtonTextColors[tv] = color
            hasLockedButtonColors = true
        }
        if (tv.text is Spanned) {
            tv.text = tv.text.toString()
        }
        tv.setTextColor(ColorStateList.valueOf(color))
    }

    private fun hookMiuiDialogs(config: Config) {
        runCatching {
            Dialog::class.java.resolve().method {
                name = "show"
            }.hookAll {
                after {
                    val dialog = instance as? Dialog ?: return@after
                    val window = dialog.window ?: return@after
                    val decor = window.decorView ?: return@after
                    decor.post {
                        runCatching { styleMiuiDialog(dialog, config) }
                    }
                    decor.postDelayed({
                        runCatching {
                            if (dialog.isShowing) styleMiuiDialog(dialog, config)
                        }
                    }, 70L)
                }
            }
        }
    }

    private fun findDialogView(root: View, ctx: Context, entryName: String): View? {
        val pkgId = ctx.resources.getIdentifier(entryName, "id", ctx.packageName)
        if (pkgId > 0) root.findViewById<View>(pkgId)?.let { return it }
        val sysId = ctx.resources.getIdentifier(entryName, "id", "android")
        if (sysId > 0) root.findViewById<View>(sysId)?.let { return it }
        return null
    }

    private fun isDestructivePrompt(title: String, positiveText: String): Boolean {
        val combined = "$title $positiveText".lowercase(Locale.ROOT)
        val keywords = listOf(
            "sil", "kaldır", "kaldir", "temizle", "engelle", "çıkış", "cikis", "sıfırla", "sifirla",
            "risk", "tehlike", "hata", "uyarı", "uyari",
            "delete", "remove", "clear", "block", "erase", "reset", "uninstall", "report", "discard"
        )
        return keywords.any { it in combined }
    }

    private fun styleMiuiDialog(dialog: Dialog, config: Config) {
        val window = dialog.window ?: return
        val lp = window.attributes ?: return
        if (lp.width == WindowManager.LayoutParams.MATCH_PARENT &&
            lp.height == WindowManager.LayoutParams.MATCH_PARENT
        ) return
        val ctx = dialog.context ?: return
        val p = palette(ctx, config)
        val decor = window.decorView as? ViewGroup ?: return

        val parentPanel = findDialogView(decor, ctx, "parentPanel")
        val bottomSheet = if (parentPanel == null) findDialogView(decor, ctx, "design_bottom_sheet") else null
        if (parentPanel == null && bottomSheet == null) return

        if (bottomSheet != null) {
            val topR = Utils.dipToPixels(28f).toFloat()
            bottomSheet.backgroundTintList = null
            bottomSheet.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(topR, topR, topR, topR, 0f, 0f, 0f, 0f)
                setColor(p.dialogBg)
                setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), p.dialogBorder)
            }
            if (config.miSans) applyMiSansTree(bottomSheet, 0)
            return
        }

        val card = parentPanel!!
        val dm = ctx.resources.displayMetrics
        val sideMargin = Utils.dipToPixels(12f)
        val bottomMargin = Utils.dipToPixels(14f)

        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val targetWidth = (dm.widthPixels - sideMargin * 2).coerceAtLeast(Utils.dipToPixels(280f))
        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        val attrs = window.attributes
        attrs.y = bottomMargin
        attrs.dimAmount = if (p.dark) 0.56f else 0.38f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attrs.blurBehindRadius = Utils.dipToPixels(22f)
            }
        }
        window.attributes = attrs

        listOf(
            "topPanel", "title_template", "contentPanel", "scrollView",
            "customPanel", "custom", "buttonPanel"
        ).forEach { panelName ->
            findDialogView(card, ctx, panelName)?.let { sub ->
                sub.background = null
                sub.backgroundTintList = null
            }
        }

        // Keep horizontal padding = 0 on AlertDialogLayout so children are never clipped on the right
        card.backgroundTintList = null
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Utils.dipToPixels(30f).toFloat()
            setColor(p.dialogBg)
            setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), p.dialogBorder)
        }
        card.setPadding(0, Utils.dipToPixels(22f), 0, Utils.dipToPixels(18f))

        val padH = Utils.dipToPixels(20f)
        findDialogView(card, ctx, "topPanel")?.setPadding(padH, 0, padH, Utils.dipToPixels(6f))
        findDialogView(card, ctx, "contentPanel")?.setPadding(padH, Utils.dipToPixels(4f), padH, Utils.dipToPixels(4f))
        findDialogView(card, ctx, "customPanel")?.setPadding(padH, Utils.dipToPixels(6f), padH, Utils.dipToPixels(6f))

        // Centered MIUI bold title
        val titleTv = findDialogView(card, ctx, "alertTitle") as? TextView
        val titleStr = titleTv?.text?.toString()?.trim().orEmpty()
        if (titleTv != null && titleStr.isNotEmpty()) {
            titleTv.gravity = Gravity.CENTER_HORIZONTAL
            titleTv.textAlignment = View.TEXT_ALIGNMENT_CENTER
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            titleTv.setTextColor(p.textPrimary)
            if (config.miSans) (miSemibold ?: miBold)?.let { titleTv.typeface = it }
            titleTv.layoutParams?.let { tlp ->
                tlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                titleTv.layoutParams = tlp
            }
            (titleTv.parent as? ViewGroup)?.let { tp ->
                tp.setPadding(0, 0, 0, Utils.dipToPixels(4f))
            }
        }

        // Clean readable message body
        val msgTv = (card.findViewById<View>(android.R.id.message) ?: findDialogView(card, ctx, "message")) as? TextView
        if (msgTv != null && msgTv.visibility == View.VISIBLE) {
            val txt = msgTv.text?.toString()?.trim().orEmpty()
            msgTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            msgTv.setTextColor(if (titleStr.isEmpty()) p.textPrimary else p.textSecondary)
            msgTv.setLineSpacing(Utils.dipToPixels(4f).toFloat(), 1f)
            msgTv.setPadding(0, Utils.dipToPixels(4f), 0, Utils.dipToPixels(6f))
            if (txt.length <= 240 && "\n•" !in txt && "\n-" !in txt) {
                msgTv.gravity = Gravity.CENTER_HORIZONTAL
                msgTv.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            if (config.miSans) miRegular?.let { msgTv.typeface = it }
        }

        // Style any EditText inside custom dialogs (e.g. New Chat number input) as MIUI rounded input
        findDialogView(card, ctx, "customPanel")?.let { cp ->
            walkViews(cp, 0, 6) { v ->
                if (v is EditText) {
                    fun inputBg(focused: Boolean) = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = Utils.dipToPixels(16f).toFloat()
                        setColor(p.dialogBtnSecondaryBg)
                        setStroke(
                            Utils.dipToPixels(if (focused) 1.6f else 1f).coerceAtLeast(1),
                            if (focused) p.accent else p.dialogBorder
                        )
                    }
                    v.backgroundTintList = null
                    v.background = inputBg(v.hasFocus())
                    v.setPadding(
                        Utils.dipToPixels(14f), Utils.dipToPixels(12f),
                        Utils.dipToPixels(14f), Utils.dipToPixels(12f)
                    )
                    v.setTextColor(p.textPrimary)
                    v.setHintTextColor(p.textSecondary)
                    val prev = v.onFocusChangeListener
                    v.setOnFocusChangeListener { fv, hasFocus ->
                        prev?.onFocusChange(fv, hasFocus)
                        v.background = inputBg(hasFocus)
                    }
                }
            }
        }

        // Select / choice ListView styling inside dialogs
        val listView = (findDialogView(card, ctx, "select_dialog_listview")
            ?: card.findViewById<View>(android.R.id.list)) as? ListView
        if (listView != null) {
            listView.divider = ColorDrawable(p.divider)
            listView.dividerHeight = Utils.dipToPixels(0.8f).coerceAtLeast(1)
            listView.overScrollMode = View.OVER_SCROLL_NEVER
            listView.setPadding(0, Utils.dipToPixels(4f), 0, Utils.dipToPixels(6f))
            for (i in 0 until listView.childCount) {
                val row = listView.getChildAt(i)
                row.setPadding(
                    Utils.dipToPixels(8f), Utils.dipToPixels(13f),
                    Utils.dipToPixels(8f), Utils.dipToPixels(13f)
                )
                if (row is TextView) {
                    row.setTextColor(p.textPrimary)
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                    if (config.miSans) miRegular?.let { row.typeface = it }
                } else if (row is ViewGroup) {
                    walkViews(row, 0, 4) { ch ->
                        if (ch is TextView) {
                            ch.setTextColor(p.textPrimary)
                            if (config.miSans) miRegular?.let { ch.typeface = it }
                        }
                        if (ch is CompoundButton) {
                            ch.buttonTintList = ColorStateList.valueOf(p.accent)
                        }
                    }
                }
            }
        }

        // Replace WDSButton/AppCompatButton visuals with native MIUI 14 oval pill TextViews
        // forwarding clicks to the original buttons (100% immune to WDSButton blue-on-blue tint)
        val btnNeg = card.findViewById<View>(android.R.id.button2) as? Button
        val btnNeu = card.findViewById<View>(android.R.id.button3) as? Button
        val btnPos = card.findViewById<View>(android.R.id.button1) as? Button

        val visibleButtons = listOfNotNull(btnNeg, btnNeu, btnPos)
            .filter { (it.visibility == View.VISIBLE || it.tag == "miuix_proxied") && !it.text.isNullOrBlank() }

        val buttonPanel = findDialogView(card, ctx, "buttonPanel") as? ViewGroup
        val cardGroup = card as? ViewGroup
        if (visibleButtons.isNotEmpty() && cardGroup != null) {
            buttonPanel?.visibility = View.GONE
            cardGroup.findViewWithTag<View>("miuix_btn_row")?.let { cardGroup.removeView(it) }
            visibleButtons.forEach { it.tag = "miuix_proxied" }

            val posText = btnPos?.text?.toString().orEmpty()
            val destructive = isDestructivePrompt(titleStr, posText)
            val pillHeight = Utils.dipToPixels(46f)
            val gap = Utils.dipToPixels(6f)
            val vGap = Utils.dipToPixels(10f)
            val stackVertically = visibleButtons.size >= 3 ||
                visibleButtons.any { (it.text?.length ?: 0) > 16 }

            val ordered = if (stackVertically) {
                listOfNotNull(btnPos, btnNeu, btnNeg).filter { it in visibleButtons }
            } else {
                listOfNotNull(btnNeg, btnNeu, btnPos).filter { it in visibleButtons }
            }

            val miuiRow = LinearLayout(ctx).apply {
                tag = "miuix_btn_row"
                orientation = if (stackVertically) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            ordered.forEachIndexed { idx, origBtn ->
                val isPrimary = origBtn === btnPos
                val bgColor = when {
                    isPrimary && destructive -> p.dialogDanger
                    isPrimary -> p.accent
                    else -> p.dialogBtnSecondaryBg
                }
                val fgColor = if (isPrimary) Color.WHITE else p.dialogBtnSecondaryFg

                val pill = TextView(ctx).apply {
                    text = origBtn.text?.toString().orEmpty()
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(fgColor)
                    if (config.miSans) (miSemibold ?: miBold)?.let { typeface = it }
                    setPadding(Utils.dipToPixels(16f), 0, Utils.dipToPixels(16f), 0)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 999f
                        setColor(bgColor)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { origBtn.performClick() }
                }

                val plp = if (stackVertically) {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pillHeight).apply {
                        setMargins(0, if (idx == 0) 0 else vGap, 0, 0)
                    }
                } else {
                    LinearLayout.LayoutParams(0, pillHeight, 1f).apply {
                        setMargins(
                            if (idx == 0) 0 else gap,
                            0,
                            if (idx == ordered.lastIndex) 0 else gap,
                            0
                        )
                    }
                }
                miuiRow.addView(pill, plp)
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(padH, Utils.dipToPixels(18f), padH, 0)
            }
            cardGroup.addView(miuiRow, rowLp)
        }

        if (config.miSans) {
            applyMiSansTree(card, 0)
        }
    }

    private fun styleActivityWindow(activity: Activity, config: Config) {
        val p = palette(activity, config)
        val isHome = isHomeActivity(activity)
        val window = activity.window ?: return

        if (isHome && config.wallpaperActive) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            window.statusBarColor = p.canvas
            window.navigationBarColor = p.canvas
        }

        val root = window.decorView ?: return
        root.post {
            runCatching {
                if (isHome) {
                    styleHomeElements(activity, p, config)
                }
                if (config.miSans) {
                    applyMiSansTree(root, 0)
                }
            }
        }
    }

    private fun styleHomeElements(activity: Activity, p: MiuixPalette, config: Config) {
        val toolbarId = Utils.getID("toolbar", "id")
        val toolbar = if (toolbarId > 0) activity.findViewById<ViewGroup>(toolbarId) else null
        if (toolbar != null) {
            if (config.wallpaperActive) {
                toolbar.background = null
            }
            walkViews(toolbar, 0, 5) { v ->
                if (v is TextView && config.miSans) {
                    miBold?.let { v.typeface = it }
                    if (config.wallpaperActive) v.setTextColor(Color.WHITE)
                }
                if (config.wallpaperActive && (v is ImageButton || (v is ImageView && v.isClickable))) {
                    val sz = Utils.dipToPixels(36f)
                    if (v.background !is GradientDrawable) {
                        v.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (p.dark) 0x42FFFFFF.toInt() else 0x52FFFFFF.toInt())
                            setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), 0x48FFFFFF.toInt())
                        }
                    }
                    if (v.minimumWidth < sz) v.minimumWidth = sz
                    if (v.minimumHeight < sz) v.minimumHeight = sz
                }
            }
        }
    }

    private fun onViewAttached(view: View, config: Config) {
        val activity = findActivity(view.context) ?: return
        if (isExcludedActivity(activity)) return
        val p = palette(view.context, config)

        if (config.miSans && view is TextView) {
            val rep = resolveMiSans(view.typeface, view.typeface?.style ?: 0)
            if (rep != null) view.typeface = rep
        }

        val name = idName(view) ?: return

        if (config.squircleAndInput && isFabView(view, name)) {
            styleSquircleFab(view, p)
            return
        }

        if (isFilterChip(view, name)) {
            styleMiuiChip(view, p)
            return
        }

        if (config.squircleAndInput && isChatInputContainer(view, name)) {
            styleMiuiChatInput(view, p)
            return
        }

        if (isSearchPill(name)) {
            styleMiuiSearchBar(view, p)
        }
    }

    private fun isFabView(view: View, name: String): Boolean {
        val cls = view.javaClass.name
        if ("FloatingActionButton" in cls) return true
        return name == "fab" || name == "fab_aux" || name == "new_chat_fab"
    }

    private fun styleSquircleFab(view: View, p: MiuixPalette) {
        val radius = Utils.dipToPixels(19f).toFloat()
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(p.accent)
            setStroke(
                Utils.dipToPixels(1f).coerceAtLeast(1),
                ColorUtils.setAlphaComponent(Color.WHITE, if (p.dark) 56 else 96)
            )
        }
        view.backgroundTintList = null
        view.background = bg
        view.elevation = Utils.dipToPixels(7f).toFloat()
        if (view is ImageView) {
            view.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun isFilterChip(view: View, name: String): Boolean {
        val cls = view.javaClass.simpleName
        if ("Chip" in cls) return true
        return (view is TextView || view is LinearLayout) &&
            ("filter_chip" in name || "inbox_filter" in name || "chip_button" in name)
    }

    private fun styleMiuiChip(view: View, p: MiuixPalette) {
        if (styledViews.contains(view)) return
        styledViews.add(view)

        val corner = Utils.dipToPixels(20f).toFloat()
        val strokePx = Utils.dipToPixels(1.5f).coerceAtLeast(2)

        val selectedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(p.chipSelectedBg)
            setStroke(strokePx, p.chipStroke)
        }
        val normalBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(p.chipBg)
            setStroke(
                Utils.dipToPixels(1f).coerceAtLeast(1),
                if (p.wallpaperActive) 0x45FFFFFF.toInt() else Color.TRANSPARENT
            )
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), selectedBg)
            addState(intArrayOf(android.R.attr.state_checked), selectedBg)
            addState(intArrayOf(), normalBg)
        }
        view.backgroundTintList = null
        view.background = states

        if (view is TextView && p.wallpaperActive) {
            val fg = if (p.dark) Color.WHITE else 0xFF141820.toInt()
            view.setTextColor(fg)
        }
    }

    private fun isChatInputContainer(view: View, name: String): Boolean {
        return name == "input_layout_content" || name == "input_layout" || name == "entry_container"
    }

    private fun styleMiuiChatInput(view: View, p: MiuixPalette) {
        if (styledViews.contains(view)) return
        styledViews.add(view)

        val corner = Utils.dipToPixels(24f).toFloat()
        fun buildDrawable(focused: Boolean): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(p.inputBg)
            setStroke(
                Utils.dipToPixels(if (focused) 1.75f else 1f).coerceAtLeast(1),
                if (focused) p.accent else p.inputBorder
            )
        }

        view.background = buildDrawable(false)
        if (view is ViewGroup) {
            walkViews(view, 0, 5) { child ->
                if (child is EditText) {
                    val prev = child.onFocusChangeListener
                    child.setOnFocusChangeListener { v, hasFocus ->
                        prev?.onFocusChange(v, hasFocus)
                        view.background = buildDrawable(hasFocus)
                    }
                }
            }
        }
    }

    private fun isSearchPill(name: String): Boolean {
        return name == "my_search_bar" || name == "search_bar_container" || name == "search_pill"
    }

    private fun styleMiuiSearchBar(view: View, p: MiuixPalette) {
        if (styledViews.contains(view)) return
        styledViews.add(view)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Utils.dipToPixels(22f).toFloat()
            setColor(p.surface)
            if (p.wallpaperActive) {
                setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), 0x45FFFFFF.toInt())
            }
        }
    }

    private fun applyMiSansTree(v: View, depth: Int) {
        if (depth > 14) return
        if (v is TextView) {
            val rep = resolveMiSans(v.typeface, v.typeface?.style ?: 0)
            if (rep != null) v.typeface = rep
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                applyMiSansTree(v.getChildAt(i), depth + 1)
            }
        }
    }

    private fun walkViews(v: View, depth: Int, maxDepth: Int, fn: (View) -> Unit) {
        if (depth > maxDepth) return
        fn(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                walkViews(v.getChildAt(i), depth + 1, maxDepth, fn)
            }
        }
    }

    private fun idName(v: View): String? {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return null
        return idNameCache.getOrPut(id) {
            runCatching { v.resources.getResourceEntryName(id) }.getOrDefault("")
        }.ifEmpty { null }
    }

    private fun isHomeActivity(activity: Activity): Boolean {
        val n = activity.javaClass.name
        return n.endsWith(".HomeActivity") || n.endsWith(".Main")
    }

    private fun isExcludedActivity(activity: Activity): Boolean {
        val n = activity.javaClass.name
        return "Camera" in n || "MediaView" in n || "StatusPlayback" in n ||
            "Voip" in n || "CallActivity" in n || "Gallery" in n
    }

    private fun findActivity(ctx: Context?): Activity? {
        var c = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
