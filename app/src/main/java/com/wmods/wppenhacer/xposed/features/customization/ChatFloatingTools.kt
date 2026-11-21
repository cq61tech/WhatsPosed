package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs

class ChatFloatingTools(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private data class ToolEntry(val title: String, val action: (Activity) -> Unit)

    private class BomberSession(
        val handler: Handler = Handler(Looper.getMainLooper()),
        var active: Boolean = false
    )

    private val sessions = WeakHashMap<Activity, BomberSession>()

    override fun getPluginName(): String = "ChatFloatingTools"

    override fun doHook() {
        if (!xprefs.getBoolean("chat_floating_tools", false)) return

        WppCore.addListenerActivity { activity, state ->
            if (activity.javaClass.simpleName != "Conversation") return@addListenerActivity
            when (state) {
                WppCore.ActivityChangeState.ChangeType.RESUMED -> {
                    activity.runOnUiThread { attachBubble(activity) }
                }
                WppCore.ActivityChangeState.ChangeType.PAUSED -> {
                    activity.runOnUiThread { stopBomber(activity) }
                }
                WppCore.ActivityChangeState.ChangeType.DESTROYED -> {
                    activity.runOnUiThread { detachBubble(activity) }
                }
                else -> Unit
            }
        }
    }

    private fun isTurkish(): Boolean =
        Locale.getDefault().language.equals("tr", ignoreCase = true)

    private fun buildTools(): List<ToolEntry> = listOf(
        ToolEntry("Message Bomber") { showBomberDialog(it) }
    )

    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubble(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (root.findViewWithTag<View>(BUBBLE_TAG) != null) return

        val density = activity.resources.displayMetrics.density
        val size = (48f * density).toInt()
        val slop = 10f * density

        val label = TextView(activity).apply {
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        val bubble = FrameLayout(activity).apply {
            tag = BUBBLE_TAG
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_IDLE)
            }
            elevation = 6f * density
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        bubble.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxX = (root.width - v.width).coerceAtLeast(0).toFloat()
                        val maxY = (root.height - v.height).coerceAtLeast(0).toFloat()
                        v.x = (startX + dx).coerceIn(0f, maxX)
                        v.y = (startY + dy).coerceIn(0f, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.performClick()
                        onBubbleClick(activity)
                    }
                    true
                }
                else -> false
            }
        }

        val params = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = (12f * density).toInt()
        }
        root.addView(bubble, params)
    }

    private fun onBubbleClick(activity: Activity) {
        if (sessions[activity]?.active == true) {
            stopBomber(activity)
            Toast.makeText(
                activity,
                if (isTurkish()) "Durduruldu" else "Stopped",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showToolsMenu(activity)
    }

    private fun showToolsMenu(activity: Activity) {
        val tools = buildTools()
        val titles = tools.map { it.title }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(if (isTurkish()) "Sohbet Araçları" else "Chat Tools")
            .setItems(titles) { _, which ->
                tools[which].action(activity)
            }
            .show()
    }

    private fun showBomberDialog(activity: Activity) {
        val tr = isTurkish()
        val pad = (20f * activity.resources.displayMetrics.density).toInt()

        val messageInput = EditText(activity).apply {
            hint = if (tr) "Mesaj" else "Message"
        }
        val countInput = EditText(activity).apply {
            hint = if (tr) "Tekrar sayısı" else "Repeat count"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val delayInput = EditText(activity).apply {
            hint = if (tr) "Gecikme (ms)" else "Delay (ms)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("250")
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(messageInput)
            addView(countInput)
            addView(delayInput)
        }

        AlertDialog.Builder(activity)
            .setTitle("Message Bomber")
            .setView(container)
            .setNegativeButton(if (tr) "İptal" else "Cancel", null)
            .setPositiveButton(if (tr) "Başlat" else "Start") { _, _ ->
                val text = messageInput.text?.toString()?.trim().orEmpty()
                val count = countInput.text?.toString()?.toIntOrNull() ?: 0
                val delay = (delayInput.text?.toString()?.toLongOrNull() ?: 250L).coerceAtLeast(80L)
                if (text.isEmpty() || count <= 0) {
                    Toast.makeText(
                        activity,
                        if (tr) "Geçerli mesaj ve sayı girin" else "Enter valid message and count",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                startBomber(activity, text, count, delay)
            }
            .show()
    }

    private fun startBomber(activity: Activity, text: String, total: Int, delayMs: Long) {
        val entryId = activity.resources.getIdentifier("entry", "id", activity.packageName)
        val sendId = activity.resources.getIdentifier("send", "id", activity.packageName)
        if (entryId == 0 || sendId == 0 || activity.findViewById<EditText>(entryId) == null) {
            Toast.makeText(
                activity,
                if (isTurkish()) "Mesaj kutusu bulunamadı" else "Message input not found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val session = sessions.getOrPut(activity) { BomberSession() }
        session.handler.removeCallbacksAndMessages(null)
        session.active = true
        updateBubbleState(activity, true)
        runBomberStep(activity, session, entryId, sendId, text, total, delayMs)
    }

    private fun runBomberStep(
        activity: Activity,
        session: BomberSession,
        entryId: Int,
        sendId: Int,
        text: String,
        remaining: Int,
        delayMs: Long
    ) {
        if (!session.active || remaining <= 0 || activity.isFinishing || activity.isDestroyed) {
            stopBomber(activity)
            return
        }
        val input = activity.findViewById<EditText>(entryId)
        if (input == null) {
            stopBomber(activity)
            return
        }
        input.setText(text)
        input.setSelection(text.length)
        session.handler.postDelayed({
            if (!session.active || activity.isFinishing || activity.isDestroyed) return@postDelayed
            val sendBtn = activity.findViewById<View>(sendId)
            if (sendBtn != null && sendBtn.visibility == View.VISIBLE) {
                sendBtn.performClick()
            }
            val next = remaining - 1
            if (next > 0 && session.active) {
                session.handler.postDelayed({
                    runBomberStep(activity, session, entryId, sendId, text, next, delayMs)
                }, delayMs)
            } else {
                stopBomber(activity)
            }
        }, 60L)
    }

    private fun stopBomber(activity: Activity) {
        sessions[activity]?.let {
            it.active = false
            it.handler.removeCallbacksAndMessages(null)
        }
        updateBubbleState(activity, false)
    }

    private fun detachBubble(activity: Activity) {
        stopBomber(activity)
        sessions.remove(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(BUBBLE_TAG)?.let { root.removeView(it) }
    }

    private fun updateBubbleState(activity: Activity, running: Boolean) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bubble = root.findViewWithTag<FrameLayout>(BUBBLE_TAG) ?: return
        (bubble.background as? GradientDrawable)?.setColor(if (running) COLOR_RUNNING else COLOR_IDLE)
        (bubble.getChildAt(0) as? TextView)?.text = if (running) "×" else "+"
    }

    companion object {
        private const val BUBBLE_TAG = "wp_chat_floating_tools"
        private const val COLOR_IDLE = 0xFF25D366.toInt()
        private const val COLOR_RUNNING = 0xFFE53935.toInt()
    }
}
