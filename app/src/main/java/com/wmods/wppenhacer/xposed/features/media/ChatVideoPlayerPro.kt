package com.wmods.wppenhacer.xposed.features.media

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class ChatVideoPlayerPro(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private class MediaState {
        var speedIdx = 0
        var boundPlayerRef: WeakReference<Any>? = null
        var speedPill: TextView? = null
        var feedbackBox: LinearLayout? = null
        var feedbackTitle: TextView? = null
        var feedbackBarFill: View? = null
        var feedbackBarTrack: FrameLayout? = null
        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        var downX = 0f
        var downY = 0f
        var dragMode = 0 // 0 none, 1 brightness, 2 volume
        var startVal = 0f
        var maxVol = 15
        val handler = Handler(Looper.getMainLooper())
        var hideRunnable: Runnable? = null
    }

    private interface PlayerHandle {
        fun seekBy(deltaMs: Long): Boolean
        fun setSpeed(speed: Float): Boolean
    }

    private val states = WeakHashMap<Activity, MediaState>()
    private val viewToWaPlayer = Collections.synchronizedMap(WeakHashMap<View, Any>())
    private val waLastStart = Collections.synchronizedMap(WeakHashMap<Any, Long>())
    private val waToSpeedTarget = Collections.synchronizedMap(WeakHashMap<Any, Pair<Any, Method>>())

    @Volatile
    private var activeMediaActivity: WeakReference<Activity>? = null
    @Volatile
    private var lastMediaPlayer: WeakReference<MediaPlayer>? = null
    @Volatile
    private var lastWaPlayer: WeakReference<Any>? = null

    private var waStartMethod: Method? = null
    private var waSeekMethod: Method? = null
    private var waGetPosMethod: Method? = null
    private var waGetDurMethod: Method? = null
    private var waGetViewMethod: Method? = null
    private var speedMethods: List<Method> = emptyList()

    override fun getPluginName(): String = "ChatVideoPlayerPro"

    override fun doHook() {
        if (!xprefs.getBoolean("chat_video_player_pro", false)) return

        setupPlayerHooks()

        WppCore.addListenerActivity { activity, state ->
            val isMedia = isMediaViewActivity(activity)
            if (state == WppCore.ActivityChangeState.ChangeType.STARTED ||
                state == WppCore.ActivityChangeState.ChangeType.RESUMED
            ) {
                if (isMedia) {
                    activeMediaActivity = WeakReference(activity)
                    activity.runOnUiThread {
                        runCatching { attachToMediaView(activity) }
                    }
                } else if (state == WppCore.ActivityChangeState.ChangeType.RESUMED &&
                    activeMediaActivity?.get() === activity
                ) {
                    activeMediaActivity = null
                }
            }
        }
    }

    private fun isMediaViewActivity(activity: Activity): Boolean {
        val n = activity.javaClass.simpleName
        return n.contains("MediaView", ignoreCase = true) &&
            !n.contains("StatusPlayback", ignoreCase = true)
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

    private fun stateOf(activity: Activity): MediaState =
        states.getOrPut(activity) { MediaState() }

    private fun resolveMethod(cls: Class<*>, name: String, vararg params: Class<*>): Method? {
        return runCatching { cls.getDeclaredMethod(name, *params) }.getOrNull()
            ?: runCatching { cls.getMethod(name, *params) }.getOrNull()
    }

    private fun setupPlayerHooks() {
        val waCls = runCatching { classLoader.loadClass(WA_HERO_PLAYER_NAME) }.getOrNull()
        if (waCls != null) {
            waStartMethod = resolveMethod(waCls, "start")?.apply { isAccessible = true }
            waSeekMethod = resolveMethod(waCls, "seekTo", Int::class.javaPrimitiveType!!)?.apply { isAccessible = true }
            waGetPosMethod = resolveMethod(waCls, "getCurrentPosition")?.apply { isAccessible = true }
            waGetDurMethod = resolveMethod(waCls, "getDuration")?.apply { isAccessible = true }
            waGetViewMethod = resolveMethod(waCls, "getView")?.apply { isAccessible = true }
        }

        val found = mutableListOf<Method>()
        Unobfuscator.loadHeroPlayerSpeedV4(classLoader)?.apply { isAccessible = true }?.let { found.add(it) }
        Unobfuscator.loadHeroServiceSpeedV4(classLoader)?.apply { isAccessible = true }?.let { m ->
            if (found.none { it.declaringClass == m.declaringClass && it.name == m.name }) found.add(m)
        }
        speedMethods = found

        waGetViewMethod?.hook {
            after {
                val p = instanceOrNull ?: return@after
                val v = result as? View ?: return@after
                viewToWaPlayer[v] = p
            }
        }

        waStartMethod?.hook {
            after {
                val p = instanceOrNull ?: return@after
                waLastStart[p] = SystemClock.uptimeMillis()
                lastWaPlayer = WeakReference(p)
                runCatching {
                    (waGetViewMethod?.invoke(p) as? View)?.let { v -> viewToWaPlayer[v] = p }
                }
                val act = activeMediaActivity?.get()
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    act.runOnUiThread {
                        val st = stateOf(act)
                        if (st.boundPlayerRef?.get() !== p) {
                            st.boundPlayerRef = WeakReference(p)
                            st.speedIdx = 0
                            st.speedPill?.text = SPEED_LABELS[0]
                        }
                        st.speedPill?.visibility = View.VISIBLE
                    }
                }
            }
        }

        runCatching {
            MediaPlayer::class.java.resolve().firstMethod {
                name = "start"
                emptyParameters()
            }.hook {
                after {
                    val mp = instance as? MediaPlayer ?: return@after
                    val act = activeMediaActivity?.get()
                    if (act != null && !act.isFinishing && !act.isDestroyed) {
                        lastMediaPlayer = WeakReference(mp)
                        act.runOnUiThread {
                            stateOf(act).speedPill?.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun attachToMediaView(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val st = stateOf(activity)

        if (content.findViewWithTag<View>(OVERLAY_TAG) == null) {
            val overlay = FrameLayout(activity).apply {
                tag = OVERLAY_TAG
                isClickable = false
                isFocusable = false
            }

            val speedPill = TextView(activity).apply {
                text = SPEED_LABELS[st.speedIdx]
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(activity, 20f)
                    setColor(0x9910161D.toInt())
                    setStroke(dp(activity, 1f).coerceAtLeast(1), 0x59FFFFFF)
                }
                val ph = dp(activity, 13f)
                val pv = dp(activity, 7f)
                setPadding(ph, pv, ph, pv)
                elevation = dpf(activity, 6f)
                visibility = if (findActivePlayer(activity) != null) View.VISIBLE else View.GONE
                setOnClickListener { cycleSpeed(activity) }
            }

            val feedbackTitle = TextView(activity).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            val barFill = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(activity, 4f)
                    setColor(0xFF7BE8B4.toInt())
                }
            }
            val barTrack = FrameLayout(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(activity, 4f)
                    setColor(0x40FFFFFF)
                }
                addView(barFill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
                visibility = View.GONE
            }
            val feedbackBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(activity, 22f)
                    setColor(0xB80E141B.toInt())
                    setStroke(dp(activity, 1f).coerceAtLeast(1), 0x4DFFFFFF)
                }
                setPadding(dp(activity, 18f), dp(activity, 11f), dp(activity, 18f), dp(activity, 11f))
                alpha = 0f
                visibility = View.GONE
                addView(feedbackTitle)
                addView(
                    barTrack,
                    LinearLayout.LayoutParams(dp(activity, 124f), dp(activity, 6f)).apply {
                        topMargin = dp(activity, 7f)
                    }
                )
            }

            overlay.addView(
                speedPill,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dp(activity, 94f)
                    marginEnd = dp(activity, 14f)
                }
            )
            overlay.addView(
                feedbackBox,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            content.addView(
                overlay,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            st.speedPill = speedPill
            st.feedbackBox = feedbackBox
            st.feedbackTitle = feedbackTitle
            st.feedbackBarFill = barFill
            st.feedbackBarTrack = barTrack
        }

        hookWindowTouch(activity)
    }

    private fun hookWindowTouch(activity: Activity) {
        val window = activity.window ?: return
        val current = window.callback ?: return
        if (current is GestureWindowCallback) return
        window.callback = GestureWindowCallback(activity, current)
    }

    private inner class GestureWindowCallback(
        private val activity: Activity,
        private val delegate: android.view.Window.Callback
    ) : android.view.Window.Callback by delegate {

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (handleGesture(activity, ev)) {
                return true
            }
            return delegate.dispatchTouchEvent(ev)
        }
    }

    private fun isInsideView(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return rawX >= loc[0] && rawX <= loc[0] + view.width &&
            rawY >= loc[1] && rawY <= loc[1] + view.height
    }

    private fun handleGesture(activity: Activity, ev: MotionEvent): Boolean {
        val st = stateOf(activity)
        if (isInsideView(st.speedPill, ev.rawX, ev.rawY)) return false

        val player = findActivePlayer(activity)
        if (player != null && st.speedPill?.visibility != View.VISIBLE) {
            st.speedPill?.visibility = View.VISIBLE
        }
        if (player == null) {
            st.dragMode = 0
            return false
        }

        val metrics = activity.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val slop = ViewConfiguration.get(activity).scaledTouchSlop * 1.4f
        val topGuard = dp(activity, 90f).toFloat()
        val botGuard = h - dp(activity, 130f).toFloat()

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                st.downX = ev.rawX
                st.downY = ev.rawY
                st.dragMode = 0
                if (ev.rawY in topGuard..botGuard) {
                    val now = SystemClock.uptimeMillis()
                    val dt = now - st.lastTapTime
                    val dist = abs(ev.rawX - st.lastTapX) + abs(ev.rawY - st.lastTapY)
                    if (dt in 40L..310L && dist < dp(activity, 64f)) {
                        st.lastTapTime = 0L
                        val delta = if (ev.rawX < w * 0.45f) -10_000L
                        else if (ev.rawX > w * 0.55f) 10_000L
                        else 0L
                        if (delta != 0L && player.seekBy(delta)) {
                            val sec = (delta / 1000L).toInt()
                            val label = if (sec > 0) "» +${sec}s" else "« ${sec}s"
                            showFeedback(activity, label, null)
                            return true
                        }
                    } else {
                        st.lastTapTime = now
                        st.lastTapX = ev.rawX
                        st.lastTapY = ev.rawY
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) {
                    st.dragMode = 0
                    return false
                }
                val dx = ev.rawX - st.downX
                val dy = ev.rawY - st.downY
                if (st.dragMode == 0 && st.downY in topGuard..botGuard) {
                    val edgeZone = w * 0.28f
                    val inLeft = st.downX <= edgeZone
                    val inRight = st.downX >= w - edgeZone
                    if ((inLeft || inRight) && abs(dy) > slop && abs(dy) > abs(dx) * 1.6f) {
                        st.dragMode = if (inLeft) 1 else 2
                        if (st.dragMode == 1) {
                            val lp = activity.window.attributes
                            st.startVal = if (lp.screenBrightness in 0f..1f) lp.screenBrightness
                            else runCatching {
                                Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                            }.getOrDefault(0.5f)
                        } else {
                            val am = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            st.maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                            val cur = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
                            st.startVal = cur.toFloat() / st.maxVol.toFloat()
                        }
                    }
                }
                if (st.dragMode != 0) {
                    val deltaRatio = -(dy / (h * 0.48f))
                    val target = (st.startVal + deltaRatio).coerceIn(0f, 1f)
                    val pct = (target * 100f).roundToInt()
                    val tr = isTurkish(activity)
                    if (st.dragMode == 1) {
                        val lp = activity.window.attributes
                        lp.screenBrightness = target.coerceAtLeast(0.02f)
                        activity.window.attributes = lp
                        showFeedback(activity, if (tr) "Parlaklık  %$pct" else "Brightness  $pct%", target)
                    } else {
                        val am = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val targetIdx = (target * st.maxVol).roundToInt().coerceIn(0, st.maxVol)
                        am?.setStreamVolume(AudioManager.STREAM_MUSIC, targetIdx, 0)
                        showFeedback(activity, if (tr) "Ses  %$pct" else "Volume  $pct%", target)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (st.dragMode != 0) {
                    st.dragMode = 0
                    return true
                }
            }
        }
        return false
    }

    private fun showFeedback(activity: Activity, title: String, progress: Float?) {
        val st = stateOf(activity)
        val box = st.feedbackBox ?: return
        val titleTv = st.feedbackTitle ?: return
        val track = st.feedbackBarTrack
        val fill = st.feedbackBarFill

        titleTv.text = title
        if (progress != null && track != null && fill != null) {
            track.visibility = View.VISIBLE
            val trackW = dp(activity, 124f)
            val fillLp = fill.layoutParams
            fillLp.width = (trackW * progress.coerceIn(0f, 1f)).toInt().coerceAtLeast(dp(activity, 4f))
            fill.layoutParams = fillLp
        } else {
            track?.visibility = View.GONE
        }

        st.hideRunnable?.let { st.handler.removeCallbacks(it) }
        box.visibility = View.VISIBLE
        box.animate().cancel()
        box.animate().alpha(1f).setDuration(110L).start()

        val hide = Runnable {
            box.animate().alpha(0f).setDuration(220L).withEndAction {
                box.visibility = View.GONE
            }.start()
        }
        st.hideRunnable = hide
        st.handler.postDelayed(hide, 950L)
    }

    private fun cycleSpeed(activity: Activity) {
        val st = stateOf(activity)
        val tr = isTurkish(activity)
        val player = findActivePlayer(activity) ?: return
        val nextIdx = (st.speedIdx + 1) % SPEEDS.size
        if (player.setSpeed(SPEEDS[nextIdx])) {
            st.speedIdx = nextIdx
            st.speedPill?.text = SPEED_LABELS[nextIdx]
            showFeedback(activity, if (tr) "Hız  ${SPEED_LABELS[nextIdx]}" else "Speed  ${SPEED_LABELS[nextIdx]}", null)
        }
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

        synchronized(waLastStart) {
            val attached = waLastStart.entries
                .sortedByDescending { it.value }
                .firstOrNull { (player, _) ->
                    val v = runCatching { waGetViewMethod?.invoke(player) as? View }.getOrNull()
                    v != null && v.isAttachedToWindow && v.rootView === decor
                }
                ?.key
            if (attached != null) return attached
        }
        return null
    }

    private fun findActivePlayer(activity: Activity): PlayerHandle? {
        findVisibleWaPlayer(activity)?.let { wa ->
            stateOf(activity).boundPlayerRef = WeakReference(wa)
            return wrapWaPlayer(wa)
        }
        val mp = lastMediaPlayer?.get()
        if (mp != null) {
            val alive = runCatching { mp.duration > 0 && mp.isPlaying }.getOrDefault(false)
            if (alive) return wrapMediaPlayer(mp)
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content != null) {
            findVideoView(content)?.let { return wrapVideoView(it) }
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
            if (v is Reference<*>) { enqueue(v.get(), nextDepth); return }
            if (v is AtomicReference<*>) { enqueue(v.get(), nextDepth); return }
            if (v is String || v is Number || v is Boolean || v is Char || v is Enum<*>) return
            if (v is View || v is Context || v is Class<*> || v is Handler || v is Looper || v is Thread) return
            if (v is Collection<*>) {
                var c = 0
                for (item in v) { if (c++ >= 12) break; enqueue(item, nextDepth) }
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
            if (visited.add(v)) queue.addLast(v to nextDepth)
        }

        while (queue.isNotEmpty() && scanned < 650) {
            val (cur, depth) = queue.removeFirst()
            scanned++
            for (m in methods) {
                if (m.declaringClass.isInstance(cur)) return cur to m
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
                    val v = runCatching { f.isAccessible = true; f.get(cur) }.getOrNull() ?: continue
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
                seekM.invoke(player, (cur + deltaMs.toInt()).coerceIn(0, dur))
                true
            }.getOrDefault(false)
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

        override fun setSpeed(speed: Float): Boolean {
            val mpField = runCatching {
                val f = VideoView::class.java.getDeclaredField("mMediaPlayer")
                f.isAccessible = true
                f.get(vv) as? MediaPlayer
            }.getOrNull() ?: return false
            return wrapMediaPlayer(mpField).setSpeed(speed)
        }
    }

    companion object {
        private const val OVERLAY_TAG = "wp_chat_video_pro_overlay"
        private const val WA_HERO_PLAYER_NAME = "com.whatsapp.videoplayback.WaFbHeroPlayer"
        private val SPEEDS = floatArrayOf(1f, 1.25f, 1.5f, 2f, 2.5f, 0.5f)
        private val SPEED_LABELS = arrayOf("1x", "1.25x", "1.5x", "2x", "2.5x", "0.5x")
    }
}
