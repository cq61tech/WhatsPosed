package com.wmods.wppenhacer.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmods.wppenhacer.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialExpressiveScreen(prefs: MaterialExpressivePrefs, onBack: () -> Unit) {
    val active = prefs.enabled.value

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                BackArrowIcon(MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wpme_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.wpme_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExpressivePreviewCard(prefs)

        SectionCard(title = stringResource(R.string.wpme_section_master)) {
            SwitchRow(
                title = stringResource(R.string.wpme_enable),
                summary = stringResource(R.string.wpme_enable_sum),
                checked = active,
                onChecked = { prefs.enabled.value = it },
            )

            val presets = listOf(
                "qpr2" to stringResource(R.string.wpme_preset_qpr2),
                "subtle" to stringResource(R.string.wpme_preset_subtle),
                "full" to stringResource(R.string.wpme_preset_full),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                presets.forEachIndexed { index, (id, label) ->
                    SegmentedButton(
                        selected = prefs.preset.value == id,
                        onClick = { prefs.applyPreset(id) },
                        enabled = active,
                        shape = SegmentedButtonDefaults.itemShape(index, presets.size),
                    ) {
                        Text(label, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.wpme_section_color)) {
            val sources = listOf(
                "monet" to stringResource(R.string.wpme_source_monet),
                "neutral" to stringResource(R.string.wpme_source_neutral),
                "custom" to stringResource(R.string.wpme_source_custom),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                sources.forEachIndexed { index, (id, label) ->
                    val allow = active && (id != "monet" || prefs.monetAvailable)
                    SegmentedButton(
                        selected = prefs.colorSource.value == id,
                        onClick = { prefs.colorSource.value = id },
                        enabled = allow,
                        shape = SegmentedButtonDefaults.itemShape(index, sources.size),
                    ) {
                        Text(label, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }

            SliderRow(
                label = stringResource(R.string.wpme_monet_intensity),
                value = prefs.monetIntensity.value.toFloat(),
                range = 0f..100f,
                unit = "%",
                enabled = active && prefs.colorSource.value != "neutral",
                onChange = { prefs.monetIntensity.value = it.roundToInt() },
            )

            if (prefs.monetAvailable) {
                SwitchRow(
                    title = stringResource(R.string.wpme_sync_accent),
                    summary = stringResource(R.string.wpme_sync_accent_sum),
                    checked = prefs.syncAccentEngine,
                    enabled = active,
                    onChecked = { prefs.syncAccentEngine = it },
                )
            }
        }

        SectionCard(title = stringResource(R.string.wpme_section_geometry)) {
            val styles = listOf(
                "segmented" to stringResource(R.string.wpme_style_segmented),
                "rounded" to stringResource(R.string.wpme_style_rounded),
                "soft" to stringResource(R.string.wpme_style_soft),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                styles.forEachIndexed { index, (id, label) ->
                    SegmentedButton(
                        selected = prefs.cardStyle.value == id,
                        onClick = { prefs.cardStyle.value = id },
                        enabled = active,
                        shape = SegmentedButtonDefaults.itemShape(index, styles.size),
                    ) {
                        Text(label, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }

            SliderRow(
                label = stringResource(R.string.wpme_corner_radius),
                value = prefs.cornerRadius.value.toFloat(),
                range = 10f..30f,
                unit = "dp",
                enabled = active,
                onChange = { prefs.cornerRadius.value = it.roundToInt() },
            )

            SliderRow(
                label = stringResource(R.string.wpme_card_gap),
                value = prefs.cardGap.value.toFloat(),
                range = 0f..8f,
                unit = "dp",
                enabled = active,
                onChange = { prefs.cardGap.value = it.roundToInt() },
            )

            SwitchRow(
                title = stringResource(R.string.wpme_card_outline),
                summary = stringResource(R.string.wpme_card_outline_sum),
                checked = prefs.cardOutline.value,
                enabled = active,
                onChecked = { prefs.cardOutline.value = it },
            )

            SwitchRow(
                title = stringResource(R.string.wpme_hide_dividers),
                summary = stringResource(R.string.wpme_hide_dividers_sum),
                checked = prefs.hideDividers.value,
                enabled = active,
                onChecked = { prefs.hideDividers.value = it },
            )
        }

        SectionCard(title = stringResource(R.string.wpme_section_surfaces)) {
            SwitchRow(
                title = stringResource(R.string.wpme_chats_cards),
                summary = stringResource(R.string.wpme_chats_cards_sum),
                checked = prefs.chatsCards.value,
                enabled = active,
                onChecked = { prefs.chatsCards.value = it },
            )
            SwitchRow(
                title = stringResource(R.string.wpme_settings_cards),
                summary = stringResource(R.string.wpme_settings_cards_sum),
                checked = prefs.settingsCards.value,
                enabled = active,
                onChecked = { prefs.settingsCards.value = it },
            )
            SwitchRow(
                title = stringResource(R.string.wpme_search_pill),
                summary = stringResource(R.string.wpme_search_pill_sum),
                checked = prefs.searchPill.value,
                enabled = active,
                onChecked = { prefs.searchPill.value = it },
            )
            SwitchRow(
                title = stringResource(R.string.wpme_chat_composer),
                summary = stringResource(R.string.wpme_chat_composer_sum),
                checked = prefs.chatComposer.value,
                enabled = active,
                onChecked = { prefs.chatComposer.value = it },
            )
            SwitchRow(
                title = stringResource(R.string.wpme_expressive_fab),
                summary = stringResource(R.string.wpme_expressive_fab_sum),
                checked = prefs.expressiveFab.value,
                enabled = active,
                onChecked = { prefs.expressiveFab.value = it },
            )
            SwitchRow(
                title = stringResource(R.string.wpme_dialogs),
                summary = stringResource(R.string.wpme_dialogs_sum),
                checked = prefs.styleDialogs.value,
                enabled = active,
                onChecked = { prefs.styleDialogs.value = it },
            )
            SwitchRow(
                title = stringResource(R.string.wpme_sync_bubble),
                summary = stringResource(R.string.wpme_sync_bubble_sum),
                checked = prefs.syncBubble.value,
                enabled = active,
                onChecked = { prefs.syncBubble.value = it },
            )
        }

        Text(
            text = stringResource(R.string.wpme_restart_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        WideOutlinedButton(text = stringResource(R.string.wpme_reset)) {
            prefs.reset()
        }
    }
}

@Composable
private fun BackArrowIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.2.dp.toPx()
        val cy = size.height / 2f
        val left = size.width * 0.2f
        val right = size.width * 0.82f
        val head = size.width * 0.48f
        drawLine(color, Offset(right, cy), Offset(left, cy), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, cy), Offset(head, size.height * 0.22f), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, cy), Offset(head, size.height * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ExpressivePreviewCard(prefs: MaterialExpressivePrefs) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val factor = if (prefs.colorSource.value == "neutral") 0f else (prefs.monetIntensity.value / 100f)

    val neutralCanvas = if (dark) Color(0xFF0E1215) else Color(0xFFF5F6F7)
    val neutralCard = if (dark) Color(0xFF1A1F23) else Color(0xFFFFFFFF)
    val neutralHigh = if (dark) Color(0xFF23292E) else Color(0xFFEBEFF2)

    val canvasColor = lerp(neutralCanvas, scheme.background, factor)
    val cardColor = lerp(neutralCard, scheme.surfaceContainer, factor)
    val pillColor = lerp(neutralHigh, scheme.surfaceContainerHigh, factor)
    val accentColor = lerp(Color(0xFF1DAA61), scheme.primary, factor)
    val badgeBg = lerp(Color(0xFF1F3D2F), scheme.primaryContainer, factor)
    val outlineColor = scheme.outlineVariant.copy(alpha = if (prefs.cardOutline.value) 0.45f else 0f)

    val outer = when (prefs.cardStyle.value) {
        "soft" -> (prefs.cornerRadius.value * 0.7f).coerceIn(10f, 20f)
        else -> prefs.cornerRadius.value.toFloat()
    }
    val inner = if (prefs.cardStyle.value == "segmented") 5f else outer
    val gap = if (prefs.chatsCards.value) prefs.cardGap.value.dp else 0.dp

    Surface(
        shape = CardShape,
        color = canvasColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), CardShape),
    ) {
        Box(Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "WhatsApp",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(pillColor))
                        Box(Modifier.size(18.dp).clip(CircleShape).background(pillColor))
                    }
                }

                if (prefs.searchPill.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(pillColor)
                            .border(1.dp, outlineColor, RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .width(84.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (i in 0 until 3) {
                        val shape = if (!prefs.chatsCards.value) {
                            RoundedCornerShape(0.dp)
                        } else when (i) {
                            0 -> RoundedCornerShape(topStart = outer.dp, topEnd = outer.dp, bottomStart = inner.dp, bottomEnd = inner.dp)
                            2 -> RoundedCornerShape(topStart = inner.dp, topEnd = inner.dp, bottomStart = outer.dp, bottomEnd = outer.dp)
                            else -> RoundedCornerShape(inner.dp)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (prefs.chatsCards.value) cardColor else Color.Transparent)
                                .border(if (prefs.chatsCards.value && prefs.cardOutline.value) 1.dp else 0.dp, outlineColor, shape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (i == 0) badgeBg else pillColor)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    Modifier
                                        .width((76 + i * 16).dp)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(scheme.onSurface.copy(alpha = 0.72f))
                                )
                                Box(
                                    Modifier
                                        .width((118 - i * 14).dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(scheme.onSurfaceVariant.copy(alpha = 0.42f))
                                )
                            }
                            if (i == 0) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            val fabShape = if (prefs.expressiveFab.value) RoundedCornerShape(14.dp) else CircleShape
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp)
                    .clip(fabShape)
                    .background(badgeBg)
                    .border(1.dp, accentColor.copy(alpha = 0.35f), fabShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
            }
        }
    }
}
