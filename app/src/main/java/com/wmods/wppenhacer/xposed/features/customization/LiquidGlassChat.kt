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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.AbsListView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
        var listFloated = false
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
                    decor.post {
                        runCatching {
                            if (enableGlass) applyLiquidGlassChat(activity)
                            if (enableElastic) attachElasticMessages(activity)
                        }
                    }
                    decor.postDelayed({
                        runCatching {
                            if (enableGlass) applyLiquidGlassChat(activity)
                            if (enableElastic) attachElasticMessages(activity)
                        }
                    }, 380L)
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

    private fun applyLiquidGlassChat(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val dark = isDark(activity)
        val holder = holderOf(activity)

        val backdropRoot = findBackdropRoot(activity, content) ?: content
        val excluded = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())

        // --- TOP BAR: fully transparent shell + compact floating glass islands ---
        val toolbar = findByName(activity, "toolbar") as? ViewGroup
        val header = findByName(activity, "header") as? ViewGroup
        val toolbarContainer = findByName(activity, "toolbar_container") as? ViewGroup
        val topAnchor = header ?: toolbarContainer ?: toolbar

        toolbar?.let { tb ->
            excluded.add(tb)
            tb.setBackgroundColor(Color.TRANSPARENT)
            tb.elevation = 0f
            tb.clipChildren = false
            tb.clipToPadding = false
            clearAncestorsBackground(tb, 4, backdropRoot)

            val padV = dp(activity, 4f)
            val padH = dp(activity, 6f)
            tb.setPadding(padH, padV, padH, padV)

            // 1) Back + avatar pill (compact so contact name has room)
            val backView = findByName(activity, "back") ?: findDirectNavButton(tb)
            if (backView != null && backView.visibility == View.VISIBLE) {
                styleIsland(
                    activity = activity,
                    view = backView,
                    backdropRoot = backdropRoot,
                    excludeSet = excluded,
                    holder = holder,
                    isCircle = backView.width in 1..(dp(activity, 56f)),
                    dark = dark,
                    radiusDp = 22f,
                    padHDp = 4f,
                    padVDp = 3f
                )
            }

            // 2) Contact name pill (wider, minimal outer margin)
            val contactPill = findByName(activity, "conversation_contact")
                ?: findByName(activity, "conversation_contact_name")?.parent as? View
            if (contactPill != null && contactPill !== tb) {
                styleIsland(
                    activity = activity,
                    view = contactPill,
                    backdropRoot = backdropRoot,
                    excludeSet = excluded,
                    holder = holder,
                    isCircle = false,
                    dark = dark,
                    radiusDp = 22f,
                    padHDp = 8f,
                    padVDp = 4f
                )
                setHorizontalMargins(contactPill, dp(activity, 4f), dp(activity, 4f))
            }

            // 3) Right action menu pill
            val actionMenu = findActionMenuView(tb)
            if (actionMenu != null && actionMenu.childCount > 0) {
                styleIsland(
                    activity = activity,
                    view = actionMenu,
                    backdropRoot = backdropRoot,
                    excludeSet = excluded,
                    holder = holder,
                    isCircle = false,
                    dark = dark,
                    radiusDp = 22f,
                    padHDp = 2f,
                    padVDp = 2f
                )
            }
        }
        header?.let {
            excluded.add(it)
            it.setBackgroundColor(Color.TRANSPARENT)
            it.elevation = 0f
            it.clipChildren = false
            it.clipToPadding = false
        }
        toolbarContainer?.let {
            excluded.add(it)
            it.setBackgroundColor(Color.TRANSPARENT)
            it.elevation = 0f
            it.clipChildren = false
            it.clipToPadding = false
        }

        // --- BOTTOM COMPOSER: transparent shell + separated glass capsule + circular mic/send ---
        val entry = findByName(activity, "entry") as? EditText
        val inputCard = findByName(activity, "input_layout_content")
            ?: findByName(activity, "input_layout")
            ?: entry?.parent as? View
        val footer = findByName(activity, "footer") as? ViewGroup
            ?: inputCard?.parent as? ViewGroup

        footer?.let { ft ->
            excluded.add(ft)
            ft.setBackgroundColor(Color.TRANSPARENT)
            ft.elevation = 0f
            ft.clipChildren = false
            ft.clipToPadding = false
            clearAncestorsBackground(ft, 3, backdropRoot)
        }

        if (inputCard != null) {
            excluded.add(inputCard)
            styleIsland(
                activity = activity,
                view = inputCard,
                backdropRoot = backdropRoot,
                excludeSet = excluded,
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
            excluded.add(btn)
            styleIsland(
                activity = activity,
                view = btn,
                backdropRoot = backdropRoot,
                excludeSet = excluded,
                holder = holder,
                isCircle = true,
                dark = dark,
                radiusDp = 24f,
                padHDp = 0f,
                padVDp = 0f,
                preserveAccentTint = true
            )
        }

        // --- FLOAT LIST BEHIND HEADER & FOOTER so messages truly scroll under the glass ---
        val list = activity.findViewById<View>(android.R.id.list)
        if (list != null) {
            if (!holder.listFloated) {
                holder.listFloated = true
                floatListBehindBars(activity, list, topAnchor, footer)
            }
            if (!holder.listListenerAttached) {
                holder.listListenerAttached = true
                list.viewTreeObserver.addOnScrollChangedListener {
                    for (d in holder.drawables) d.requestCapture()
                }
            }
        }
        topAnchor?.bringToFront()
        footer?.bringToFront()
        for (d in holder.drawables) d.requestCapture()
    }

    private fun floatListBehindBars(
        activity: Activity,
        list: View,
        topBar: View?,
        bottomBar: View?
    ) {
        if (list is ViewGroup) {
            list.clipToPadding = false
        }
        val lp = list.layoutParams
        val topH = (topBar?.height ?: 0).takeIf { it > 0 } ?: dp(activity, 64f)
        val botH = (bottomBar?.height ?: 0).takeIf { it > 0 } ?: dp(activity, 62f)

        if (lp is RelativeLayout.LayoutParams) {
            lp.removeRule(RelativeLayout.BELOW)
            lp.removeRule(RelativeLayout.ABOVE)
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            list.layoutParams = lp
            list.setPadding(
                list.paddingLeft,
                maxOf(list.paddingTop, topH + dp(activity, 6f)),
                list.paddingRight,
                maxOf(list.paddingBottom, botH + dp(activity, 10f))
            )
        } else if (lp is ViewGroup.MarginLayoutParams) {
            // Pull list under top/bottom bars via negative margins + matching padding
            if (lp.topMargin >= 0) lp.topMargin = -topH
            if (lp.bottomMargin >= 0) lp.bottomMargin = -botH
            list.layoutParams = lp
            list.setPadding(
                list.paddingLeft,
                maxOf(list.paddingTop, topH + dp(activity, 6f)),
                list.paddingRight,
                maxOf(list.paddingBottom, botH + dp(activity, 10f))
            )
        }
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

    private fun clearAncestorsBackground(view: View, levels: Int, stopAt: ViewGroup) {
        var cur = view.parent as? ViewGroup
        var step = 0
        while (cur != null && cur !== stopAt && step < levels) {
            cur.setBackgroundColor(Color.TRANSPARENT)
            cur.elevation = 0f
            cur.clipChildren = false
            cur.clipToPadding = false
            cur = cur.parent as? ViewGroup
            step++
        }
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

        // Lighter, more transparent glass tint so scrolling messages & wallpaper refract clearly
        val tintColor = when {
            preserveAccentTint && dark -> Color.argb(92, 16, 118, 86)
            preserveAccentTint && !dark -> Color.argb(104, 20, 158, 114)
            dark -> Color.argb(56, 16, 24, 34)
            else -> Color.argb(86, 250, 253, 255)
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

    // --- ELASTIC MESSAGE PRESS (Whole bubble stretches smoothly on hold+drag) ---
    private fun attachElasticMessages(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val holder = holderOf(activity)
        val list = activity.findViewById<ViewGroup>(android.R.id.list) ?: return

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
        val maxStretch = dpf(activity, 22f)
        val swipeReplyThreshold = dpf(activity, 52f)
        val verticalScrollThreshold = dpf(activity, 46f)
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()

        var downX = 0f
        var downY = 0f
        var active = false
        var gelLocked = false
        var holdRunnable: Runnable? = null

        fun springBack(target: View) {
            holdRunnable?.let { mainHandler.removeCallbacks(it) }
            holdRunnable = null
            target.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setDuration(300L)
                .setInterpolator(OvershootInterpolator(1.55f))
                .start()
        }

        // Animate the entire row so bubble background + text + timestamp + tail all stretch together
        row.setOnTouchListener { target, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    active = true
                    gelLocked = false
                    target.animate().cancel()
                    target.pivotX = ev.x.coerceIn(0f, target.width.toFloat().coerceAtLeast(1f))
                    target.pivotY = ev.y.coerceIn(0f, target.height.toFloat().coerceAtLeast(1f))

                    val r = Runnable {
                        if (active) {
                            gelLocked = true
                            target.animate()
                                .scaleX(1.032f)
                                .scaleY(0.972f)
                                .setDuration(120L)
                                .start()
                        }
                    }
                    holdRunnable = r
                    mainHandler.postDelayed(r, 150L)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!active) return@setOnTouchListener false
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY

                    // Only release to WhatsApp swipe-to-reply on fast deliberate rightward swipe before gel-lock
                    if (!gelLocked && dx > swipeReplyThreshold && dx > abs(dy) * 2.1f) {
                        active = false
                        springBack(target)
                        return@setOnTouchListener false
                    }
                    if (!gelLocked && abs(dy) > verticalScrollThreshold && abs(dy) > abs(dx) * 1.8f) {
                        active = false
                        springBack(target)
                        return@setOnTouchListener false
                    }

                    val dist = hypot(dx, dy)
                    if (gelLocked || dist > touchSlop * 0.35f) {
                        val tx = (tanh((dx / (maxStretch * 1.9f)).toDouble()) * maxStretch).toFloat()
                        val ty = (tanh((dy / (maxStretch * 2.1f)).toDouble()) * (maxStretch * 0.72f)).toFloat()
                        val pull = (dist / (maxStretch * 2.8f)).coerceIn(0f, 1f)
                        val sx = if (abs(dx) >= abs(dy)) 1f + 0.048f * pull else 1f - 0.024f * pull
                        val sy = if (abs(dy) > abs(dx)) 1f + 0.042f * pull else 1f - 0.022f * pull
                        target.translationX = tx
                        target.translationY = ty
                        target.scaleX = sx
                        target.scaleY = sy
                        target.rotation = sign(dx) * min(abs(dx) / 95f, 1f) * 1.15f
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    active = false
                    gelLocked = false
                    springBack(target)
                }
            }
            false
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
                float sheen = pow(1.0 - normY, 2.2) * (isDark > 0.5 ? 0.13 : 0.20);

                half h = half(spec1 * 0.48 + spec2 * 0.28 + crisp * (isDark > 0.5 ? 0.28 : 0.38) + sheen);
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
            if (now - lastCaptureMs >= 55L) {
                lastCaptureMs = now
                capture()
            } else if (!pending) {
                pending = true
                target.postDelayed({
                    pending = false
                    lastCaptureMs = SystemClock.uptimeMillis()
                    capture()
                }, 55L)
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
