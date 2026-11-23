package com.wmods.wppenhacer.xposed.features.others

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivBoolean
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.WppCore.setPrivBoolean
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.features.customization.ThemedDialogEngine
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.CopyOnWriteArraySet

class MenuHome(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        hookMenu()
        val action = xprefs.getBoolean("buttonaction", true)
        val useHub = isHubEnabled(xprefs)

        // Core quick tools always registered so hub stays complete
        addQuickTool { activity -> buildGhostQuickTool(activity) }
        addQuickTool { activity -> buildFreezeLastSeenQuickTool(activity) }
        addQuickTool { activity -> buildDndQuickTool(activity) }
        addQuickTool { activity -> buildRestartQuickTool(activity) }
        addQuickTool { activity -> buildOpenWaeQuickTool(activity) }

        if (useHub) {
            addMenuItem { menu, activity ->
                // Still run auto-reset guards if a toggle was disabled in settings
                enforceDisabledToggleGuards(activity)
                insertQuickToolsHubButton(menu, activity)
            }
        } else {
            addMenuItem { menu, activity -> insertRestartButton(menu, activity, action) }
            addMenuItem { menu, activity -> insertDNDOption(menu, activity, action) }
            addMenuItem { menu, activity -> insertGhostModeOption(menu, activity, action) }
            addMenuItem { menu, activity -> insertFreezeLastSeenOption(menu, activity, action) }
            addMenuItem { menu, activity -> insertOpenWae(menu, activity) }
        }
    }

    private fun enforceDisabledToggleGuards(activity: Activity) {
        if (!xprefs.getBoolean("ghostmode", true) && getPrivBoolean("ghostmode", false)) {
            setPrivBoolean("ghostmode", false)
            Utils.doRestart(activity)
            return
        }
        if (!xprefs.getBoolean("show_dndmode", false) && getPrivBoolean("dndmode", false)) {
            setPrivBoolean("dndmode", false)
            Utils.doRestart(activity)
            return
        }
        if (!xprefs.getBoolean("show_freezeLastSeen", true) && getPrivBoolean("freezelastseen", false)) {
            setPrivBoolean("freezelastseen", false)
            Utils.doRestart(activity)
        }
    }

    private fun anyPrivacyToggleActive(): Boolean {
        return (xprefs.getBoolean("ghostmode", true) && getPrivBoolean("ghostmode", false)) ||
            (xprefs.getBoolean("show_freezeLastSeen", true) && getPrivBoolean("freezelastseen", false)) ||
            (xprefs.getBoolean("show_dndmode", false) && getPrivBoolean("dndmode", false))
    }

    private fun insertQuickToolsHubButton(menu: Menu, activity: Activity) {
        if (menu.findItem(MENU_ID_QUICK_TOOLS) != null) return
        val palette = ThemedDialogEngine.resolvePalette(activity, xprefs)
        val iconTint = DesignUtils.getPrimaryTextColor()
        val dotColor = if (anyPrivacyToggleActive()) palette.accent else null
        val item = menu.add(0, MENU_ID_QUICK_TOOLS, 0, activity.getString(R.string.quick_tools_title))
        item.icon = QuickToolsGridDrawable(iconTint, dotColor)
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        item.setOnMenuItemClickListener {
            showQuickToolsHubDialog(activity)
            true
        }
    }

    private fun showQuickToolsHubDialog(activity: Activity) {
        val entries = quickTools.mapNotNull { runCatching { it.create(activity) }.getOrNull() }
            .sortedBy { it.order }
        if (entries.isEmpty()) return

        val p = ThemedDialogEngine.resolvePalette(activity, xprefs)
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Utils.dipToPixels(4f), 0, Utils.dipToPixels(2f))
        }
        scroll.addView(list)

        var hubDialog: AlertDialogWpp? = null

        entries.forEachIndexed { idx, entry ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                val hPad = Utils.dipToPixels(14f)
                val vPad = Utils.dipToPixels(12f)
                setPadding(hPad, vPad, hPad, vPad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = Utils.dipToPixels(18f).toFloat()
                    setColor(p.rowBg)
                    if (entry.active == true) {
                        setStroke(
                            Utils.dipToPixels(1.2f).coerceAtLeast(1),
                            ColorUtils.setAlphaComponent(p.accent, 165)
                        )
                    }
                }
                setOnClickListener {
                    hubDialog?.dismiss()
                    entry.onClick()
                }
            }

            // Icon pill
            val iconBoxSz = Utils.dipToPixels(38f)
            val iconBox = LinearLayout(activity).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (entry.active == true) ColorUtils.setAlphaComponent(p.accent, 42)
                        else ColorUtils.setAlphaComponent(p.textPrimary, 18)
                    )
                }
            }
            val iv = ImageView(activity).apply {
                val d = entry.icon?.constantState?.newDrawable()?.mutate() ?: entry.icon
                d?.setTint(if (entry.active == true) p.accent else p.textPrimary)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val icSz = Utils.dipToPixels(20f)
            iconBox.addView(iv, LinearLayout.LayoutParams(icSz, icSz))
            row.addView(iconBox, LinearLayout.LayoutParams(iconBoxSz, iconBoxSz))

            // Title & short description
            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Utils.dipToPixels(12f), 0, Utils.dipToPixels(8f), 0)
            }
            val tvTitle = TextView(activity).apply {
                text = entry.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(p.textPrimary)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            textCol.addView(tvTitle)
            if (!entry.subtitle.isNullOrBlank()) {
                val tvSub = TextView(activity).apply {
                    text = entry.subtitle
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.2f)
                    setTextColor(p.textSecondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                textCol.addView(tvSub)
            }
            row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // Live status badge or chevron
            if (entry.active != null) {
                val isOn = entry.active
                val badge = TextView(activity).apply {
                    text = activity.getString(
                        if (isOn) R.string.quick_tools_status_on else R.string.quick_tools_status_off
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (isOn) p.onAccent else p.textSecondary)
                    setPadding(
                        Utils.dipToPixels(10f), Utils.dipToPixels(4f),
                        Utils.dipToPixels(10f), Utils.dipToPixels(4f)
                    )
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 999f
                        setColor(if (isOn) p.accent else p.secondaryBg)
                    }
                }
                row.addView(badge, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            } else {
                val chev = TextView(activity).apply {
                    text = "›"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTextColor(p.textSecondary)
                    setPadding(Utils.dipToPixels(4f), 0, Utils.dipToPixels(2f), 0)
                }
                row.addView(chev)
            }

            val rlp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (idx > 0) topMargin = Utils.dipToPixels(8f)
            }
            list.addView(row, rlp)
        }

        hubDialog = AlertDialogWpp(activity)
            .setTitle(activity.getString(R.string.quick_tools_title))
            .setView(scroll)
            .setNegativeButton(activity.getString(R.string.quick_tools_close)) { d, _ -> d?.dismiss() }
        hubDialog?.show()
    }

    // --- Quick Tool Builders ---

    private fun buildGhostQuickTool(activity: Activity): QuickToolEntry? {
        if (!xprefs.getBoolean("ghostmode", true)) return null
        val active = getPrivBoolean("ghostmode", false)
        return QuickToolEntry(
            id = "ghost_mode",
            order = 10,
            title = activity.getString(R.string.ghost_mode),
            subtitle = activity.getString(R.string.quick_tools_ghost_sum),
            icon = activity.getDrawable(if (active) R.drawable.ghost_enabled else R.drawable.ghost_disabled),
            active = active,
            onClick = { showGhostModeDialog(activity) }
        )
    }

    private fun buildFreezeLastSeenQuickTool(activity: Activity): QuickToolEntry? {
        if (!xprefs.getBoolean("show_freezeLastSeen", true)) return null
        val active = getPrivBoolean("freezelastseen", false)
        return QuickToolEntry(
            id = "freeze_last_seen",
            order = 20,
            title = activity.getString(R.string.freezelastseen_title),
            subtitle = activity.getString(R.string.quick_tools_freeze_sum),
            icon = Utils.application.getDrawable(if (active) R.drawable.eye_disabled else R.drawable.eye_enabled),
            active = active,
            onClick = { showFreezeLastSeenDialog(activity) }
        )
    }

    private fun buildDndQuickTool(activity: Activity): QuickToolEntry? {
        if (!xprefs.getBoolean("show_dndmode", false)) return null
        val active = getPrivBoolean("dndmode", false)
        return QuickToolEntry(
            id = "dnd_mode",
            order = 30,
            title = activity.getString(R.string.dnd_mode_title),
            subtitle = activity.getString(R.string.quick_tools_dnd_sum),
            icon = Utils.application.getDrawable(if (active) R.drawable.airplane_enabled else R.drawable.airplane_disabled),
            active = active,
            onClick = { showDndDialog(activity) }
        )
    }

    private fun buildRestartQuickTool(activity: Activity): QuickToolEntry? {
        if (!xprefs.getBoolean("restartbutton", true)) return null
        return QuickToolEntry(
            id = "restart_wa",
            order = 80,
            title = activity.getString(R.string.restart_whatsapp),
            subtitle = activity.getString(R.string.quick_tools_restart_sum),
            icon = activity.getDrawable(R.drawable.refresh),
            active = null,
            onClick = { showRestartDialog(activity) }
        )
    }

    private fun buildOpenWaeQuickTool(activity: Activity): QuickToolEntry? {
        if (!xprefs.getBoolean("open_wae", true)) return null
        return QuickToolEntry(
            id = "open_wae",
            order = 90,
            title = activity.getString(R.string.app_name),
            subtitle = activity.getString(R.string.quick_tools_open_settings_sum),
            icon = DesignUtils.getDrawableByName("ic_settings"),
            active = null,
            onClick = { openWhatsPosedApp(activity) }
        )
    }

    // --- Unified Status & Description Dialogs (used both from Hub and Legacy Icons) ---

    private fun showToggleStatusDialog(
        activity: Activity,
        baseTitle: String,
        description: String,
        currentlyOn: Boolean,
        prefKey: String,
    ) {
        val statusBadge = activity.getString(
            if (currentlyOn) R.string.quick_tools_status_on else R.string.quick_tools_status_off
        )
        val stateLine = activity.getString(
            if (currentlyOn) R.string.quick_tools_current_state_on else R.string.quick_tools_current_state_off
        )
        val fullMsg = "$stateLine\n\n$description"
        val title = "$baseTitle • $statusBadge"

        val dialog = AlertDialogWpp(activity)
            .setTitle(title)
            .setMessage(fullMsg)

        if (currentlyOn) {
            // Primary button performs the state change (Disable); secondary keeps enabled cleanly
            dialog
                .setNegativeButton(activity.getString(R.string.enable)) { d, _ -> d?.dismiss() }
                .setPositiveButton(activity.getString(R.string.disable)) { _, _ ->
                    setPrivBoolean(prefKey, false)
                    Utils.doRestart(activity)
                }
        } else {
            // Primary button performs the state change (Enable); secondary keeps disabled cleanly
            dialog
                .setNegativeButton(activity.getString(R.string.disable)) { d, _ -> d?.dismiss() }
                .setPositiveButton(activity.getString(R.string.enable)) { _, _ ->
                    setPrivBoolean(prefKey, true)
                    Utils.doRestart(activity)
                }
        }
        dialog.show()
    }

    private fun showGhostModeDialog(activity: Activity) {
        val active = getPrivBoolean("ghostmode", false)
        showToggleStatusDialog(
            activity = activity,
            baseTitle = activity.getString(R.string.ghost_mode),
            description = activity.getString(R.string.ghost_mode_message),
            currentlyOn = active,
            prefKey = "ghostmode",
        )
    }

    private fun showFreezeLastSeenDialog(activity: Activity) {
        val active = getPrivBoolean("freezelastseen", false)
        showToggleStatusDialog(
            activity = activity,
            baseTitle = activity.getString(R.string.freezelastseen_title),
            description = activity.getString(R.string.freezelastseen_message),
            currentlyOn = active,
            prefKey = "freezelastseen",
        )
    }

    private fun showDndDialog(activity: Activity) {
        val active = getPrivBoolean("dndmode", false)
        showToggleStatusDialog(
            activity = activity,
            baseTitle = activity.getString(R.string.dnd_mode_title),
            description = activity.getString(R.string.dnd_message),
            currentlyOn = active,
            prefKey = "dndmode",
        )
    }

    private fun showRestartDialog(activity: Activity) {
        AlertDialogWpp(activity)
            .setTitle(activity.getString(R.string.restart_whatsapp))
            .setMessage(activity.getString(R.string.quick_tools_restart_message))
            .setNegativeButton(activity.getString(R.string.cancel)) { d, _ -> d?.dismiss() }
            .setPositiveButton(activity.getString(R.string.quick_tools_restart_action)) { _, _ ->
                Utils.doRestart(activity)
            }
            .show()
    }

    private fun openWhatsPosedApp(activity: Activity) {
        try {
            val intent = activity.packageManager.getLaunchIntentForPackage(
                BuildConfig.APPLICATION_ID
            )
            intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Utils.showToast(e.message)
        }
    }

    // --- Legacy Individual Toolbar Buttons (when Quick Tools Hub is turned OFF) ---

    private fun insertOpenWae(menu: Menu, activity: Activity) {
        val waeMenu = xprefs.getBoolean("open_wae", true)
        if (!waeMenu) return
        val itemMenu = menu.add(0, 0, 9999, " " + activity.getString(R.string.app_name))
        val iconDraw = DesignUtils.getDrawableByName("ic_settings")
        iconDraw?.setTint(-0x796960)
        itemMenu.icon = iconDraw
        itemMenu.setOnMenuItemClickListener {
            openWhatsPosedApp(activity)
            true
        }
    }

    private fun insertGhostModeOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val ghostmode = getPrivBoolean("ghostmode", false)
        if (!xprefs.getBoolean("ghostmode", true)) {
            if (ghostmode) {
                setPrivBoolean("ghostmode", false)
                Utils.doRestart(activity)
            }
            return
        }
        val itemMenu = menu.add(0, 0, 0, R.string.ghost_mode)
        val iconDraw =
            activity.getDrawable(if (ghostmode) R.drawable.ghost_enabled else R.drawable.ghost_disabled)
        if (iconDraw != null) {
            iconDraw.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            itemMenu.icon = iconDraw
        }
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        itemMenu.setOnMenuItemClickListener {
            showGhostModeDialog(activity)
            true
        }
    }

    private fun insertRestartButton(menu: Menu, activity: Activity, newSettings: Boolean) {
        if (!xprefs.getBoolean("restartbutton", true)) return
        val iconDraw = activity.getDrawable(R.drawable.refresh)
        iconDraw?.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
        val itemMenu = menu.add(0, 0, 0, R.string.restart_whatsapp).setIcon(iconDraw)
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        itemMenu.setOnMenuItemClickListener {
            showRestartDialog(activity)
            true
        }
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref")
    private fun insertDNDOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val dndmode = getPrivBoolean("dndmode", false)
        if (!xprefs.getBoolean("show_dndmode", false)) {
            if (dndmode) {
                setPrivBoolean("dndmode", false)
                Utils.doRestart(activity)
            }
            return
        }
        val item = menu.add(0, 0, 0, activity.getString(R.string.dnd_mode_title))
        val drawable = Utils.application
            .getDrawable(if (dndmode) R.drawable.airplane_enabled else R.drawable.airplane_disabled)
        if (drawable != null) {
            drawable.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            item.icon = drawable
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            showDndDialog(activity)
            true
        }
    }

    private fun insertFreezeLastSeenOption(menu: Menu, activity: Activity, newSettings: Boolean) {
        val freezelastseen = getPrivBoolean("freezelastseen", false)
        if (!xprefs.getBoolean("show_freezeLastSeen", true)) {
            if (freezelastseen) {
                setPrivBoolean("freezelastseen", false)
                Utils.doRestart(activity)
            }
            return
        }

        val item = menu.add(0, 0, 0, activity.getString(R.string.freezelastseen_title))
        val drawable = Utils.application
            .getDrawable(if (freezelastseen) R.drawable.eye_disabled else R.drawable.eye_enabled)
        if (drawable != null) {
            drawable.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
            item.icon = drawable
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            showFreezeLastSeenDialog(activity)
            true
        }
    }

    private fun hookMenu() {
        homeActivityClass.resolve().firstMethod {
            name = "onCreateOptionsMenu"
            superclass()
            parameters(Menu::class.java)
        }.hook {
            after {
                val menu = args[0] as Menu
                val activity = instance as Activity
                for (menuItem in menuItems) {
                    menuItem.addMenu(menu, activity)
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Menu Home"
    }

    fun interface HomeMenuItem {
        fun addMenu(menu: Menu, activity: Activity)
    }

    data class QuickToolEntry(
        val id: String,
        val order: Int,
        val title: String,
        val subtitle: String? = null,
        val icon: Drawable? = null,
        val active: Boolean? = null,
        val onClick: () -> Unit,
    )

    fun interface QuickToolProvider {
        fun create(activity: Activity): QuickToolEntry?
    }

    private class QuickToolsGridDrawable(
        private val strokeColor: Int,
        private val activeDotColor: Int?,
    ) : Drawable() {
        private val sizePx = Utils.dipToPixels(24f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = Utils.dipToPixels(1.8f).toFloat().coerceAtLeast(2f)
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activeDotColor ?: Color.TRANSPARENT
            style = Paint.Style.FILL
        }
        private val rect = RectF()

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat().takeIf { it > 0f } ?: sizePx.toFloat()
            val h = b.height().toFloat().takeIf { it > 0f } ?: sizePx.toFloat()
            val pad = w * 0.14f
            val gap = w * 0.12f
            val cell = (w - pad * 2f - gap) / 2f
            val r = cell * 0.30f

            val x0 = b.left + pad
            val y0 = b.top + pad
            val x1 = x0 + cell + gap
            val y1 = y0 + cell + gap

            rect.set(x0, y0, x0 + cell, y0 + cell); canvas.drawRoundRect(rect, r, r, paint)
            rect.set(x1, y0, x1 + cell, y0 + cell); canvas.drawRoundRect(rect, r, r, paint)
            rect.set(x0, y1, x0 + cell, y1 + cell); canvas.drawRoundRect(rect, r, r, paint)
            rect.set(x1, y1, x1 + cell, y1 + cell); canvas.drawRoundRect(rect, r, r, paint)

            if (activeDotColor != null) {
                val dotR = w * 0.16f
                canvas.drawCircle(b.right - dotR * 0.9f, b.top + dotR * 0.9f, dotR, dotPaint)
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; dotPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        const val MENU_ID_QUICK_TOOLS = 0x57505154
        private val menuItems = CopyOnWriteArraySet<HomeMenuItem>()
        private val quickTools = CopyOnWriteArraySet<QuickToolProvider>()

        @JvmStatic
        fun isHubEnabled(prefs: SharedPreferences): Boolean =
            prefs.getBoolean("quick_tools_hub", true)

        @JvmStatic
        fun addMenuItem(homeMenuItem: HomeMenuItem) {
            menuItems += homeMenuItem
        }

        @JvmStatic
        fun addQuickTool(provider: QuickToolProvider) {
            quickTools += provider
        }
    }
}
