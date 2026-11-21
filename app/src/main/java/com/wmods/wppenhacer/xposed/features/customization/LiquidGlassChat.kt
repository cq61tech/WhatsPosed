package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
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
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tanh

class LiquidGlassChat(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private class ChatGlassHolder {
        val drawables = mutableListOf<LiquidIslandDrawable>()
        var elasticAttached = false
        var listListenerAttached = false
        var topFloated = false
        var bottomFloated = false
    }

    private val holders = WeakHashMap<Activity, ChatGlassHolder>()
    private val elasticViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getPluginName(): String = "LiquidGlassChat"

    override fun doHook() {
        val enableGlass = xprefs.getBoolean("liquid_glass_chat", false)
        val enableElastic = xprefs.getBoolean("elastic_message_press", false)
        if (!enableGlass && !enableElastic) return

        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName != "Conversation") return@addListenerActivity
            if (state == WppCore.ActivityChangeState.ChangeType.STARTED ||
                state == WppCore.ActivityChangeState.ChangeType.RESUMED
            ) {
                activity.runOnUiThread {
                    val decor = activity.window?.decorView ?: return@runOnUiThread
                    val applyPass = {
                        runCatching {
                            if (enableGlass) applyLiquidGlassChat(activity)
                            if (enableElastic) attachElasticMessages(activity)
                        }
                    }
                    decor.post { applyPass() }
                    decor.postDelayed({ applyPass() }, 260L)
                    decor.postDelayed({ applyPass() }, 680L)
                }
            }
        }
    }

    private fun isDark(activity: Activity): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(activity: Activity, v: Float): Int =
        (v * activity.resources.displayMetrics.density).toInt()

    private fun dpf(activity: Activity, v: Float): Float =
        v * activity.resources.displayMetrics.density

    private fun findByName(activity: Activity, name: String): View? {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        return if (id != 0) activity.findViewById(id) else null
    }

    private fun holderOf(activity: Activity): ChatGlassHolder =
        holders.getOrPut(activity) { ChatGlassHolder() }

    private fun ancestorsOf(v: View): List<View> {
        val out = mutableListOf<View>()
        var cur: View? = v
        while (cur != null) {
            out.add(cur)
            cur = cur.parent as? View
        }
        return out
    }

    private fun findLca(a: View, b: View): ViewGroup? {
        val setA = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        for (v in ancestorsOf(a)) setA.add(v)
        for (v in ancestorsOf(b)) {
            if (v in setA && v is ViewGroup) return v
        }
        return null
    }

    private fun directChildUnder(parent: ViewGroup, descendant: View): View? {
        var cur: View? = descendant
        while (cur != null && cur.parent !== parent) {
            cur = cur.parent as? View
        }
        return cur
    }

    private fun clearChainTransparent(from: View, upToInclusive: View) {
        var cur: View? = from
        while (cur != null) {
            cur.setBackgroundColor(Color.TRANSPARENT)
            cur.elevation = 0f
            if (cur is ViewGroup) {
                cur.clipChildren = false
                cur.clipToPadding = false
            }
            if (cur === upToInclusive) break
            cur = cur.parent as? View
        }
    }

    private fun pullBranchToTop(lca: ViewGroup, branch: View, topH: Int) {
        lca.clipChildren = false
        lca.clipToPadding = false
        if (branch is ViewGroup) {
            branch.clipChildren = false
            branch.clipToPadding = false
        }
        val lp = branch.layoutParams ?: return
        when (lp) {
            is RelativeLayout.LayoutParams -> {
                lp.removeRule(RelativeLayout.BELOW)
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                lp.topMargin = 0
                branch.layoutParams = lp
            }
            is LinearLayout.LayoutParams -> {
                if (lp.topMargin >= 0) {
                    lp.topMargin = -topH
                    branch.layoutParams = lp
                }
            }
            is FrameLayout.LayoutParams -> {
                if (lp.topMargin > 0) {
                    lp.topMargin = 0
                    branch.layoutParams = lp
                }
            }
            else -> {
                // Handle ConstraintLayout.LayoutParams via reflection if present
                val handledConstraint = runCatching {
                    val cls = lp.javaClass
                    if (cls.name.contains("ConstraintLayout")) {
                        val fTopToBot = cls.getField("topToBottom")
                        val fTopToTop = cls.getField("topToTop")
                        fTopToBot.setInt(lp, -1)
                        fTopToTop.setInt(lp, 0)
                        if (lp is ViewGroup.MarginLayoutParams) lp.topMargin = 0
                        branch.layoutParams = lp
                        true
                    } else false
                }.getOrDefault(false)
                if (!handledConstraint && lp is ViewGroup.MarginLayoutParams && lp.topMargin >= 0) {
                    lp.topMargin = -topH
                    branch.layoutParams = lp
                }
            }
        }
    }

    private fun applyLiquidGlassChat(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val dark = isDark(activity)
        val holder = holderOf(activity)

        val toolbar = findByName(activity, "toolbar") as? ViewGroup
        val header = findByName(activity, "header") as? ViewGroup
        val toolbarContainer = findByName(activity, "toolbar_container") as? ViewGroup
        val topAnchor: View? = header ?: toolbarContainer ?: toolbar

        val list = activity.findViewById<View>(android.R.id.list)
        val entry = findByName(activity, "entry") as? EditText
        val inputCard = findByName(activity, "input_layout_content")
            ?: findByName(activity, "input_layout")
            ?: entry?.parent as? View
        val footer = findByName(activity, "footer") as? ViewGroup
            ?: inputCard?.parent as? ViewGroup

        val topLca = if (topAnchor != null && list != null) findLca(topAnchor, list) else null
        val topBranch = if (topLca != null && topAnchor != null) directChildUnder(topLca, topAnchor) else topAnchor
        val listBranch = if (topLca != null && list != null) directChildUnder(topLca, list) else list

        // --- FLOAT ENTIRE WALLPAPER + LIST CONTAINER BEHIND TOP BAR & RAISE TOP BAR Z ---
        if (topLca != null && topBranch != null && listBranch != null && topBranch !== listBranch) {
            clearChainTransparent(toolbar ?: topAnchor!!, topBranch)
            topBranch.translationZ = dpf(activity, 28f)
            topBranch.bringToFront()

            val topH = topBranch.height.takeIf { it > 0 }
                ?: topAnchor?.height?.takeIf { it > 0 }
                ?: dp(activity, 62f)

            if (!holder.topFloated) {
                holder.topFloated = true
                pullBranchToTop(topLca, listBranch, topH)
                // Also pull any sibling wallpaper view in topLca up to the top
                for (i in 0 until topLca.childCount) {
                    val c = topLca.getChildAt(i)
                    if (c === topBranch || c === listBranch) continue
                    val entryName = runCatching {
                        if (c.id != View.NO_ID && c.id > 0) c.resources.getResourceEntryName(c.id) else ""
                    }.getOrDefault("")
                    if (entryName.contains("wallpaper", ignoreCase = true) ||
                        entryName.contains("background", ignoreCase = true) ||
                        c is ImageView
                    ) {
                        pullBranchToTop(topLca, c, topH)
                    }
                }
                if (list is ViewGroup) list.clipToPadding = false
                if (list != null) {
                    list.setPadding(
                        list.paddingLeft,
                        maxOf(list.paddingTop, topH + dp(activity, 8f)),
                        list.paddingRight,
                        list.paddingBottom
                    )
                }
            }
        } else if (topAnchor != null) {
            topAnchor.setBackgroundColor(Color.TRANSPARENT)
            topAnchor.translationZ = dpf(activity, 28f)
            topAnchor.bringToFront()
        }

        val topBackdrop = topLca ?: findBackdropRoot(activity, content) ?: content
        val topExcluded = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        topBranch?.let { topExcluded.add(it) }
        topAnchor?.let { topExcluded.add(it) }
        toolbar?.let { topExcluded.add(it) }

        // --- TOP BAR: compact separated glass islands so contact name has plenty of room ---
        toolbar?.let { tb ->
            tb.setBackgroundColor(Color.TRANSPARENT)
            tb.elevation = 0f
            tb.clipChildren = false
            tb.clipToPadding = false
            runCatching {
                tb.javaClass.getMethod(
                    "setContentInsetsAbsolute",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(tb, 0, 0)
            }
            runCatching {
                tb.javaClass.getMethod("setContentInsetStartWithNavigation", Int::class.javaPrimitiveType)
                    .invoke(tb, 0)
            }
            tb.setPadding(dp(activity, 6f), dp(activity, 4f), dp(activity, 6f), dp(activity, 4f))

            // 1) Compact Back + Avatar pill
            val backView = findByName(activity, "back") ?: findDirectNavButton(tb)
            if (backView != null && backView.visibility == View.VISIBLE) {
                if (backView is ViewGroup) {
                    for (i in 0 until backView.childCount) {
                        val ch = backView.getChildAt(i)
                        val lp = ch.layoutParams as? ViewGroup.MarginLayoutParams
                        if (lp != null) {
                            lp.marginStart = min(lp.marginStart, dp(activity, 2f))
                            lp.marginEnd = min(lp.marginEnd, dp(activity, 2f))
                            ch.layoutParams = lp
                        }
                    }
                }
                styleIsland(
                    activity = activity,
                    view = backView,
                    backdropRoot = topBackdrop,
                    excludeSet = topExcluded,
                    holder = holder,
                    isCircle = backView.width in 1..(dp(activity, 52f)),
                    dark = dark,
                    radiusDp = 22f,
                    padHDp = 4f,
                    padVDp = 3f
                )
            }

            // 2) Roomy Contact Name pill
            val contactPill = findByName(activity, "conversation_contact")
                ?: findByName(activity, "conversation_contact_name")?.parent as? View
            if (contactPill != null && contactPill !== tb) {
                styleIsland(
                    activity = activity,
                    view = contactPill,
                    backdropRoot = topBackdrop,
                    excludeSet = topExcluded,
                    holder = holder,
                    isCircle = false,
                    dark = dark,
                    radiusDp = 22f,
                    padHDp = 10f,
                    padVDp = 4f
                )
                setHorizontalMargins(contactPill, dp(activity, 5f), dp(activity, 5f))
                if (contactPill.minimumWidth < dp(activity, 110f)) {
                    contactPill.minimumWidth = dp(activity, 110f)
                }
            }

            // 3) Compact Right Action Menu pill (tighten child paddings so name isn't squeezed)
            val actionMenu = findActionMenuView(tb)
            if (actionMenu != null && actionMenu.childCount > 0) {
                val tightPad = dp(activity, 4f)
                val tightMinW = dp(activity, 34f)
                for (i in 0 until actionMenu.childCount) {
                    val item = actionMenu.getChildAt(i)
                    item.minimumWidth = min(item.minimumWidth, tightMinW)
                    item.setPadding(
                        min(item.paddingLeft, tightPad),
                        item.paddingTop,
                        min(item.paddingRight, tightPad),
                        item.paddingBottom
                    )
                }
                styleIsland(
                    activity = activity,
                    view = actionMenu,
                    backdropRoot = topBackdrop,
                    excludeSet = topExcluded,
                    holder = holder,
                    isCircle = false,
                    dark = dark,
                    radiusDp = 22f,
                    padHDp = 3f,
                    padVDp = 2f
                )
            }
        }

        // --- BOTTOM COMPOSER (separate backdropRoot = footer's parent so it captures wallpaper + list) ---
        val bottomBackdrop = (footer?.parent as? ViewGroup) ?: findBackdropRoot(activity, content) ?: content
        val bottomExcluded = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())

        footer?.let { ft ->
            bottomExcluded.add(ft)
            ft.setBackgroundColor(Color.TRANSPARENT)
            ft.elevation = 0f
            ft.translationZ = dpf(activity, 18f)
            ft.clipChildren = false
            ft.clipToPadding = false
            if (!holder.bottomFloated && list != null) {
                holder.bottomFloated = true
                val botH = ft.height.takeIf { it > 0 } ?: dp(activity, 62f)
                val lp = list.layoutParams
                if (lp is RelativeLayout.LayoutParams) {
                    lp.removeRule(RelativeLayout.ABOVE)
                    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    list.layoutParams = lp
                }
                if (list is ViewGroup) list.clipToPadding = false
                list.setPadding(
                    list.paddingLeft,
                    list.paddingTop,
                    list.paddingRight,
                    maxOf(list.paddingBottom, botH + dp(activity, 10f))
                )
            }
        }

        if (inputCard != null) {
            bottomExcluded.add(inputCard)
            styleIsland(
                activity = activity,
                view = inputCard,
                backdropRoot = bottomBackdrop,
                excludeSet = bottomExcluded,
                holder = holder,
                isCircle = false,
                dark = dark,
                radiusDp = 26f,
                padHDp = 6f,
                padVDp = 2f
            )
        }

        val actionBtnIds = listOf("voice_note_btn", "send", "input_circle_button", "send_container")
        for (btnName in actionBtnIds) {
            val btn = findByName(activity, btnName) ?: continue
            if (btn === inputCard) continue
            bottomExcluded.add(btn)
            styleIsland(
                activity = activity,
                view = btn,
                backdropRoot = bottomBackdrop,
                excludeSet = bottomExcluded,
                holder = holder,
                isCircle = true,
                dark = dark,
                radiusDp = 24f,
                padHDp = 0f,
                padVDp = 0f,
                preserveAccentTint = true
            )
        }

        if (list != null && !holder.listListenerAttached) {
            holder.listListenerAttached = true
            list.viewTreeObserver.addOnScrollChangedListener {
                for (d in holder.drawables) d.requestCapture()
            }
        }
        for (d in holder.drawables) d.requestCapture()
    }

    private fun findBackdropRoot(activity: Activity, content: ViewGroup): ViewGroup? {
        val candidates = listOf("conversation_background", "root_view", "main_layout")
        for (name in candidates) {
            val v = findByName(activity, name)
            if (v is ViewGroup && v.width > 0 && v.height > 0) return v
        }
        val list = activity.findViewById<View>(android.R.id.list)
        var p = list?.parent as? ViewGroup
        while (p != null && p.parent is ViewGroup && p.parent !== content) {
            if (p.height >= content.height * 0.8f) return p
            p = p.parent as? ViewGroup
        }
        return content.getChildAt(0) as? ViewGroup ?: content
    }

    private fun findDirectNavButton(toolbar: ViewGroup): View? {
        for (i in 0 until toolbar.childCount) {
            val c = toolbar.getChildAt(i)
            if (c is ImageButton || c is ImageView) return c
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

    private fun setHorizontalMargins(view: View, startPx: Int, endPx: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.marginStart = startPx
        lp.marginEnd = endPx
        view.layoutParams = lp
    }

    private fun styleIsland(
        activity: Activity,
        view: View,
        backdropRoot: ViewGroup,
        excludeSet: MutableSet<View>,
        holder: ChatGlassHolder,
        isCircle: Boolean,
        dark: Boolean,
        radiusDp: Float,
        padHDp: Float,
        padVDp: Float,
        preserveAccentTint: Boolean = false
    ) {
        excludeSet.add(view)
        val radiusPx = dpf(activity, radiusDp)
        if (padHDp > 0f || padVDp > 0f) {
            val ph = dp(activity, padHDp)
            val pv = dp(activity, padVDp)
            view.setPadding(ph, pv, ph, pv)
        }

        view.elevation = dpf(activity, 5f)
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                val r = if (isCircle) min(v.width, v.height) / 2f else min(radiusPx, min(v.width, v.height) / 2f)
                outline.setRoundRect(0, 0, v.width, v.height, r)
            }
        }

        val existing = view.background as? LiquidIslandDrawable
        if (existing != null) {
            existing.requestCapture()
            return
        }

        val tintColor = when {
            preserveAccentTint && dark -> Color.argb(88, 16, 118, 86)
            preserveAccentTint && !dark -> Color.argb(100, 20, 158, 114)
            dark -> Color.argb(46, 16, 24, 34)
            else -> Color.argb(78, 250, 253, 255)
        }

        val drawable = LiquidIslandDrawable(
            target = view,
            backdropRoot = backdropRoot,
            excludeSet = excludeSet,
            radiusPx = radiusPx,
            isCircle = isCircle,
            tintColor = tintColor,
            dark = dark,
            density = activity.resources.displayMetrics.density
        )
        view.background = drawable
        drawable.attach()
        holder.drawables.add(drawable)
    }

    // --- LIQUID GEL PULL-AND-RELEASE SPRING ENGINE FOR MESSAGES ---
    private class GelSpringState(private val view: View) : Choreographer.FrameCallback {
        var tx = 0f; var vtx = 0f; var targetTx = 0f
        var ty = 0f; var vty = 0f; var targetTy = 0f
        var sx = 1f; var vsx = 0f; var targetSx = 1f
        var sy = 1f; var vsy = 0f; var targetSy = 1f
        var rot = 0f; var vrot = 0f; var targetRot = 0f
        var rx = 0f; var vrx = 0f; var targetRx = 0f
        var ry = 0f; var vry = 0f; var targetRy = 0f
        var tz = 0f; var vtz = 0f; var targetTz = 0f

        var dragging = false
        private var running = false
        private var lastNanos = 0L

        fun ensureRunning() {
            if (!running) {
                running = true
                lastNanos = System.nanoTime()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun resetImmediate() {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
            tx = 0f; vtx = 0f; targetTx = 0f
            ty = 0f; vty = 0f; targetTy = 0f
            sx = 1f; vsx = 0f; targetSx = 1f
            sy = 1f; vsy = 0f; targetSy = 1f
            rot = 0f; vrot = 0f; targetRot = 0f
            rx = 0f; vrx = 0f; targetRx = 0f
            ry = 0f; vry = 0f; targetRy = 0f
            tz = 0f; vtz = 0f; targetTz = 0f
            applyToView()
        }

        fun releaseBouncy(pullRatio: Float, dirX: Float) {
            dragging = false
            targetTx = 0f; targetTy = 0f
            targetSx = 1f; targetSy = 1f
            targetRot = 0f; targetRx = 0f; targetRy = 0f
            targetTz = 0f
            // Coupled liquid squash-and-stretch impulse on release
            val kick = (0.55f + 0.85f * pullRatio).coerceAtMost(1.35f)
            if (abs(tx) >= abs(ty)) {
                vsx -= 1.35f * kick
                vsy += 1.25f * kick
            } else {
                vsx += 1.20f * kick
                vsy -= 1.30f * kick
            }
            vrot -= sign(dirX) * 6.5f * pullRatio
            ensureRunning()
        }

        private fun stepAxis(pos: Float, vel: Float, target: Float, k: Float, c: Float, dt: Float): Pair<Float, Float> {
            val acc = -k * (pos - target) - c * vel
            val nv = vel + acc * dt
            val np = pos + nv * dt
            return np to nv
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !view.isAttachedToWindow) {
                running = false
                return
            }
            val rawDt = (frameTimeNanos - lastNanos) / 1_000_000_000f
            lastNanos = frameTimeNanos
            val dt = rawDt.coerceIn(0.001f, 0.032f)
            val subDt = dt * 0.5f

            // While dragging: smooth viscous gel follow; on release: juicy underdamped bouncy jelly
            val kPos = if (dragging) 460f else 340f
            val cPos = if (dragging) 36f else 12.5f
            val kScale = if (dragging) 420f else 380f
            val cScale = if (dragging) 32f else 11.5f
            val kTilt = if (dragging) 360f else 300f
            val cTilt = if (dragging) 28f else 13f

            repeat(2) {
                stepAxis(tx, vtx, targetTx, kPos, cPos, subDt).let { tx = it.first; vtx = it.second }
                stepAxis(ty, vty, targetTy, kPos, cPos, subDt).let { ty = it.first; vty = it.second }
                stepAxis(sx, vsx, targetSx, kScale, cScale, subDt).let { sx = it.first; vsx = it.second }
                stepAxis(sy, vsy, targetSy, kScale, cScale, subDt).let { sy = it.first; vsy = it.second }
                stepAxis(rot, vrot, targetRot, kTilt, cTilt, subDt).let { rot = it.first; vrot = it.second }
                stepAxis(rx, vrx, targetRx, kTilt, cTilt, subDt).let { rx = it.first; vrx = it.second }
                stepAxis(ry, vry, targetRy, kTilt, cTilt, subDt).let { ry = it.first; vry = it.second }
                stepAxis(tz, vtz, targetTz, 380f, 24f, subDt).let { tz = it.first; vtz = it.second }
            }

            applyToView()

            val settled = !dragging &&
                abs(tx) < 0.08f && abs(vtx) < 0.5f &&
                abs(ty) < 0.08f && abs(vty) < 0.5f &&
                abs(sx - 1f) < 0.0015f && abs(vsx) < 0.01f &&
                abs(sy - 1f) < 0.0015f && abs(vsy) < 0.01f &&
                abs(rot) < 0.04f && abs(vrot) < 0.2f &&
                abs(rx) < 0.04f && abs(ry) < 0.04f &&
                abs(tz) < 0.08f

            if (settled) {
                resetImmediate()
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        private fun applyToView() {
            view.translationX = tx
            view.translationY = ty
            view.scaleX = sx.coerceIn(0.86f, 1.16f)
            view.scaleY = sy.coerceIn(0.86f, 1.16f)
            view.rotation = rot.coerceIn(-4.5f, 4.5f)
            view.rotationX = rx.coerceIn(-7f, 7f)
            view.rotationY = ry.coerceIn(-7f, 7f)
            view.translationZ = tz.coerceAtLeast(0f)
        }
    }

    private fun attachElasticMessages(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val holder = holderOf(activity)
        val list = activity.findViewById<ViewGroup>(android.R.id.list) ?: return
        list.clipChildren = false

        bindVisibleMessageRows(activity, list)
        if (holder.elasticAttached) return
        holder.elasticAttached = true

        list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                if (child != null) bindElasticToRow(activity, child)
            }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })

        if (list is AbsListView) {
            list.viewTreeObserver.addOnScrollChangedListener {
                bindVisibleMessageRows(activity, list)
            }
        }
    }

    private fun bindVisibleMessageRows(activity: Activity, list: ViewGroup) {
        for (i in 0 until list.childCount) {
            bindElasticToRow(activity, list.getChildAt(i))
        }
    }

    private fun bindElasticToRow(activity: Activity, row: View) {
        if (!elasticViews.add(row)) return
        val density = activity.resources.displayMetrics.density
        row.cameraDistance = 6500f * density
        val maxX = dpf(activity, 30f)
        val maxY = dpf(activity, 20f)
        val liftZ = dpf(activity, 7f)
        val quickSwipeThreshold = dpf(activity, 26f)
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()

        val spring = GelSpringState(row)
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                spring.resetImmediate()
            }
        })

        var downX = 0f
        var downY = 0f
        var downMs = 0L
        var active = false
        var gelReady = false
        var gelLocked = false
        var lastDx = 0f
        var lastPull = 0f
        var readyRunnable: Runnable? = null

        fun cancelTimer() {
            readyRunnable?.let { mainHandler.removeCallbacks(it) }
            readyRunnable = null
        }

        row.setOnTouchListener { target, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    downMs = SystemClock.uptimeMillis()
                    active = true
                    gelReady = false
                    gelLocked = false
                    lastDx = 0f
                    lastPull = 0f

                    target.pivotX = ev.x.coerceIn(0f, target.width.toFloat().coerceAtLeast(1f))
                    target.pivotY = ev.y.coerceIn(0f, target.height.toFloat().coerceAtLeast(1f))

                    // Soft initial gel press-in
                    spring.dragging = true
                    spring.targetSx = 0.978f
                    spring.targetSy = 0.978f
                    spring.targetTz = liftZ * 0.25f
                    spring.ensureRunning()

                    cancelTimer()
                    val r = Runnable {
                        if (active && !gelLocked) {
                            gelReady = true
                            spring.targetSx = 1.028f
                            spring.targetSy = 0.974f
                            spring.targetTz = liftZ * 0.5f
                            spring.ensureRunning()
                        }
                    }
                    readyRunnable = r
                    mainHandler.postDelayed(r, 115L)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!active) return@setOnTouchListener false
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    val dist = hypot(dx, dy)
                    val elapsed = SystemClock.uptimeMillis() - downMs

                    if (!gelLocked) {
                        // Fast immediate rightward swipe (<115ms) -> let WhatsApp native swipe-to-reply handle it
                        if (!gelReady && elapsed < 120L && dx > quickSwipeThreshold && dx > abs(dy) * 1.7f) {
                            active = false
                            cancelTimer()
                            spring.releaseBouncy(0.2f, dx)
                            return@setOnTouchListener false
                        }
                        // Fast vertical scroll before hold -> let ListView scroll smoothly
                        if (!gelReady && abs(dy) > quickSwipeThreshold && abs(dy) > abs(dx) * 1.6f) {
                            active = false
                            cancelTimer()
                            spring.releaseBouncy(0.15f, 0f)
                            return@setOnTouchListener false
                        }
                        // Lock into smooth Gel Pull-and-Release once held (~115ms) + dragged OR pulled left
                        val engagePull = (gelReady && dist > touchSlop * 0.45f) ||
                            (dx < -touchSlop * 0.9f && abs(dx) > abs(dy))
                        if (engagePull) {
                            gelLocked = true
                            cancelTimer()
                            target.cancelLongPress()
                            target.parent?.requestDisallowInterceptTouchEvent(true)
                            runCatching { target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        }
                    }

                    if (gelLocked) {
                        val tx = (tanh((dx / (maxX * 1.7f)).toDouble()) * maxX).toFloat()
                        val ty = (tanh((dy / (maxY * 1.9f)).toDouble()) * maxY).toFloat()
                        val pull = (hypot(tx / maxX, ty / maxY)).coerceIn(0f, 1.15f)
                        val domX = (abs(tx) - abs(ty)) / (abs(tx) + abs(ty) + 1f)
                        val stretchX = 1f + 0.095f * pull * (0.35f + 0.65f * domX)
                        val stretchY = (1f / stretchX).coerceIn(0.90f, 1.10f)

                        spring.dragging = true
                        spring.targetTx = tx
                        spring.targetTy = ty
                        spring.targetSx = stretchX
                        spring.targetSy = stretchY
                        spring.targetRot = (tx / maxX) * 1.9f
                        spring.targetRy = (tx / maxX) * 5.2f
                        spring.targetRx = -(ty / maxY) * 4.4f
                        spring.targetTz = liftZ * (0.45f + 0.55f * min(pull, 1f))
                        spring.ensureRunning()

                        lastDx = dx
                        lastPull = min(pull, 1f)
                        return@setOnTouchListener true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasLocked = gelLocked
                    active = false
                    gelReady = false
                    gelLocked = false
                    cancelTimer()
                    if (wasLocked) {
                        target.parent?.requestDisallowInterceptTouchEvent(false)
                        spring.releaseBouncy(lastPull.coerceAtLeast(0.35f), lastDx)
                        return@setOnTouchListener true
                    } else {
                        spring.releaseBouncy(0.22f, lastDx)
                        false
                    }
                }
                else -> false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object ChatLiquidAgsl {
        private const val ISLAND_SHADER = """
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
                float rimBand = 1.0 - smoothstep(0.0, max(bezel * 0.48, 2.0), edgeDist);
                float spec1 = pow(max(dot(-n, light1), 0.0), 2.0) * rimBand;
                float spec2 = pow(max(dot(-n, light2), 0.0), 2.8) * rimBand * 0.48;
                float crisp = 1.0 - smoothstep(0.0, 2.2, edgeDist);
                float normY = clamp((p.y + halfSize.y) / max(halfSize.y * 2.0, 1.0), 0.0, 1.0);
                float sheen = pow(1.0 - normY, 2.2) * (isDark > 0.5 ? 0.12 : 0.19);

                half h = half(spec1 * 0.46 + spec2 * 0.26 + crisp * (isDark > 0.5 ? 0.26 : 0.36) + sheen);
                rgb = clamp(rgb + half3(h, h, h), half3(0.0, 0.0, 0.0), half3(1.0, 1.0, 1.0));
                return half4(rgb * half(alpha), half(alpha));
            }
        """

        fun create(): RuntimeShader = RuntimeShader(ISLAND_SHADER)

        fun update(
            shader: Any,
            content: BitmapShader,
            rect: RectF,
            radius: Float,
            density: Float,
            dark: Boolean
        ): Shader {
            val rs = shader as RuntimeShader
            rs.setInputShader("content", content)
            rs.setFloatUniform("center", rect.centerX(), rect.centerY())
            rs.setFloatUniform("halfSize", rect.width() * 0.5f, rect.height() * 0.5f)
            rs.setFloatUniform("radius", min(radius, min(rect.width(), rect.height()) * 0.5f))
            rs.setFloatUniform("texSize", rect.width(), rect.height())
            rs.setFloatUniform("refractPx", 12.5f * density)
            rs.setFloatUniform("dispersion", 2.6f * density)
            rs.setFloatUniform("bezel", 17f * density)
            rs.setFloatUniform("isDark", if (dark) 1f else 0f)
            return rs
        }
    }

    private class LiquidIslandDrawable(
        private val target: View,
        private val backdropRoot: ViewGroup,
        private val excludeSet: Set<View>,
        private val radiusPx: Float,
        private val isCircle: Boolean,
        private val tintColor: Int,
        private val dark: Boolean,
        private val density: Float
    ) : Drawable() {

        private val rect = RectF()
        private val matrix = Matrix()
        private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.55f) })
        }
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tintColor }
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.25f * density
        }

        private var agslShader: Any? = null
        private var workBmp: Bitmap? = null
        private var shownBmp: Bitmap? = null
        private var bmpShader: BitmapShader? = null
        private var lastHash = 0
        private var lastCaptureMs = 0L
        private var pending = false
        private var capturing = false
        private var observer: ViewTreeObserver? = null
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestCapture() }

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                agslShader = runCatching { ChatLiquidAgsl.create() }.getOrNull()
            }
        }

        fun attach() {
            detach()
            observer = target.viewTreeObserver
            observer?.addOnScrollChangedListener(scrollListener)
            target.postDelayed({ capture() }, 120L)
            target.postDelayed({ capture() }, 500L)
        }

        fun detach() {
            observer?.let { if (it.isAlive) it.removeOnScrollChangedListener(scrollListener) }
            observer = null
        }

        fun requestCapture() {
            val now = SystemClock.uptimeMillis()
            if (now - lastCaptureMs >= 50L) {
                lastCaptureMs = now
                capture()
            } else if (!pending) {
                pending = true
                target.postDelayed({
                    pending = false
                    lastCaptureMs = SystemClock.uptimeMillis()
                    capture()
                }, 50L)
            }
        }

        private fun isDescendantOrExcluded(v: View): Boolean {
            if (v === target || v in excludeSet) return true
            var p = target.parent
            while (p is View) {
                if (p === v) return true
                p = p.parent
            }
            return false
        }

        private fun capture() {
            if (capturing) return
            try {
                val w = target.width
                val h = target.height
                if (w <= 0 || h <= 0 || !target.isAttachedToWindow) return
                capturing = true

                val scale = 4
                val sw = (w / scale).coerceAtLeast(2)
                val sh = (h / scale).coerceAtLeast(2)
                val bmp = workBmp?.takeIf { it.width == sw && it.height == sh }
                    ?: Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { workBmp = it }

                val canvas = Canvas(bmp)
                canvas.drawColor(if (dark) 0xFF0E141B.toInt() else 0xFFE9EFF5.toInt())

                val tLoc = IntArray(2)
                val rLoc = IntArray(2)
                target.getLocationInWindow(tLoc)
                backdropRoot.getLocationInWindow(rLoc)

                canvas.scale(1f / scale, 1f / scale)
                canvas.translate(-(tLoc[0] - rLoc[0]).toFloat(), -(tLoc[1] - rLoc[1]).toFloat())

                backdropRoot.background?.let { bg ->
                    bg.setBounds(0, 0, backdropRoot.width, backdropRoot.height)
                    runCatching { bg.draw(canvas) }
                }
                for (i in 0 until backdropRoot.childCount) {
                    val child = backdropRoot.getChildAt(i)
                    if (child.visibility != View.VISIBLE || isDescendantOrExcluded(child)) continue
                    canvas.save()
                    canvas.translate(child.left + child.translationX, child.top + child.translationY)
                    runCatching { child.draw(canvas) }
                    canvas.restore()
                }

                val pixels = IntArray(sw * sh)
                bmp.getPixels(pixels, 0, sw, 0, 0, sw, sh)
                var hash = 1
                var k = 0
                while (k < pixels.size) {
                    hash = 31 * hash + pixels[k]
                    k += 7
                }
                if (hash == lastHash && shownBmp != null) return
                lastHash = hash

                val tmp = IntArray(pixels.size)
                repeat(2) {
                    boxPass(pixels, tmp, sw, sh, 2, true)
                    boxPass(tmp, pixels, sw, sh, 2, false)
                }
                val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
                out.setPixels(pixels, 0, sw, 0, 0, sw, sh)
                shownBmp = out
                bmpShader = BitmapShader(out, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                invalidateSelf()
            } catch (_: Throwable) {
            } finally {
                capturing = false
            }
        }

        private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, rad: Int, horiz: Boolean) {
            val win = rad * 2 + 1
            if (horiz) {
                for (y in 0 until h) {
                    val row = y * w
                    var r = 0; var g = 0; var b = 0
                    for (i in -rad..rad) {
                        val c = src[row + i.coerceIn(0, w - 1)]
                        r += (c ushr 16) and 0xFF; g += (c ushr 8) and 0xFF; b += c and 0xFF
                    }
                    for (x in 0 until w) {
                        dst[row + x] = (0xFF shl 24) or ((r / win) shl 16) or ((g / win) shl 8) or (b / win)
                        val addC = src[row + (x + rad + 1).coerceAtMost(w - 1)]
                        val remC = src[row + (x - rad).coerceAtLeast(0)]
                        r += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                        g += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                        b += (addC and 0xFF) - (remC and 0xFF)
                    }
                }
            } else {
                for (x in 0 until w) {
                    var r = 0; var g = 0; var b = 0
                    for (i in -rad..rad) {
                        val c = src[i.coerceIn(0, h - 1) * w + x]
                        r += (c ushr 16) and 0xFF; g += (c ushr 8) and 0xFF; b += c and 0xFF
                    }
                    for (y in 0 until h) {
                        dst[y * w + x] = (0xFF shl 24) or ((r / win) shl 16) or ((g / win) shl 8) or (b / win)
                        val addC = src[(y + rad + 1).coerceAtMost(h - 1) * w + x]
                        val remC = src[(y - rad).coerceAtLeast(0) * w + x]
                        r += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                        g += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                        b += (addC and 0xFF) - (remC and 0xFF)
                    }
                }
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            rect.set(b)
            val rad = if (isCircle) min(rect.width(), rect.height()) * 0.5f
            else min(radiusPx, min(rect.width(), rect.height()) * 0.5f)

            val bs = bmpShader
            val bmp = shownBmp
            if (bs != null && bmp != null) {
                matrix.reset()
                matrix.setScale(rect.width() / bmp.width.toFloat(), rect.height() / bmp.height.toFloat())
                bs.setLocalMatrix(matrix)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslShader != null) {
                    backdropPaint.shader = ChatLiquidAgsl.update(agslShader!!, bs, rect, rad, density, dark)
                } else {
                    backdropPaint.shader = bs
                }
                canvas.drawRoundRect(rect, rad, rad, backdropPaint)
            }

            canvas.drawRoundRect(rect, rad, rad, tintPaint)

            sheenPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                if (dark) 0x26FFFFFF else 0x52FFFFFF,
                0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, rad, rad, sheenPaint)

            edgePaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                if (dark) 0x64FFFFFF else 0xB2FFFFFF.toInt(),
                if (dark) 0x1EFFFFFF else 0x32FFFFFF,
                Shader.TileMode.CLAMP
            )
            val inset = edgePaint.strokeWidth * 0.5f
            val er = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            canvas.drawRoundRect(er, rad, rad, edgePaint)
        }

        override fun setAlpha(alpha: Int) {
            backdropPaint.alpha = alpha
            tintPaint.alpha = alpha
            edgePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            backdropPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
