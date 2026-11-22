package com.wmods.wppenhacer.ui.compose

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.FloatingBarActivity
import com.wmods.wppenhacer.activities.ManualColorsActivity
import com.wmods.wppenhacer.activities.MaterialExpressiveActivity

class ThemesPrefs(ctx: Context) {
    private val sp = PreferenceManager.getDefaultSharedPreferences(ctx)

    private val initialTheme: String = run {
        val saved = sp.getString("active_ui_theme", null)
        when {
            !saved.isNullOrBlank() -> saved
            sp.getBoolean("ios27_enabled", false) -> "ios27"
            sp.getBoolean("me_enabled", false) -> "material_expressive"
            else -> "default"
        }
    }

    val activeTheme = TextState(sp, "active_ui_theme", initialTheme)

    // iOS 27 fine-grained toggles
    val ios27OledCanvas = BoolState(sp, "ios27_oled_canvas", true)
    val ios27HeaderSearch = BoolState(sp, "ios27_header_search", true)
    val ios27GlassChat = BoolState(sp, "ios27_glass_chat", true)
    val ios27GlassOverlays = BoolState(sp, "ios27_glass_overlays", true)
    val ios27GlassFabs = BoolState(sp, "ios27_glass_fabs", true)
    val ios27Bubbles = BoolState(sp, "ios27_bubbles", true)
    val ios27Typography = BoolState(sp, "ios27_typography", true)
    val ios27ContactCards = BoolState(sp, "ios27_contact_cards", true)

    fun selectTheme(themeId: String) {
        activeTheme.value = themeId
        val editor = sp.edit()
        when (themeId) {
            "material_expressive" -> {
                editor.putBoolean("me_enabled", true)
                editor.putBoolean("ios27_enabled", false)
            }
            "ios27" -> {
                editor.putBoolean("me_enabled", false)
                editor.putBoolean("ios27_enabled", true)
                // Auto-enable full iOS 27 Liquid Glass navigation + chat islands
                editor.putBoolean("liquid_glass_chat", true)
                editor.putBoolean("floating_bottom_bar", true)
                editor.putString("fbb_preset", "liquid")
                editor.putInt("floating_bottom_bar_radius", 34)
                editor.putInt("fbb_bottom_margin", 18)
                editor.putInt("fbb_side_margin", 14)
                editor.putInt("fbb_pad_v", 4)
                editor.putInt("fbb_pad_h", 6)
                editor.putString("fbb_indicator", "oval")
                editor.putBoolean("fbb_glass", true)
                editor.putInt("fbb_glass_alpha", 22)
                editor.putBoolean("fbb_shadow", true)
                editor.putInt("fbb_indicator_color", 0)
                editor.putInt("fbb_icon_selected", 0)
                editor.putInt("fbb_icon_unselected", 0)
            }
            else -> {
                editor.putBoolean("me_enabled", false)
                editor.putBoolean("ios27_enabled", false)
            }
        }
        editor.apply()
    }
}

