package com.wmods.wppenhacer.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.FeatureLoader

private class PreviewTab(val icon: ImageVector, val label: Int)

private val previewTabs = listOf(
    PreviewTab(WpIcons.Chat, R.string.wpfb_tab_chats),
    PreviewTab(WpIcons.Star, R.string.wpfb_tab_updates),
    PreviewTab(WpIcons.Dashboard, R.string.wpfb_tab_communities),
    PreviewTab(WpIcons.Mic, R.string.wpfb_tab_calls),
)

@Composable
private fun MockBackdrop(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.background(
            Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(5) { i ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (i % 2 == 0) cs.primary else cs.tertiary),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.height(10.dp).fillMaxWidth(0.55f).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.35f)))
                        Box(Modifier.height(8.dp).fillMaxWidth(0.8f).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.18f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarPreview(p: FloatingBarPrefs) {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(p.radius.value.dp)
    val bottom = p.bottomMargin.value.coerceAtMost(40).dp
    val side = p.sideMargin.value.coerceAtMost(40).dp
    val iconDp = if (p.iconSize.value > 0) p.iconSize.value else 24
    val selectedColor = if (p.iconSelected.value != 0) Color(p.iconSelected.value) else cs.onSurface
    val unselectedColor = if (p.iconUnselected.value != 0) Color(p.iconUnselected.value) else cs.onSurfaceVariant
    val indicatorColor = if (p.indicatorColor.value != 0) Color(p.indicatorColor.value) else cs.secondaryContainer
    val indicatorShape = if (p.indicator.value == "oval") CircleShape else RoundedCornerShape(16.dp)

    Surface(shape = CardShape, color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(260.dp).clip(CardShape)) {
            val w = maxWidth
            val h = maxHeight
            MockBackdrop(Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = side, end = side, bottom = bottom)
                    .shadow(
                        if (p.shadow.value) 8.dp else 0.dp, shape,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    )
                    .clip(shape),
            ) {
                if (p.glass.value) {
                    MockBackdrop(
                        Modifier
                            .requiredSize(w, h)
                            .align(Alignment.BottomCenter)
                            .offset(y = bottom)
                            .graphicsLayer { renderEffect = BlurEffect(28f, 28f, TileMode.Clamp) },
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            cs.surface.copy(alpha = p.glassAlpha.value / 100f),
                        ),
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)),
                        ),
                    )
                    Box(
                        Modifier.matchParentSize().border(
                            BorderStroke(
                                1.2.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = if (dark) 0.55f else 0.8f), Color.White.copy(alpha = 0.12f)),
                                ),
                            ),
                            shape,
                        ),
                    )
                } else {
                    Box(
                        Modifier.matchParentSize()
                            .background(cs.surfaceContainerHigh.copy(alpha = 0.92f))
                            .border(BorderStroke(1.5.dp, cs.onSurface.copy(alpha = 0.22f)), shape),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = (6 + p.padH.value).dp,
                            vertical = (6 + p.padV.value).dp,
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    previewTabs.forEachIndexed { i, tab ->
                        val sel = i == 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .clip(indicatorShape)
                                    .background(if (sel) indicatorColor else Color.Transparent)
                                    .padding(horizontal = 18.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    tab.icon, null, Modifier.size(iconDp.dp),
                                    tint = if (sel) selectedColor else unselectedColor,
                                )
                            }
                            if (!p.hideLabels.value) {
                                Text(
                                    stringResource(tab.label),
                                    fontSize = (if (p.textSize.value > 0) p.textSize.value else 11).sp,
                                    maxLines = 1,
                                    color = if (sel) selectedColor else unselectedColor,
                                    modifier = Modifier.padding(top = (2 + p.labelGap.value).dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntSlider(
    label: String,
    state: IntState,
    range: IntRange,
    unit: String,
    enabled: Boolean = true,
    autoZero: Boolean = false,
) {
    val autoLabel = stringResource(R.string.wpfb_auto)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (autoZero && state.value == 0) autoLabel else "${state.value}$unit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = state.value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { state.value = it.toInt() },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun ColorRow(label: String, state: IntState) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (state.value != 0) Color(state.value) else MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.wpfb_color_choose)) }
        if (state.value != 0) {
            TextButton(onClick = { state.value = 0 }) { Text(stringResource(R.string.wpfb_color_default)) }
        }
    }
    if (open) {
        ColorDialog(
            initial = state.value,
            onDismiss = { open = false },
            onPick = {
                state.value = it
                open = false
            },
        )
    }
}

@Composable
private fun ColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(if (initial != 0) initial else 0xFF2E7D32.toInt(), hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wpfb_color_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(argb)),
                )
                Text(stringResource(R.string.wpfb_hue))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.wpfb_saturation))
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text(stringResource(R.string.wpfb_brightness))
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(argb) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun PresetRow(selected: Boolean, title: String, summary: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.padding(start = 6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun FloatingBarScreen(prefs: FloatingBarPrefs, onBack: () -> Unit) {
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(WpIcons.Back, null, Modifier.size(26.dp)) }
            Text(
                stringResource(R.string.wpfb_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(stringResource(R.string.wpfb_preview), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.wpfb_preview_sum),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BarPreview(prefs)

        SectionCard(null) {
            SwitchRow(
                stringResource(R.string.wpfb_enable),
                stringResource(R.string.wpfb_enable_sum),
                prefs.enabled.value,
            ) { prefs.enabled.value = it }
        }

        SectionCard(stringResource(R.string.wpfb_preset)) {
            Text(
                stringResource(R.string.wpfb_preset_sum),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PresetRow(
                prefs.preset.value == "classic",
                stringResource(R.string.wpfb_preset_classic),
                stringResource(R.string.wpfb_preset_classic_sum),
            ) { prefs.applyPreset("classic") }
            PresetRow(
                prefs.preset.value == "enhanced",
                stringResource(R.string.wpfb_preset_enhanced),
                stringResource(R.string.wpfb_preset_enhanced_sum),
            ) { prefs.applyPreset("enhanced") }
            PresetRow(
                prefs.preset.value == "glass",
                stringResource(R.string.wpfb_preset_glass),
                stringResource(R.string.wpfb_preset_glass_sum),
            ) { prefs.applyPreset("glass") }
            PresetRow(
                prefs.preset.value == "liquid",
                stringResource(R.string.wpfb_preset_liquid),
                stringResource(R.string.wpfb_preset_liquid_sum),
            ) { prefs.applyPreset("liquid") }
            PresetRow(
                prefs.preset.value == "material_pill",
                stringResource(R.string.wpfb_preset_material_pill),
                stringResource(R.string.wpfb_preset_material_pill_sum),
            ) { prefs.applyPreset("material_pill") }
            PresetRow(
                prefs.preset.value == "miuix",
                stringResource(R.string.wpfb_preset_miuix),
                stringResource(R.string.wpfb_preset_miuix_sum),
            ) { prefs.applyPreset("miuix") }
        }

        SectionCard(stringResource(R.string.wpfb_geometry)) {
            Text(
                stringResource(R.string.wpfb_geometry_sum),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IntSlider(stringResource(R.string.wpfb_radius), prefs.radius, 0..48, " dp")
            IntSlider(stringResource(R.string.wpfb_bottom_margin), prefs.bottomMargin, 0..48, " dp")
            IntSlider(stringResource(R.string.wpfb_side_margin), prefs.sideMargin, 0..40, " dp")
            IntSlider(stringResource(R.string.wpfb_pad_v), prefs.padV, 0..16, " dp")
            IntSlider(stringResource(R.string.wpfb_pad_h), prefs.padH, 0..32, " dp")
            IntSlider(stringResource(R.string.wpfb_icon_size), prefs.iconSize, 0..40, " dp", autoZero = true)
        }

        SectionCard(stringResource(R.string.wpfb_indicator)) {
            Text(stringResource(R.string.wpfb_indicator_type), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("default" to R.string.wpfb_indicator_default, "oval" to R.string.wpfb_indicator_oval)
                    .forEachIndexed { i, (id, res) ->
                        SegmentedButton(
                            selected = prefs.indicator.value == id,
                            onClick = { prefs.indicator.value = id },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            label = { Text(stringResource(res)) },
                        )
                    }
            }
            ColorRow(stringResource(R.string.wpfb_indicator_color), prefs.indicatorColor)
            ColorRow(stringResource(R.string.wpfb_icon_selected), prefs.iconSelected)
            ColorRow(stringResource(R.string.wpfb_icon_unselected), prefs.iconUnselected)
        }

        SectionCard(stringResource(R.string.wpfb_labels)) {
            SwitchRow(
                stringResource(R.string.wpfb_hide_labels),
                stringResource(R.string.wpfb_hide_labels_sum),
                prefs.hideLabels.value,
            ) { prefs.hideLabels.value = it }
            IntSlider(stringResource(R.string.wpfb_text_size), prefs.textSize, 0..18, " sp", enabled = !prefs.hideLabels.value, autoZero = true)
            IntSlider(stringResource(R.string.wpfb_label_gap), prefs.labelGap, 0..12, " dp", enabled = !prefs.hideLabels.value)
        }

        SectionCard(stringResource(R.string.wpfb_behavior)) {
            Text(
                stringResource(R.string.wpfb_behavior_sum),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                stringResource(R.string.wpfb_glass_effect),
                stringResource(R.string.wpfb_glass_effect_sum),
                prefs.glass.value,
            ) { prefs.glass.value = it }
            IntSlider(stringResource(R.string.wpfb_glass_opacity), prefs.glassAlpha, 5..90, " %", enabled = prefs.glass.value)
            SwitchRow(
                stringResource(R.string.wpfb_shadow),
                stringResource(R.string.wpfb_shadow_sum),
                prefs.shadow.value,
            ) { prefs.shadow.value = it }
            SwitchRow(
                stringResource(R.string.wpfb_hide_scroll),
                stringResource(R.string.wpfb_hide_scroll_sum),
                prefs.hideScroll.value,
            ) { prefs.hideScroll.value = it }
            if (prefs.hideScroll.value) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("slide" to R.string.wpfb_hide_slide, "fade" to R.string.wpfb_hide_fade)
                        .forEachIndexed { i, (id, res) ->
                            SegmentedButton(
                                selected = prefs.hideMode.value == id,
                                onClick = { prefs.hideMode.value = id },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                label = { Text(stringResource(res)) },
                            )
                        }
                }
            }
        }

        SectionCard(null) {
            Text(
                stringResource(R.string.wpfb_restart_sum),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    App.instance.restartApp(
                        if (App.isOriginalPackage) FeatureLoader.PACKAGE_WPP else FeatureLoader.PACKAGE_BUSINESS,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.wpfb_restart)) }
            WideOutlinedButton(stringResource(R.string.wpfb_reset)) { prefs.reset() }
        }
    }
}
