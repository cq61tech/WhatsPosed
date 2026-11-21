package com.wmods.wppenhacer.ui.compose

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

class ElasticMessagesPrefs(ctx: Context) {
    private val sp = PreferenceManager.getDefaultSharedPreferences(ctx)

    val enabled = BoolState(sp, "elastic_message_press", false)
    val stretch = IntState(sp, "elastic_stretch_amount", 120)
    val bounce = IntState(sp, "elastic_bounce_amount", 125)
    val jelly = IntState(sp, "elastic_jelly_amount", 120)
    val tilt = IntState(sp, "elastic_tilt_amount", 100)
    val holdDelay = IntState(sp, "elastic_hold_delay", 95)
    val haptic = BoolState(sp, "elastic_haptic", true)

    fun applyPreset(id: String) {
        when (id) {
            "soft" -> {
                stretch.value = 75
                bounce.value = 70
                jelly.value = 75
                tilt.value = 60
                holdDelay.value = 120
            }
            "jelly" -> {
                stretch.value = 125
                bounce.value = 135
                jelly.value = 140
                tilt.value = 110
                holdDelay.value = 95
            }
            "bouncy" -> {
                stretch.value = 165
                bounce.value = 185
                jelly.value = 165
                tilt.value = 140
                holdDelay.value = 80
            }
            else -> reset()
        }
    }

    fun reset() {
        stretch.value = 120
        bounce.value = 125
        jelly.value = 120
        tilt.value = 100
        holdDelay.value = 95
        haptic.value = true
    }
}