@Composable
fun ThemesScreen(prefs: ThemesPrefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val current = prefs.activeTheme.value

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
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.themes_center_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground
                )
                Text(
                    text = stringResource(R.string.themes_center_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1) iOS 27 (Liquid Glass) Theme Card
            ThemePackCard(
                title = stringResource(R.string.theme_ios27_title),
                badge = "NEW",
                description = stringResource(R.string.theme_ios27_desc),
                selected = current == "ios27",
                onSelect = { prefs.selectTheme("ios27") },
                preview = { Ios27MiniPreview() },
                footer = {
                    if (current == "ios27") {
                        Spacer(Modifier.height(10.dp))
                        Ios27OptionsPanel(prefs) {
                            ctx.startActivity(Intent(ctx, FloatingBarActivity::class.java))
                        }
                    }
                }
            )

            // 2) Material 3 Expressive (Android 16 QPR2) Card
            ThemePackCard(
                title = stringResource(R.string.wpme_title),
                badge = "A16",
                description = stringResource(R.string.wpme_entry_summary),
                selected = current == "material_expressive",
                onSelect = { prefs.selectTheme("material_expressive") },
                preview = { MaterialExpressiveMiniPreview() },
                footer = {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (current != "material_expressive") {
                                    prefs.selectTheme("material_expressive")
                                }
                                ctx.startActivity(Intent(ctx, MaterialExpressiveActivity::class.java))
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.theme_customize_btn))
                        }
                    }
                }
            )

            // 3) Default (Stock WhatsApp) Card
            ThemePackCard(
                title = stringResource(R.string.theme_default_title),
                badge = null,
                description = stringResource(R.string.theme_default_desc),
                selected = current == "default",
                onSelect = { prefs.selectTheme("default") },
                preview = null,
                footer = null
            )

            // 4) Unified Tools Card: Floating Bar + Manual Colors/Monet/Bubbles
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cs.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.theme_tools_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    if (current == "ios27") {
                        Text(
                            text = stringResource(R.string.theme_ios27_conflict_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.primary
                        )
                    }
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, FloatingBarActivity::class.java)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wpfb_title))
                    }
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, ManualColorsActivity::class.java)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.theme_manual_colors_title))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemePackCard(
    title: String,
    badge: String?,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    preview: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (selected) cs.primaryContainer.copy(alpha = 0.36f) else cs.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (preview != null) {
                preview()
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        if (badge != null) {
                            Box(
                                modifier = Modifier
                                    .background(cs.primary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badge,
                                    color = cs.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) cs.primary else cs.surfaceVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (selected) stringResource(R.string.theme_active_badge) else stringResource(R.string.theme_apply_btn),
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (footer != null) footer()
        }
    }
}

@Composable
private fun Ios27MiniPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF000000), Color(0xFF0A141E), Color(0xFF000000))
                )
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top iOS Large Title + Glass Search Pill
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chats",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF30D158))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0x33FFFFFF))
                        .border(0.8.dp, Color(0x40FFFFFF), RoundedCornerShape(11.dp))
                )
            }

            // Middle mock chat bubbles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF1F2225))
                        .border(0.7.dp, Color(0x2BFFFFFF), RoundedCornerShape(11.dp))
                )
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF005C4B))
                        .border(0.7.dp, Color(0x33FFFFFF), RoundedCornerShape(11.dp))
                )
            }

            // Bottom Liquid Glass floating tab bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x55FFFFFF), Color(0x28FFFFFF))
                        )
                    )
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x4D30D158))
                    )
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0x88FFFFFF)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0x88FFFFFF)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0x88FFFFFF)))
                }
            }
        }
    }
}

@Composable
private fun MaterialExpressiveMiniPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101720))
            .border(1.dp, Color(0x2A78B8FF), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                    .background(Color(0xFF1D2938))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(Color(0xFF1D2938))
            )
        }
    }
}

@Composable
private fun Ios27OptionsPanel(prefs: ThemesPrefs, onOpenFloatingBar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_ios27_options_header),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_oled),
                checked = prefs.ios27OledCanvas.value,
                onChange = { prefs.ios27OledCanvas.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_header_search),
                checked = prefs.ios27HeaderSearch.value,
                onChange = { prefs.ios27HeaderSearch.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_glass_chat),
                checked = prefs.ios27GlassChat.value,
                onChange = { prefs.ios27GlassChat.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_glass_overlays),
                checked = prefs.ios27GlassOverlays.value,
                onChange = { prefs.ios27GlassOverlays.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_glass_fabs),
                checked = prefs.ios27GlassFabs.value,
                onChange = { prefs.ios27GlassFabs.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_bubbles),
                checked = prefs.ios27Bubbles.value,
                onChange = { prefs.ios27Bubbles.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_typography),
                checked = prefs.ios27Typography.value,
                onChange = { prefs.ios27Typography.value = it }
            )
            ThemeToggleRow(
                title = stringResource(R.string.ios27_opt_contact_cards),
                checked = prefs.ios27ContactCards.value,
                onChange = { prefs.ios27ContactCards.value = it }
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onOpenFloatingBar,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ios27_tweak_floating_bar))
            }
        }
    }
}

@Composable
private fun ThemeToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
