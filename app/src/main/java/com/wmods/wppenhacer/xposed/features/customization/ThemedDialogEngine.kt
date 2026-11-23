package com.wmods.wppenhacer.xposed.features.customization

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.Locale

class ThemedDialogEngine(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    enum class DialogStyle { NONE, MIUIX, IOS27, MONET }

    data class DialogPalette(
        val style: DialogStyle,
        val dark: Boolean,
        val bottomAnchored: Boolean,
        val cornerDp: Float,
        val buttonCornerDp: Float,
        val dialogBg: Int,
        val dialogBorder: Int,
        val accent: Int,
        val onAccent: Int,
        val secondaryBg: Int,
        val secondaryFg: Int,
        val dangerBg: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val divider: Int,
        val rowBg: Int,
        val dimAmount: Float,
        val blurRadiusDp: Float,
    )

    @Volatile private var miuiFontsLoaded = false
    @Volatile private var miRegular: Typeface? = null
    @Volatile private var miSemibold: Typeface? = null

    @Volatile private var iosFontsLoaded = false
    @Volatile private var sfRegular: Typeface? = null
    @Volatile private var sfSemibold: Typeface? = null

    override fun getPluginName(): String = "ThemedDialogEngine"

    override fun doHook() {
        val style = resolveActiveStyle(xprefs)
        if (style == DialogStyle.NONE) return

        runCatching {
            Dialog::class.java.resolve().method {
                name = "show"
            }.hookAll {
                after {
                    val dialog = instance as? Dialog ?: return@after
                    val window = dialog.window ?: return@after
                    val decor = window.decorView ?: return@after
                    decor.post {
                        runCatching { styleDialog(dialog, style) }
                            .onFailure { Log.e("ThemedDialogEngine", "dialog style err", it) }
                    }
                    decor.postDelayed({
                        runCatching {
                            if (dialog.isShowing) styleDialog(dialog, style)
                        }.onFailure { Log.e("ThemedDialogEngine", "dialog restyle err", it) }
                    }, 75L)
                }
            }
        }
    }

    private fun ensureFonts(style: DialogStyle) {
        when (style) {
            DialogStyle.MIUIX -> {
                if (miuiFontsLoaded || !xprefs.getBoolean("miuix_typography", true)) return
                synchronized(this) {
                    if (miuiFontsLoaded) return
                    runCatching {
                        val assets = FeatureLoader.moduleContext.assets
                        miRegular = runCatching { Typeface.createFromAsset(assets, "fonts/misans_regular.ttf") }.getOrNull()
                        miSemibold = runCatching { Typeface.createFromAsset(assets, "fonts/misans_semibold.ttf") }.getOrNull()
                            ?: runCatching { Typeface.createFromAsset(assets, "fonts/misans_bold.ttf") }.getOrNull()
                    }
                    miuiFontsLoaded = true
                }
            }
            DialogStyle.IOS27 -> {
                if (iosFontsLoaded || !xprefs.getBoolean("ios27_typography", true)) return
                synchronized(this) {
                    if (iosFontsLoaded) return
                    runCatching {
                        val assets = FeatureLoader.moduleContext.assets
                        val files = assets.list("fonts")?.toList().orEmpty()
                        val regFile = files.firstOrNull {
                            val l = it.lowercase(Locale.ROOT)
                            ("sf" in l || "ios" in l) && "regular" in l
                        }
                        val semiFile = files.firstOrNull {
                            val l = it.lowercase(Locale.ROOT)
                            ("sf" in l || "ios" in l) && ("semibold" in l || "bold" in l)
                        }
                        if (regFile != null) sfRegular = runCatching { Typeface.createFromAsset(assets, "fonts/$regFile") }.getOrNull()
                        if (semiFile != null) sfSemibold = runCatching { Typeface.createFromAsset(assets, "fonts/$semiFile") }.getOrNull()
                    }
                    iosFontsLoaded = true
                }
            }
            else -> Unit
        }
    }

    private fun regularFont(style: DialogStyle): Typeface? {
        ensureFonts(style)
        return when (style) {
            DialogStyle.MIUIX -> miRegular
            DialogStyle.IOS27 -> sfRegular
            else -> null
        }
    }

    private fun boldFont(style: DialogStyle): Typeface? {
        ensureFonts(style)
        return when (style) {
            DialogStyle.MIUIX -> miSemibold ?: miRegular
            DialogStyle.IOS27 -> sfSemibold ?: sfRegular
            else -> null
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

    private fun styleDialog(dialog: Dialog, style: DialogStyle) {
        val window = dialog.window ?: return
        val lp = window.attributes ?: return
        if (lp.width == WindowManager.LayoutParams.MATCH_PARENT &&
            lp.height == WindowManager.LayoutParams.MATCH_PARENT
        ) return
        val ctx = dialog.context ?: return
        val p = resolvePalette(ctx, xprefs, style)
        val decor = window.decorView as? ViewGroup ?: return

        val parentPanel = findDialogView(decor, ctx, "parentPanel")
        val bottomSheet = if (parentPanel == null) findDialogView(decor, ctx, "design_bottom_sheet") else null
        if (parentPanel == null && bottomSheet == null) return

        val regTf = regularFont(style)
        val boldTf = boldFont(style)

        if (bottomSheet != null) {
            val topR = Utils.dipToPixels(p.cornerDp).toFloat()
            bottomSheet.backgroundTintList = null
            bottomSheet.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(topR, topR, topR, topR, 0f, 0f, 0f, 0f)
                setColor(p.dialogBg)
                setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), p.dialogBorder)
            }
            if (regTf != null) applyFontTree(bottomSheet, regTf, boldTf, 0)
            return
        }

        val card = parentPanel!!
        val dm = ctx.resources.displayMetrics
        val sideMargin = Utils.dipToPixels(if (p.bottomAnchored) 12f else 22f)
        val bottomMargin = Utils.dipToPixels(14f)

        window.setGravity(
            if (p.bottomAnchored) (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            else Gravity.CENTER
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val targetWidth = (dm.widthPixels - sideMargin * 2).coerceAtLeast(Utils.dipToPixels(280f))
        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        val attrs = window.attributes
        attrs.y = if (p.bottomAnchored) bottomMargin else 0
        attrs.dimAmount = p.dimAmount
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && p.blurRadiusDp > 0f) {
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attrs.blurBehindRadius = Utils.dipToPixels(p.blurRadiusDp)
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

        // Keep horizontal padding = 0 on AlertDialogLayout to prevent right-edge clipping
        card.backgroundTintList = null
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Utils.dipToPixels(p.cornerDp).toFloat()
            setColor(p.dialogBg)
            setStroke(
                Utils.dipToPixels(if (style == DialogStyle.IOS27) 1.2f else 1f).coerceAtLeast(1),
                p.dialogBorder
            )
        }
        card.setPadding(0, Utils.dipToPixels(22f), 0, Utils.dipToPixels(18f))

        val padH = Utils.dipToPixels(20f)
        findDialogView(card, ctx, "topPanel")?.setPadding(padH, 0, padH, Utils.dipToPixels(6f))
        findDialogView(card, ctx, "contentPanel")?.setPadding(padH, Utils.dipToPixels(4f), padH, Utils.dipToPixels(4f))
        findDialogView(card, ctx, "customPanel")?.setPadding(padH, Utils.dipToPixels(6f), padH, Utils.dipToPixels(6f))

        val titleTv = findDialogView(card, ctx, "alertTitle") as? TextView
        val titleStr = titleTv?.text?.toString()?.trim().orEmpty()
        if (titleTv != null && titleStr.isNotEmpty()) {
            val centerTitle = style != DialogStyle.MONET
            titleTv.gravity = if (centerTitle) Gravity.CENTER_HORIZONTAL else Gravity.START
            titleTv.textAlignment = if (centerTitle) View.TEXT_ALIGNMENT_CENTER else View.TEXT_ALIGNMENT_VIEW_START
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (style == DialogStyle.MONET) 20f else 18f)
            titleTv.setTextColor(p.textPrimary)
            boldTf?.let { titleTv.typeface = it }
            titleTv.layoutParams?.let { tlp ->
                tlp.width = ViewGroup.LayoutParams.MATCH_PARENT
                titleTv.layoutParams = tlp
            }
            (titleTv.parent as? ViewGroup)?.setPadding(0, 0, 0, Utils.dipToPixels(4f))
        }

        val msgTv = (card.findViewById<View>(android.R.id.message) ?: findDialogView(card, ctx, "message")) as? TextView
        if (msgTv != null && msgTv.visibility == View.VISIBLE) {
            val txt = msgTv.text?.toString()?.trim().orEmpty()
            msgTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.8f)
            msgTv.setTextColor(if (titleStr.isEmpty()) p.textPrimary else p.textSecondary)
            msgTv.setLineSpacing(Utils.dipToPixels(4f).toFloat(), 1f)
            msgTv.setPadding(0, Utils.dipToPixels(4f), 0, Utils.dipToPixels(6f))
            val shouldCenter = style != DialogStyle.MONET && txt.length <= 260 && "\n•" !in txt && "\n-" !in txt
            msgTv.gravity = if (shouldCenter) Gravity.CENTER_HORIZONTAL else Gravity.START
            msgTv.textAlignment = if (shouldCenter) View.TEXT_ALIGNMENT_CENTER else View.TEXT_ALIGNMENT_VIEW_START
            regTf?.let { msgTv.typeface = it }
        }

        // Style EditText fields inside customPanel (e.g. New Chat dialog)
        findDialogView(card, ctx, "customPanel")?.let { cp ->
            walkViews(cp, 0, 6) { v ->
                if (v is EditText) {
                    fun inputBg(focused: Boolean) = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = Utils.dipToPixels(if (style == DialogStyle.IOS27) 14f else 16f).toFloat()
                        setColor(p.secondaryBg)
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

        // Select / choice ListView inside dialogs
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
                    regTf?.let { row.typeface = it }
                } else if (row is ViewGroup) {
                    walkViews(row, 0, 4) { ch ->
                        if (ch is TextView) {
                            ch.setTextColor(p.textPrimary)
                            regTf?.let { ch.typeface = it }
                        }
                        if (ch is CompoundButton) {
                            ch.buttonTintList = ColorStateList.valueOf(p.accent)
                        }
                    }
                }
            }
        }

        // Native pill buttons inside ButtonBarLayout with explicit width (immune to WDSButton overrides)
        val btnNeg = card.findViewById<View>(android.R.id.button2) as? Button
        val btnNeu = card.findViewById<View>(android.R.id.button3) as? Button
        val btnPos = card.findViewById<View>(android.R.id.button1) as? Button

        val visibleButtons = listOfNotNull(btnNeg, btnNeu, btnPos)
            .filter { (it.visibility == View.VISIBLE || it.tag == "wp_themed_proxied") && !it.text.isNullOrBlank() }

        val buttonPanel = findDialogView(card, ctx, "buttonPanel") as? ViewGroup
        val buttonBar = visibleButtons.firstOrNull()?.parent as? ViewGroup
        if (visibleButtons.isNotEmpty() && buttonBar != null) {
            buttonPanel?.visibility = View.VISIBLE
            buttonPanel?.minimumHeight = 0
            buttonPanel?.setPadding(padH, Utils.dipToPixels(16f), padH, 0)

            buttonBar.visibility = View.VISIBLE
            buttonBar.background = null
            buttonBar.backgroundTintList = null
            buttonBar.minimumHeight = 0
            buttonBar.setPadding(0, 0, 0, 0)
            if (buttonBar is LinearLayout) {
                buttonBar.orientation = LinearLayout.VERTICAL
                buttonBar.gravity = Gravity.CENTER_HORIZONTAL
            }

            buttonBar.findViewWithTag<View>("wp_themed_btn_row")?.let { buttonBar.removeView(it) }
            for (i in 0 until buttonBar.childCount) {
                val ch = buttonBar.getChildAt(i)
                ch.visibility = View.GONE
                if (ch !is Button) {
                    (ch.layoutParams as? LinearLayout.LayoutParams)?.let { slp ->
                        slp.width = 0
                        slp.height = 0
                        slp.weight = 0f
                        ch.layoutParams = slp
                    }
                }
            }
            visibleButtons.forEach { it.tag = "wp_themed_proxied" }

            val posText = btnPos?.text?.toString().orEmpty()
            val destructive = isDestructivePrompt(titleStr, posText)
            val pillHeight = Utils.dipToPixels(if (style == DialogStyle.IOS27) 44f else 46f)
            val gap = Utils.dipToPixels(10f)
            val stackVertically = visibleButtons.size >= 3 ||
                visibleButtons.any { (it.text?.length ?: 0) > 16 }

            val ordered = if (stackVertically) {
                listOfNotNull(btnPos, btnNeu, btnNeg).filter { it in visibleButtons }
            } else {
                listOfNotNull(btnNeg, btnNeu, btnPos).filter { it in visibleButtons }
            }

            val cardW = card.width
            val availW = if (cardW > Utils.dipToPixels(180f)) {
                cardW - padH * 2
            } else {
                (targetWidth - padH * 2 - Utils.dipToPixels(8f)).coerceAtLeast(Utils.dipToPixels(220f))
            }

            val row = LinearLayout(ctx).apply {
                tag = "wp_themed_btn_row"
                orientation = if (stackVertically) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val pillW = if (stackVertically) {
                availW
            } else {
                ((availW - gap * (ordered.size - 1)) / ordered.size).coerceAtLeast(Utils.dipToPixels(84f))
            }

            val btnRadius = if (p.buttonCornerDp >= 500f) 999f else Utils.dipToPixels(p.buttonCornerDp).toFloat()

            ordered.forEachIndexed { idx, origBtn ->
                val isPrimary = origBtn === btnPos
                val bgColor = when {
                    isPrimary && destructive -> p.dangerBg
                    isPrimary -> p.accent
                    else -> p.secondaryBg
                }
                val fgColor = when {
                    isPrimary && destructive -> 0xFFFEFEFE.toInt()
                    isPrimary -> p.onAccent
                    else -> p.secondaryFg
                }

                val pill = TextView(ctx).apply {
                    text = origBtn.text?.toString().orEmpty()
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(fgColor)
                    if (boldTf != null) typeface = boldTf else setTypeface(typeface, Typeface.BOLD)
                    setPadding(Utils.dipToPixels(14f), 0, Utils.dipToPixels(14f), 0)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = btnRadius
                        setColor(bgColor)
                        if (style == DialogStyle.IOS27 && !isPrimary) {
                            setStroke(Utils.dipToPixels(1f).coerceAtLeast(1), p.dialogBorder)
                        }
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { origBtn.performClick() }
                }

                val plp = LinearLayout.LayoutParams(pillW, pillHeight).apply {
                    if (stackVertically) {
                        setMargins(0, if (idx == 0) 0 else gap, 0, 0)
                    } else {
                        setMargins(if (idx == 0) 0 else gap, 0, 0, 0)
                    }
                }
                row.addView(pill, plp)
            }

            buttonBar.addView(
                row,
                LinearLayout.LayoutParams(availW, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            buttonBar.requestLayout()
            card.requestLayout()
        }

        if (regTf != null) {
            applyFontTree(card, regTf, boldTf, 0)
        }
    }

    private fun applyFontTree(v: View, reg: Typeface, bold: Typeface?, depth: Int) {
        if (depth > 12) return
        if (v is TextView && v.typeface !== reg && v.typeface !== bold) {
            val isBold = v.typeface?.isBold == true
            v.typeface = if (isBold) (bold ?: reg) else reg
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                applyFontTree(v.getChildAt(i), reg, bold, depth + 1)
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

    companion object {
        fun resolveActiveStyle(prefs: SharedPreferences): DialogStyle = when {
            ThemeCoordinator.isMiuix(prefs) && prefs.getBoolean("miuix_dialogs", true) -> DialogStyle.MIUIX
            ThemeCoordinator.isIos27(prefs) -> DialogStyle.IOS27
            ThemeCoordinator.isMaterialExpressive(prefs) && prefs.getBoolean("me_dialogs", true) -> DialogStyle.MONET
            ThemeCoordinator.activeTheme(prefs) == ThemeCoordinator.THEME_DEFAULT &&
                prefs.getBoolean("changecolor", false) &&
                prefs.getString("changecolor_mode", "manual") == "monet" -> DialogStyle.MONET
            else -> DialogStyle.NONE
        }

        fun resolvePalette(
            ctx: Context,
            prefs: SharedPreferences,
            forcedStyle: DialogStyle = resolveActiveStyle(prefs),
        ): DialogPalette {
            val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return when (forcedStyle) {
                DialogStyle.MIUIX -> {
                    val green = prefs.getString("miuix_accent_mode", "blue") == "green"
                    val accent = if (green) {
                        if (dark) 0xFF2CE072.toInt() else 0xFF25D366.toInt()
                    } else {
                        if (dark) 0xFF1F93FF.toInt() else 0xFF0D84FF.toInt()
                    }
                    if (dark) {
                        DialogPalette(
                            style = DialogStyle.MIUIX, dark = true, bottomAnchored = true,
                            cornerDp = 30f, buttonCornerDp = 999f,
                            dialogBg = 0xFF222226.toInt(), dialogBorder = 0x1FFFFFFF.toInt(),
                            accent = accent, onAccent = 0xFFFEFEFE.toInt(),
                            secondaryBg = 0xFF36383E.toInt(), secondaryFg = 0xFFF3F5F8.toInt(),
                            dangerBg = 0xFFF24848.toInt(),
                            textPrimary = 0xFFF7F8FA.toInt(), textSecondary = 0xCCD5DAE3.toInt(),
                            divider = 0x1AFFFFFF.toInt(), rowBg = 0xFF2C2E33.toInt(),
                            dimAmount = 0.56f, blurRadiusDp = 22f,
                        )
                    } else {
                        DialogPalette(
                            style = DialogStyle.MIUIX, dark = false, bottomAnchored = true,
                            cornerDp = 30f, buttonCornerDp = 999f,
                            dialogBg = 0xFFFEFEFE.toInt(), dialogBorder = 0x14000000.toInt(),
                            accent = accent, onAccent = 0xFFFEFEFE.toInt(),
                            secondaryBg = 0xFFF0F2F5.toInt(), secondaryFg = 0xFF1A1C20.toInt(),
                            dangerBg = 0xFFFA3E3E.toInt(),
                            textPrimary = 0xFF121417.toInt(), textSecondary = 0xB8282E38.toInt(),
                            divider = 0x14000000.toInt(), rowBg = 0xFFF4F6F9.toInt(),
                            dimAmount = 0.38f, blurRadiusDp = 22f,
                        )
                    }
                }

                DialogStyle.IOS27 -> {
                    if (dark) {
                        DialogPalette(
                            style = DialogStyle.IOS27, dark = true, bottomAnchored = false,
                            cornerDp = 26f, buttonCornerDp = 999f,
                            dialogBg = 0xE61C1E24.toInt(), dialogBorder = 0x38FFFFFF.toInt(),
                            accent = 0xFF0A84FF.toInt(), onAccent = 0xFFFEFEFE.toInt(),
                            secondaryBg = 0x26FFFFFF.toInt(), secondaryFg = 0xFFF5F7FA.toInt(),
                            dangerBg = 0xFFFF453A.toInt(),
                            textPrimary = 0xFFFEFEFE.toInt(), textSecondary = 0xC8D8DEE9.toInt(),
                            divider = 0x24FFFFFF.toInt(), rowBg = 0x1FFFFFFF.toInt(),
                            dimAmount = 0.48f, blurRadiusDp = 28f,
                        )
                    } else {
                        DialogPalette(
                            style = DialogStyle.IOS27, dark = false, bottomAnchored = false,
                            cornerDp = 26f, buttonCornerDp = 999f,
                            dialogBg = 0xEDF7F9FC.toInt(), dialogBorder = 0xC8FFFFFF.toInt(),
                            accent = 0xFF007AFF.toInt(), onAccent = 0xFFFEFEFE.toInt(),
                            secondaryBg = 0x14000000.toInt(), secondaryFg = 0xFF14181F.toInt(),
                            dangerBg = 0xFFFF3B30.toInt(),
                            textPrimary = 0xFF121418.toInt(), textSecondary = 0xB52A303C.toInt(),
                            divider = 0x1A000000.toInt(), rowBg = 0x10000000.toInt(),
                            dimAmount = 0.32f, blurRadiusDp = 28f,
                        )
                    }
                }

                DialogStyle.MONET -> {
                    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            ContextCompat.getColor(
                                ctx,
                                if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
                            )
                        }.getOrDefault(if (dark) 0xFFA8C7FA.toInt() else 0xFF0B57D0.toInt())
                    } else {
                        if (dark) 0xFFA8C7FA.toInt() else 0xFF0B57D0.toInt()
                    }
                    val onAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            ContextCompat.getColor(
                                ctx,
                                if (dark) android.R.color.system_accent1_800 else android.R.color.system_neutral1_0
                            )
                        }.getOrDefault(if (dark) 0xFF062E6F.toInt() else 0xFFFEFEFE.toInt())
                    } else {
                        if (dark) 0xFF062E6F.toInt() else 0xFFFEFEFE.toInt()
                    }
                    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            val base = ContextCompat.getColor(
                                ctx,
                                if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10
                            )
                            ColorUtils.blendARGB(base, accent, if (dark) 0.11f else 0.06f)
                        }.getOrDefault(if (dark) 0xFF1F2229.toInt() else 0xFFF3F6FC.toInt())
                    } else {
                        if (dark) 0xFF1F2229.toInt() else 0xFFF3F6FC.toInt()
                    }
                    val secBg = ColorUtils.blendARGB(surface, accent, if (dark) 0.22f else 0.14f)
                    val rowBg = ColorUtils.blendARGB(surface, accent, if (dark) 0.12f else 0.08f)
                    val border = ColorUtils.setAlphaComponent(accent, if (dark) 56 else 44)
                    DialogPalette(
                        style = DialogStyle.MONET, dark = dark, bottomAnchored = false,
                        cornerDp = 28f, buttonCornerDp = 999f,
                        dialogBg = surface, dialogBorder = border,
                        accent = accent, onAccent = if (onAccent == Color.WHITE) 0xFFFEFEFE.toInt() else onAccent,
                        secondaryBg = secBg, secondaryFg = if (dark) 0xFFE6ECF5.toInt() else 0xFF1A1E26.toInt(),
                        dangerBg = if (dark) 0xFFE55353.toInt() else 0xFFDC3545.toInt(),
                        textPrimary = if (dark) 0xFFE8ECF2.toInt() else 0xFF191C20.toInt(),
                        textSecondary = if (dark) 0xB8C6CEDB.toInt() else 0xB8424854.toInt(),
                        divider = if (dark) 0x1EFFFFFF.toInt() else 0x1A000000.toInt(),
                        rowBg = rowBg,
                        dimAmount = if (dark) 0.54f else 0.36f, blurRadiusDp = 12f,
                    )
                }

                DialogStyle.NONE -> {
                    val accent = if (dark) 0xFF25D366.toInt() else 0xFF128C7E.toInt()
                    DialogPalette(
                        style = DialogStyle.NONE, dark = dark, bottomAnchored = false,
                        cornerDp = 24f, buttonCornerDp = 999f,
                        dialogBg = if (dark) 0xFF1F2C34.toInt() else 0xFFFEFEFE.toInt(),
                        dialogBorder = if (dark) 0x1EFFFFFF.toInt() else 0x14000000.toInt(),
                        accent = accent, onAccent = 0xFFFEFEFE.toInt(),
                        secondaryBg = if (dark) 0xFF2A3942.toInt() else 0xFFF0F2F5.toInt(),
                        secondaryFg = if (dark) 0xFFE9EDEF.toInt() else 0xFF111B21.toInt(),
                        dangerBg = 0xFFE53935.toInt(),
                        textPrimary = if (dark) 0xFFE9EDEF.toInt() else 0xFF111B21.toInt(),
                        textSecondary = if (dark) 0xFF8696A0.toInt() else 0xFF667781.toInt(),
                        divider = if (dark) 0x1EFFFFFF.toInt() else 0x14000000.toInt(),
                        rowBg = if (dark) 0xFF26353E.toInt() else 0xFFF5F6F8.toInt(),
                        dimAmount = 0.45f, blurRadiusDp = 0f,
                    )
                }
            }
        }
    }
}
