package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.min

class StatusPlaybackControls(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private enum class BtnIcon { EYE, EYE_OFF, REWIND, FORWARD, PAUSE, PLAY }

    private class StatusState {
        var cleanMode = false
        var speedIdx = 0
        var paused = false
        val hiddenOverlays = LinkedHashMap<View, Int>()
        var pill: LinearLayout? = null
        var videoGroup: LinearLayout? = null
        var cleanBtn: ImageView? = null
        var playBtn: ImageView? = null
        var speedBtn: TextView? = null
        var savedY = Float.NaN
        var boundPlayerRef: WeakReference<Any>? = null
    }

    private class WaPlayerTracker {
        var paused = false
        var lastStartMs = SystemClock.uptimeMillis()
    }

    private interface PlayerHandle {
        fun seekBy(deltaMs: Long): Boolean
        fun togglePause(): Boolean?
        fun setSpeed(speed: Float): Boolean
    }

    private val states = WeakHashMap<Activity, StatusState>()
    private val waTrackers = Collections.synchronizedMap(WeakHashMap<Any, WaPlayerTracker>())
    private val viewToWaPlayer = Collections.synchronizedMap(WeakHashMap<View, Any>())
    private val waToSpeedTarget = Collections.synchronizedMap(WeakHashMap<Any, Pair<Any, Method>>())

    @Volatile
    private var activeStatusActivity: WeakReference<Activity>? = null
    @Volatile
    private var lastMediaPlayer: WeakReference<MediaPlayer>? = null
    @Volatile
    private var lastWaPlayer: WeakReference<Any>? = null

    private var waStartMethod: Method? = null
    private var waPauseMethod: Method? = null
    private var waSeekMethod: Method? = null
    private var waGetPosMethod: Method? = null
    private var waGetDurMethod: Method? = null
    private var waGetViewMethod: Method? = null
    private var waIsPlayingMethod: Method? = null
    private var speedMethods: List<Method> = emptyList()

    override fun getPluginName(): String = "StatusPlaybackControls"

    override fun doHook() {
        if (!xprefs.getBoolean("status_playback_controls", false)) return

        setupWaHeroPlayerHooks()

        runCatching {
            MediaPlayer::class.java.resolve().firstMethod {
                name = "start"
                emptyParameters()
            }.hook {
                after {
                    val mp = instance as? MediaPlayer ?: return@after
                    if (isStatusActivityOpen()) {
                        lastMediaPlayer = WeakReference(mp)
                    }
                }
            }
        }

        WppCore.addListenerActivity { activity, state ->
            val isStatus = isStatusActivity(activity)
            if (state == WppCore.ActivityChangeState.ChangeType.CREATED ||
                state == WppCore.ActivityChangeState.ChangeType.STARTED ||
                state == WppCore.ActivityChangeState.ChangeType.RESUMED
            ) {
                if (isStatus) {
                    activeStatusActivity = WeakReference(activity)
                    if (state != WppCore.ActivityChangeState.ChangeType.CREATED) {
                        activity.runOnUiThread {
                            runCatching { attachOverlay(activity) }
                        }
                    }
                } else if (state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                    if (activeStatusActivity?.get() === activity) {
                        activeStatusActivity = null
                    }
                }
            }
        }
    }

    private fun isStatusActivityOpen(): Boolean {
        val act = activeStatusActivity?.get() ?: return false
        return !act.isFinishing && !act.isDestroyed
    }

    private fun resolveMethod(cls: Class<*>, name: String, vararg params: Class<*>): Method? {
        return runCatching { cls.getDeclaredMethod(name, *params) }.getOrNull()
            ?: runCatching { cls.getMethod(name, *params) }.getOrNull()
    }

    private fun setupWaHeroPlayerHooks() {
        val waCls = runCatching { classLoader.loadClass(WA_HERO_PLAYER_NAME) }.getOrNull()
        if (waCls != null) {
            waStartMethod = resolveMethod(waCls, "start")?.apply { isAccessible = true }
            waPauseMethod = resolveMethod(waCls, "pause")?.apply { isAccessible = true }
            waSeekMethod = resolveMethod(waCls, "seekTo", Int::class.javaPrimitiveType!!)?.apply { isAccessible = true }
            waGetPosMethod = resolveMethod(waCls, "getCurrentPosition")?.apply { isAccessible = true }
            waGetDurMethod = resolveMethod(waCls, "getDuration")?.apply { isAccessible = true }
            waGetViewMethod = resolveMethod(waCls, "getView")?.apply { isAccessible = true }
            waIsPlayingMethod = resolveMethod(waCls, "isPlaying")?.apply { isAccessible = true }
        }

        val foundSpeeds = mutableListOf<Method>()
        Unobfuscator.loadHeroPlayerSpeedV4(classLoader)?.apply { isAccessible = true }?.let { foundSpeeds.add(it) }
        Unobfuscator.loadHeroServiceSpeedV4(classLoader)?.apply { isAccessible = true }?.let { m ->
            if (foundSpeeds.none { it.declaringClass == m.declaringClass && it.name == m.name }) {
                foundSpeeds.add(m)
            }
        }
        speedMethods = foundSpeeds

        waGetViewMethod?.hook {
            after {
                val player = instanceOrNull ?: return@after
                val view = result as? View ?: return@after
                viewToWaPlayer[view] = player
            }
        }

        waStartMethod?.hook {
            after {
                val player = instanceOrNull ?: return@after
                val trk = trackerOf(player)
                trk.paused = false
                trk.lastStartMs = SystemClock.uptimeMillis()
                lastWaPlayer = WeakReference(player)
                runCatching {
                    (waGetViewMethod?.invoke(player) as? View)?.let { v ->
                        viewToWaPlayer[v] = player
                    }
                }
                val act = activeStatusActivity?.get()
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    act.runOnUiThread {
                        val st = stateOf(act)
                        val prev = st.boundPlayerRef?.get()
                        if (prev !== player) {
                            st.boundPlayerRef = WeakReference(player)
                            st.speedIdx = 0
                            st.speedBtn?.text = SPEED_LABELS[0]
                        }
                        st.paused = false
                        st.playBtn?.setImageDrawable(
                            StatusIconDrawable(BtnIcon.PAUSE, Color.WHITE, dpf(act, 1.9f))
                        )
                    }
                }
            }
        }

        waPauseMethod?.hook {
            after {
                val player = instanceOrNull ?: return@after
                trackerOf(player).paused = true
                val act = activeStatusActivity?.get()
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    act.runOnUiThread {
                        val st = stateOf(act)
                        if (st.boundPlayerRef?.get() === player) {
                            st.paused = true
                            st.playBtn?.setImageDrawable(
                                StatusIconDrawable(BtnIcon.PLAY, Color.WHITE, dpf(act, 1.9f))
                            )
                        }
                    }
                }
            }
        }
    }

    private fun trackerOf(player: Any): WaPlayerTracker {
        synchronized(waTrackers) {
            return waTrackers.getOrPut(player) { WaPlayerTracker() }
        }
    }

    private fun isStatusActivity(activity: Activity): Boolean {
        val name = activity.javaClass.simpleName
        return name.contains("StatusPlayback", ignoreCase = true)
    }

    private fun isTurkish(activity: Activity): Boolean {
        val lang = activity.resources.configuration.locales[0]?.language
            ?: Locale.getDefault().language
        return lang.equals("tr", ignoreCase = true)
    }

    private fun dp(activity: Activity, v: Float): Int =
        (v * activity.resources.displayMetrics.density).toInt()

    private fun dpf(activity: Activity, v: Float): Float =
        v * activity.resources.displayMetrics.density

    private fun stateOf(activity: Activity): StatusState =
        states.getOrPut(activity) { StatusState() }

    private fun attachOverlay(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val existing = content.findViewWithTag<View>(PILL_TAG)
        if (existing != null) {
            existing.bringToFront()
            return
        }

        val st = stateOf(activity)
        val tr = isTurkish(activity)

        val pill = LinearLayout(activity).apply {
            tag = PILL_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(activity, 24f)
                setColor(0xA610151A.toInt())
                setStroke(dp(activity, 1f).coerceAtLeast(1), 0x4DFFFFFF)
            }
            val p = dp(activity, 5f)
            setPadding(p, dp(activity, 6f), p, dp(activity, 6f))
            elevation = dpf(activity, 8f)
        }

        val cleanBtn = makeIconButton(activity, BtnIcon.EYE) {
            toggleCleanView(activity)
        }
        val speedBtn = TextView(activity).apply {
            text = SPEED_LABELS[st.speedIdx]
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x28FFFFFF)
            }
            val sz = dp(activity, 36f)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                topMargin = dp(activity, 5f)
            }
            applyTapFeedback(this)
            setOnClickListener { cycleSpeed(activity) }
        }
        val rewBtn = makeIconButton(activity, BtnIcon.REWIND, topGap = true) {
            seekStatus(activity, -5000L, tr)
        }
        val playBtn = makeIconButton(activity, BtnIcon.PAUSE, topGap = true) {
            togglePauseStatus(activity, tr)
        }
        val fwdBtn = makeIconButton(activity, BtnIcon.FORWARD, topGap = true) {
            seekStatus(activity, 5000L, tr)
        }

        val videoGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(speedBtn)
            addView(rewBtn)
            addView(playBtn)
            addView(fwdBtn)
        }

        pill.addView(cleanBtn)
        pill.addView(videoGroup)

        st.pill = pill
        st.videoGroup = videoGroup
        st.cleanBtn = cleanBtn
        st.playBtn = playBtn
        st.speedBtn = speedBtn

        installVerticalDrag(activity, content, pill, st)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply {
            marginEnd = dp(activity, 10f)
        }
        content.addView(pill, lp)
        pill.post {
            if (!st.savedY.isNaN()) {
                pill.y = st.savedY
            }
        }
        pill.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                restoreHiddenOverlays(st)
                if (activeStatusActivity?.get() === activity) {
                    activeStatusActivity = null
                }
            }
        })
    }

    private fun makeIconButton(
        activity: Activity,
        icon: BtnIcon,
        topGap: Boolean = false,
        onClick: () -> Unit
    ): ImageView = ImageView(activity).apply {
        setImageDrawable(StatusIconDrawable(icon, Color.WHITE, dpf(activity, 1.9f)))
        val pad = dp(activity, 9f)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x28FFFFFF)
        }
        val sz = dp(activity, 36f)
        layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
            if (topGap) topMargin = dp(activity, 5f)
        }
        applyTapFeedback(this)
        setOnClickListener { onClick() }
    }

    private fun applyTapFeedback(view: View) {
        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            false
        }
    }

    private fun installVerticalDrag(
        activity: Activity,
        root: ViewGroup,
        pill: View,
        st: StatusState
    ) {
        val slop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
        var downRawY = 0f
        var startY = 0f
        var dragging = false
        pill.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = ev.rawY
                    startY = v.y
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - downRawY
                    if (!dragging && abs(dy) > slop) {
                        dragging = true
                    }
                    if (dragging) {
                        val minY = dpf(activity, 64f)
                        val maxY = (root.height - v.height - dpf(activity, 88f)).coerceAtLeast(minY)
                        v.y = (startY + dy).coerceIn(minY, maxY)
                        st.savedY = v.y
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging
                else -> false
            }
        }
    }

    private fun toggleCleanView(activity: Activity) {
        val st = stateOf(activity)
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        st.cleanMode = !st.cleanMode
        if (st.cleanMode) {
            hideStatusChrome(activity, content, st)
            st.videoGroup?.visibility = View.GONE
            st.cleanBtn?.setImageDrawable(StatusIconDrawable(BtnIcon.EYE_OFF, 0xFF7BE8B4.toInt(), dpf(activity, 1.9f)))
            st.pill?.alpha = 0.55f
        } else {
            restoreHiddenOverlays(st)
            st.videoGroup?.visibility = View.VISIBLE
            st.cleanBtn?.setImageDrawable(StatusIconDrawable(BtnIcon.EYE, Color.WHITE, dpf(activity, 1.9f)))
            st.pill?.alpha = 1f
        }
    }

    private fun hideStatusChrome(activity: Activity, content: ViewGroup, st: StatusState) {
        restoreHiddenOverlays(st)
        val protected = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        val screenW = activity.resources.displayMetrics.widthPixels
        val screenH = activity.resources.displayMetrics.heightPixels

        fun markAncestors(v: View) {
            var cur: View? = v
            while (cur != null) {
                protected.add(cur)
                if (cur === content) break
                cur = cur.parent as? View
            }
        }

        fun scanMedia(v: View) {
            if (v.tag == PILL_TAG) {
                markAncestors(v)
                return
            }
            val entry = runCatching {
                if (v.id != View.NO_ID && v.id > 0) v.resources.getResourceEntryName(v.id) else ""
            }.getOrDefault("")

            val isMedia = v is SurfaceView || v is TextureView || v is VideoView ||
                viewToWaPlayer.containsKey(v) ||
                (v is ImageView && v.width >= screenW * 0.55f && v.height >= screenH * 0.32f) ||
                (v is TextView && entry.contains("message_text", ignoreCase = true) && v.height >= screenH * 0.20f)
            if (isMedia) {
                markAncestors(v)
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) scanMedia(v.getChildAt(i))
            }
        }

        scanMedia(content)

        val loc = IntArray(2)
        fun walkHide(v: View) {
            if (v.tag == PILL_TAG || v.visibility != View.VISIBLE) return
            if (v in protected) {
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) walkHide(v.getChildAt(i))
                }
                return
            }
            val entry = runCatching {
                if (v.id != View.NO_ID && v.id > 0) v.resources.getResourceEntryName(v.id) else ""
            }.getOrDefault("")
            v.getLocationOnScreen(loc)
            val topBand = loc[1] + v.height <= screenH * 0.24f
            val bottomBand = loc[1] >= screenH * 0.72f
            val matchesToken = OVERLAY_TOKENS.any { entry.contains(it, ignoreCase = true) }

            if (matchesToken || topBand || bottomBand) {
                st.hiddenOverlays[v] = v.visibility
                v.visibility = View.INVISIBLE
                return
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walkHide(v.getChildAt(i))
            }
        }

        walkHide(content)
    }

    private fun restoreHiddenOverlays(st: StatusState) {
        for ((v, origVis) in st.hiddenOverlays) {
            runCatching { v.visibility = origVis }
        }
        st.hiddenOverlays.clear()
    }

    private fun cycleSpeed(activity: Activity) {
        val st = stateOf(activity)
        val tr = isTurkish(activity)
        val player = findActivePlayer(activity)
        if (player == null) {
            showNoVideoToast(activity, tr)
            return
        }
        val nextIdx = (st.speedIdx + 1) % SPEEDS.size
        val targetSpeed = SPEEDS[nextIdx]
        if (player.setSpeed(targetSpeed)) {
            st.speedIdx = nextIdx
            st.speedBtn?.text = SPEED_LABELS[nextIdx]
            Toast.makeText(
                activity,
                if (tr) "Hız: ${SPEED_LABELS[nextIdx]}" else "Speed: ${SPEED_LABELS[nextIdx]}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                activity,
                if (tr) "Hız bu videoda değiştirilemedi" else "Speed unavailable for this video",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun seekStatus(activity: Activity, deltaMs: Long, tr: Boolean) {
        val player = findActivePlayer(activity)
        if (player == null) {
            showNoVideoToast(activity, tr)
            return
        }
        if (player.seekBy(deltaMs)) {
            val sec = (deltaMs / 1000L).toInt()
            val label = if (sec > 0) "+${sec}s" else "${sec}s"
            Toast.makeText(activity, label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePauseStatus(activity: Activity, tr: Boolean) {
        val st = stateOf(activity)
        val player = findActivePlayer(activity)
        if (player == null) {
            showNoVideoToast(activity, tr)
            return
        }
        val nowPaused = player.togglePause()
        if (nowPaused != null) {
            st.paused = nowPaused
            st.playBtn?.setImageDrawable(
                StatusIconDrawable(
                    if (nowPaused) BtnIcon.PLAY else BtnIcon.PAUSE,
                    Color.WHITE,
                    dpf(activity, 1.9f)
                )
            )
        }
    }

    private fun showNoVideoToast(activity: Activity, tr: Boolean) {
        Toast.makeText(
            activity,
            if (tr) "Aktif video oynatıcı bulunamadı" else "No active video player found",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun findVisibleWaPlayer(activity: Activity): Any? {
        val decor = activity.window?.decorView ?: return null
        val rect = Rect()
        var bestPlayer: Any? = null
        var bestArea = 0

        synchronized(viewToWaPlayer) {
            for ((view, player) in viewToWaPlayer) {
                if (!view.isAttachedToWindow || view.visibility != View.VISIBLE) continue
                if (view.rootView !== decor) continue
                if (!view.getGlobalVisibleRect(rect)) continue
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    bestArea = area
                    bestPlayer = player
                }
            }
        }
        if (bestPlayer != null) return bestPlayer

        synchronized(waTrackers) {
            val bestInWindow = waTrackers.entries
                .sortedByDescending { it.value.lastStartMs }
                .firstOrNull { (player, _) ->
                    val v = runCatching { waGetViewMethod?.invoke(player) as? View }.getOrNull()
                    v != null && v.isAttachedToWindow && v.rootView === decor
                }
                ?.key
            if (bestInWindow != null) return bestInWindow
        }

        val last = lastWaPlayer?.get()
        if (last != null) {
            val dur = runCatching { (waGetDurMethod?.invoke(last) as? Number)?.toInt() ?: 0 }.getOrDefault(0)
            val pos = runCatching { (waGetPosMethod?.invoke(last) as? Number)?.toInt() ?: 0 }.getOrDefault(0)
            if (dur > 0 || pos > 0) return last
        }
        return null
    }

    private fun findActivePlayer(activity: Activity): PlayerHandle? {
        findVisibleWaPlayer(activity)?.let { waPlayer ->
            stateOf(activity).boundPlayerRef = WeakReference(waPlayer)
            return wrapWaPlayer(waPlayer)
        }

        val mp = lastMediaPlayer?.get()
        if (mp != null) {
            val alive = runCatching { mp.duration > 0 || mp.isPlaying }.getOrDefault(false)
            if (alive) return wrapMediaPlayer(mp)
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content != null) {
            findVideoView(content)?.let { vv ->
                return wrapVideoView(vv)
            }
        }
        return null
    }

    private fun findVideoView(view: View): VideoView? {
        if (view is VideoView && view.visibility == View.VISIBLE) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findVideoView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findSpeedTargetByBfs(root: Any): Pair<Any, Method>? {
        val methods = speedMethods
        if (methods.isEmpty()) return null

        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val queue = ArrayDeque<Pair<Any, Int>>()
        visited.add(root)
        queue.addLast(root to 0)
        var scanned = 0

        fun enqueue(v: Any?, nextDepth: Int) {
            if (v == null) return
            if (v is Reference<*>) {
                enqueue(v.get(), nextDepth)
                return
            }
            if (v is AtomicReference<*>) {
                enqueue(v.get(), nextDepth)
                return
            }
            if (v is String || v is Number || v is Boolean || v is Char || v is Enum<*>) return
            if (v is View || v is Context || v is Class<*> || v is Handler || v is Looper || v is Thread) return
            if (v is Collection<*>) {
                var c = 0
                for (item in v) {
                    if (c++ >= 12) break
                    enqueue(item, nextDepth)
                }
                return
            }
            if (v is Array<*>) {
                val lim = min(v.size, 12)
                for (i in 0 until lim) enqueue(v[i], nextDepth)
                return
            }
            val cn = v.javaClass.name
            if (cn.startsWith("android.") || cn.startsWith("androidx.") ||
                cn.startsWith("java.lang.") || cn.startsWith("kotlin.")
            ) return
            if (visited.add(v)) {
                queue.addLast(v to nextDepth)
            }
        }

        while (queue.isNotEmpty() && scanned < 650) {
            val (cur, depth) = queue.removeFirst()
            scanned++

            for (m in methods) {
                if (m.declaringClass.isInstance(cur)) {
                    return cur to m
                }
            }
            if (depth >= 7) continue

            var cls: Class<*>? = cur.javaClass
            var hops = 0
            while (cls != null && cls != Any::class.java && hops < 5) {
                val fields = runCatching { cls.declaredFields }.getOrNull() ?: break
                for (f in fields) {
                    if (Modifier.isStatic(f.modifiers)) continue
                    val ft = f.type
                    if (ft.isPrimitive || ft == String::class.java) continue
                    val v = runCatching {
                        f.isAccessible = true
                        f.get(cur)
                    }.getOrNull() ?: continue
                    enqueue(v, depth + 1)
                }
                cls = cls.superclass
                hops++
            }
        }
        return null
    }

    private fun wrapWaPlayer(player: Any): PlayerHandle = object : PlayerHandle {
        override fun seekBy(deltaMs: Long): Boolean {
            val seekM = waSeekMethod ?: return false
            val posM = waGetPosMethod ?: return false
            return runCatching {
                val cur = (posM.invoke(player) as? Number)?.toInt() ?: 0
                val dur = (waGetDurMethod?.invoke(player) as? Number)?.toInt()
                    ?.takeIf { it > 0 } ?: Int.MAX_VALUE
                val target = (cur + deltaMs.toInt()).coerceIn(0, dur)
                seekM.invoke(player, target)
                true
            }.getOrDefault(false)
        }

        override fun togglePause(): Boolean? {
            val startM = waStartMethod ?: return null
            val pauseM = waPauseMethod ?: return null
            val trk = trackerOf(player)
            val playing = runCatching {
                waIsPlayingMethod?.invoke(player) as? Boolean
            }.getOrNull() ?: !trk.paused
            return runCatching {
                if (playing) {
                    pauseM.invoke(player)
                    trk.paused = true
                    true
                } else {
                    startM.invoke(player)
                    trk.paused = false
                    false
                }
            }.getOrNull()
        }

        override fun setSpeed(speed: Float): Boolean {
            val pair = waToSpeedTarget[player] ?: findSpeedTargetByBfs(player)?.also {
                waToSpeedTarget[player] = it
            } ?: return false
            return runCatching {
                pair.second.invoke(pair.first, speed)
                true
            }.getOrDefault(false)
        }
    }

    private fun wrapMediaPlayer(mp: MediaPlayer): PlayerHandle = object : PlayerHandle {
        override fun seekBy(deltaMs: Long): Boolean = runCatching {
            val cur = mp.currentPosition
            val dur = mp.duration.takeIf { it > 0 } ?: Int.MAX_VALUE
            val target = (cur + deltaMs.toInt()).coerceIn(0, dur)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(target.toLong(), MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(target)
            }
            true
        }.getOrDefault(false)

        override fun togglePause(): Boolean? = runCatching {
            if (mp.isPlaying) {
                mp.pause()
                true
            } else {
                mp.start()
                false
            }
        }.getOrNull()

        override fun setSpeed(speed: Float): Boolean = runCatching {
            val params = mp.playbackParams
            params.speed = speed
            mp.playbackParams = params
            true
        }.getOrDefault(false)
    }

    private fun wrapVideoView(vv: VideoView): PlayerHandle = object : PlayerHandle {
        override fun seekBy(deltaMs: Long): Boolean = runCatching {
            val cur = vv.currentPosition
            val dur = vv.duration.takeIf { it > 0 } ?: Int.MAX_VALUE
            vv.seekTo((cur + deltaMs.toInt()).coerceIn(0, dur))
            true
        }.getOrDefault(false)

        override fun togglePause(): Boolean? = runCatching {
            if (vv.isPlaying) {
                vv.pause()
                true
            } else {
                vv.start()
                false
            }
        }.getOrNull()

        override fun setSpeed(speed: Float): Boolean {
            val mpField = runCatching {
                val f = VideoView::class.java.getDeclaredField("mMediaPlayer")
                f.isAccessible = true
                f.get(vv) as? MediaPlayer
            }.getOrNull() ?: return false
            return wrapMediaPlayer(mpField).setSpeed(speed)
        }
    }

    private class StatusIconDrawable(
        private val kind: BtnIcon,
        private val color: Int,
        strokePx: Float
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@StatusIconDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@StatusIconDrawable.color
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val l = b.left.toFloat()
            val t = b.top.toFloat()
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = l + w * 0.5f
            val cy = t + h * 0.5f

            when (kind) {
                BtnIcon.EYE, BtnIcon.EYE_OFF -> {
                    val path = Path().apply {
                        moveTo(l + w * 0.08f, cy)
                        quadTo(cx, t + h * 0.10f, l + w * 0.92f, cy)
                        quadTo(cx, t + h * 0.90f, l + w * 0.08f, cy)
                    }
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx, cy, min(w, h) * 0.16f, strokePaint)
                    if (kind == BtnIcon.EYE_OFF) {
                        canvas.drawLine(l + w * 0.16f, t + h * 0.84f, l + w * 0.84f, t + h * 0.16f, strokePaint)
                    }
                }
                BtnIcon.REWIND -> {
                    val p1 = Path().apply {
                        moveTo(l + w * 0.50f, t + h * 0.22f)
                        lineTo(l + w * 0.14f, cy)
                        lineTo(l + w * 0.50f, t + h * 0.78f)
                        close()
                    }
                    val p2 = Path().apply {
                        moveTo(l + w * 0.86f, t + h * 0.22f)
                        lineTo(l + w * 0.50f, cy)
                        lineTo(l + w * 0.86f, t + h * 0.78f)
                        close()
                    }
                    canvas.drawPath(p1, fillPaint)
                    canvas.drawPath(p2, fillPaint)
                }
                BtnIcon.FORWARD -> {
                    val p1 = Path().apply {
                        moveTo(l + w * 0.14f, t + h * 0.22f)
                        lineTo(l + w * 0.50f, cy)
                        lineTo(l + w * 0.14f, t + h * 0.78f)
                        close()
                    }
                    val p2 = Path().apply {
                        moveTo(l + w * 0.50f, t + h * 0.22f)
                        lineTo(l + w * 0.86f, cy)
                        lineTo(l + w * 0.50f, t + h * 0.78f)
                        close()
                    }
                    canvas.drawPath(p1, fillPaint)
                    canvas.drawPath(p2, fillPaint)
                }
                BtnIcon.PAUSE -> {
                    val barW = w * 0.18f
                    val r = barW * 0.4f
                    canvas.drawRoundRect(
                        RectF(l + w * 0.24f, t + h * 0.20f, l + w * 0.24f + barW, t + h * 0.80f),
                        r, r, fillPaint
                    )
                    canvas.drawRoundRect(
                        RectF(l + w * 0.58f, t + h * 0.20f, l + w * 0.58f + barW, t + h * 0.80f),
                        r, r, fillPaint
                    )
                }
                BtnIcon.PLAY -> {
                    val p = Path().apply {
                        moveTo(l + w * 0.26f, t + h * 0.18f)
                        lineTo(l + w * 0.84f, cy)
                        lineTo(l + w * 0.26f, t + h * 0.82f)
                        close()
                    }
                    canvas.drawPath(p, fillPaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val PILL_TAG = "wp_status_playback_pill"
        private const val WA_HERO_PLAYER_NAME = "com.whatsapp.videoplayback.WaFbHeroPlayer"
        private val SPEEDS = floatArrayOf(1f, 1.5f, 2f, 0.5f)
        private val SPEED_LABELS = arrayOf("1x", "1.5x", "2x", "0.5x")
        private val OVERLAY_TOKENS = listOf(
            "progress", "header", "toolbar", "footer", "reply", "caption",
            "reaction", "emoji", "bottom", "back", "menu", "avatar",
            "profile", "contact", "name", "date", "title", "subtitle",
            "views", "seen", "like", "heart", "share", "forward",
            "download", "gradient", "scrim", "control", "mute"
        )
    }
}