@Composable
fun ElasticMessagesScreen(prefs: ElasticMessagesPrefs, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    val c = cs.onBackground
                    val sw = 2.3.dp.toPx()
                    val midY = size.height * 0.5f
                    drawLine(c, Offset(size.width * 0.84f, midY), Offset(size.width * 0.20f, midY), sw, StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.46f, size.height * 0.22f), Offset(size.width * 0.18f, midY), sw, StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.46f, size.height * 0.78f), Offset(size.width * 0.18f, midY), sw, StrokeCap.Round)
                }
            }
            Text(
                text = stringResource(R.string.elastic_message_press),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            TextButton(onClick = { prefs.reset() }) {
                Text(stringResource(R.string.elastic_reset))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Interactive live preview card
            ElasticPreviewCard(prefs)

            // Master enable card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (prefs.enabled.value) cs.primaryContainer else cs.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.elastic_enable_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (prefs.enabled.value) cs.onPrimaryContainer else cs.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.elastic_message_press_sum),
                            style = MaterialTheme.typography.bodySmall,
                            color = (if (prefs.enabled.value) cs.onPrimaryContainer else cs.onSurfaceVariant).copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = prefs.enabled.value,
                        onCheckedChange = { prefs.enabled.value = it }
                    )
                }
            }

            // Presets row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = prefs.stretch.value == 75 && prefs.bounce.value == 70,
                    onClick = { prefs.applyPreset("soft") },
                    label = { Text(stringResource(R.string.elastic_preset_soft)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = prefs.stretch.value == 125 && prefs.bounce.value == 135,
                    onClick = { prefs.applyPreset("jelly") },
                    label = { Text(stringResource(R.string.elastic_preset_jelly)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = prefs.stretch.value == 165 && prefs.bounce.value == 185,
                    onClick = { prefs.applyPreset("bouncy") },
                    label = { Text(stringResource(R.string.elastic_preset_bouncy)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Physics sliders card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cs.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElasticSliderItem(
                        title = stringResource(R.string.elastic_stretch_amount),
                        summary = stringResource(R.string.elastic_stretch_amount_sum),
                        value = prefs.stretch.value,
                        range = 30..200,
                        suffix = "%",
                        onChange = { prefs.stretch.value = it }
                    )
                    ElasticSliderItem(
                        title = stringResource(R.string.elastic_bounce_amount),
                        summary = stringResource(R.string.elastic_bounce_amount_sum),
                        value = prefs.bounce.value,
                        range = 0..200,
                        suffix = "%",
                        onChange = { prefs.bounce.value = it }
                    )
                    ElasticSliderItem(
                        title = stringResource(R.string.elastic_jelly_amount),
                        summary = stringResource(R.string.elastic_jelly_amount_sum),
                        value = prefs.jelly.value,
                        range = 0..200,
                        suffix = "%",
                        onChange = { prefs.jelly.value = it }
                    )
                    ElasticSliderItem(
                        title = stringResource(R.string.elastic_tilt_amount),
                        summary = stringResource(R.string.elastic_tilt_amount_sum),
                        value = prefs.tilt.value,
                        range = 0..200,
                        suffix = "%",
                        onChange = { prefs.tilt.value = it }
                    )
                    ElasticSliderItem(
                        title = stringResource(R.string.elastic_hold_delay),
                        summary = stringResource(R.string.elastic_hold_delay_sum),
                        value = prefs.holdDelay.value,
                        range = 40..250,
                        suffix = " ms",
                        onChange = { prefs.holdDelay.value = it }
                    )
                }
            }

            // Haptic toggle card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cs.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.elastic_haptic),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface
                        )
                        Text(
                            text = stringResource(R.string.elastic_haptic_sum),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.haptic.value,
                        onCheckedChange = { prefs.haptic.value = it }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ElasticSliderItem(
    title: String,
    summary: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onChange: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary
            )
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange((it / 5f).roundToInt() * 5) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun ElasticPreviewCard(prefs: ElasticMessagesPrefs) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0E1722),
                            Color(0xFF152436),
                            Color(0xFF0E1A26)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.elastic_preview_hint),
                    color = Color(0xB8DDE8F5),
                    fontSize = 11.5.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InteractivePreviewBubble(
                    text = stringResource(R.string.elastic_preview_msg1),
                    time = "23:54",
                    isOutgoing = false,
                    prefs = prefs,
                    modifier = Modifier.align(Alignment.Start)
                )
                InteractivePreviewBubble(
                    text = stringResource(R.string.elastic_preview_msg2),
                    time = "23:55 ✓✓",
                    isOutgoing = true,
                    prefs = prefs,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun InteractivePreviewBubble(
    text: String,
    time: String,
    isOutgoing: Boolean,
    prefs: ElasticMessagesPrefs,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val view = LocalView.current

    val stretchScale by rememberUpdatedState(prefs.stretch.value / 100f)
    val bounceScale by rememberUpdatedState(prefs.bounce.value / 100f)
    val jellyScale by rememberUpdatedState(prefs.jelly.value / 100f)
    val tiltScale by rememberUpdatedState(prefs.tilt.value / 100f)
    val hapticOn by rememberUpdatedState(prefs.haptic.value)

    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var sx by remember { mutableFloatStateOf(1f) }
    var sy by remember { mutableFloatStateOf(1f) }
    var rot by remember { mutableFloatStateOf(0f) }
    var rx by remember { mutableFloatStateOf(0f) }
    var ry by remember { mutableFloatStateOf(0f) }

    var dragging by remember { mutableStateOf(false) }
    var targetTx by remember { mutableFloatStateOf(0f) }
    var targetTy by remember { mutableFloatStateOf(0f) }
    var targetSx by remember { mutableFloatStateOf(1f) }
    var targetSy by remember { mutableFloatStateOf(1f) }
    var targetRot by remember { mutableFloatStateOf(0f) }
    var targetRx by remember { mutableFloatStateOf(0f) }
    var targetRy by remember { mutableFloatStateOf(0f) }

    val vel = remember { FloatArray(7) } // tx, ty, sx, sy, rot, rx, ry

    LaunchedEffect(Unit) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { ns ->
                if (lastNs == 0L) {
                    lastNs = ns
                    return@withFrameNanos
                }
                val dt = ((ns - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
                lastNs = ns
                val sub = dt * 0.5f

                val bNorm = (bounceScale / 2f).coerceIn(0f, 1f)
                val kPos = if (dragging) 410f else 390f
                val cPos = if (dragging) 27f else 36f * (0.11f.pow(bNorm))
                val kScale = if (dragging) 380f else 440f
                val cScale = if (dragging) 24f else 34f * (0.105f.pow(bNorm))
                val kTilt = if (dragging) 340f else 360f
                val cTilt = if (dragging) 22f else 32f * (0.12f.pow(bNorm))

                repeat(2) {
                    var a = -kPos * (tx - targetTx) - cPos * vel[0]; vel[0] += a * sub; tx += vel[0] * sub
                    a = -kPos * (ty - targetTy) - cPos * vel[1]; vel[1] += a * sub; ty += vel[1] * sub
                    a = -kScale * (sx - targetSx) - cScale * vel[2]; vel[2] += a * sub; sx += vel[2] * sub
                    a = -kScale * (sy - targetSy) - cScale * vel[3]; vel[3] += a * sub; sy += vel[3] * sub
                    a = -kTilt * (rot - targetRot) - cTilt * vel[4]; vel[4] += a * sub; rot += vel[4] * sub
                    a = -kTilt * (rx - targetRx) - cTilt * vel[5]; vel[5] += a * sub; rx += vel[5] * sub
                    a = -kTilt * (ry - targetRy) - cTilt * vel[6]; vel[6] += a * sub; ry += vel[6] * sub
                }
            }
        }
    }

    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = tx
                translationY = ty
                scaleX = sx.coerceIn(0.76f, 1.28f)
                scaleY = sy.coerceIn(0.76f, 1.28f)
                rotationZ = rot.coerceIn(-9f, 9f)
                rotationX = rx.coerceIn(-16f, 16f)
                rotationY = ry.coerceIn(-16f, 16f)
                cameraDistance = 18f * density
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        dragDx = 0f
                        dragDy = 0f
                        if (hapticOn) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDx += dragAmount.x
                        dragDy += dragAmount.y
                        val maxX = (40f * stretchScale * density).coerceAtLeast(10f)
                        val maxY = (26f * stretchScale * density).coerceAtLeast(10f)
                        val ntx = (tanh((dragDx / (maxX * 1.55f)).toDouble()) * maxX).toFloat()
                        val nty = (tanh((dragDy / (maxY * 1.75f)).toDouble()) * maxY).toFloat()
                        val pull = hypot(ntx / maxX, nty / maxY).coerceIn(0f, 1.3f)
                        val domX = (abs(ntx) - abs(nty)) / (abs(ntx) + abs(nty) + 1f)
                        val deform = 0.135f * jellyScale * pull * (0.30f + 0.70f * domX)
                        val nsx = (1f + deform).coerceIn(0.82f, 1.24f)
                        val nsy = (1f / nsx).coerceIn(0.80f, 1.22f)

                        targetTx = ntx
                        targetTy = nty
                        targetSx = nsx
                        targetSy = nsy
                        targetRot = (ntx / maxX) * 3.2f * tiltScale
                        targetRy = (ntx / maxX) * 8.0f * tiltScale
                        targetRx = -(nty / maxY) * 6.8f * tiltScale
                    },
                    onDragEnd = {
                        val maxX = (40f * stretchScale * density).coerceAtLeast(10f)
                        val maxY = (26f * stretchScale * density).coerceAtLeast(10f)
                        val pull = min(hypot(targetTx / maxX, targetTy / maxY), 1.2f).coerceAtLeast(0.4f)
                        val dir = sign(if (dragDx != 0f) dragDx else targetTx)
                        val kick = (0.55f + 1.15f * pull) * bounceScale * jellyScale
                        if (abs(tx) >= abs(ty)) {
                            vel[2] -= 1.85f * kick
                            vel[3] += 1.75f * kick
                        } else {
                            vel[2] += 1.65f * kick
                            vel[3] -= 1.80f * kick
                        }
                        vel[4] -= dir * 11f * pull * bounceScale * tiltScale
                        vel[6] -= dir * 15f * pull * bounceScale * tiltScale
                        dragging = false
                        targetTx = 0f; targetTy = 0f
                        targetSx = 1f; targetSy = 1f
                        targetRot = 0f; targetRx = 0f; targetRy = 0f
                        if (hapticOn) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    onDragCancel = {
                        dragging = false
                        targetTx = 0f; targetTy = 0f
                        targetSx = 1f; targetSy = 1f
                        targetRot = 0f; targetRx = 0f; targetRy = 0f
                    }
                )
            }
            .background(
                color = if (isOutgoing) Color(0xFF0E8068) else Color(0xFF243446),
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isOutgoing) 18.dp else 6.dp,
                    bottomEnd = if (isOutgoing) 6.dp else 18.dp
                )
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = text, color = Color.White, fontSize = 14.sp)
            Text(text = time, color = Color(0xB3FFFFFF), fontSize = 10.5.sp)
        }
    }
}
