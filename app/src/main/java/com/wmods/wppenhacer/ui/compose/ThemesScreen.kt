package com.wmods.wppenhacer.ui.compose

import android.content.Context
import android.content.Intent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.material3.Slider
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
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
            sp.getBoolean("miuix_enabled", false) -> "miuix"
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

    val miuixWallpaperEnabled = BoolState(sp, "miuix_wallpaper_enabled", true)
    val miuixWallpaperId = TextState(sp, "miuix_wallpaper_id", "wp1")
    val miuixWallpaperDim = IntState(sp, "miuix_wallpaper_dim", 38)
    val miuixAccentMode = TextState(sp, "miuix_accent_mode", "blue")
    val miuixTypography = BoolState(sp, "miuix_typography", true)
    val miuixDialogs = BoolState(sp, "miuix_dialogs", true)
    val miuixSquircleUi = BoolState(sp, "miuix_squircle_ui", true)
    val miuixBubbles = BoolState(sp, "miuix_bubbles", true)

    fun selectTheme(themeId: String) {
        activeTheme.value = themeId
        val editor = sp.edit()
        when (themeId) {
            "miuix" -> {
                editor.putBoolean("miuix_enabled", true)
                editor.putBoolean("ios27_enabled", false)
                editor.putBoolean("me_enabled", false)
                editor.putBoolean("liquid_glass_chat", false)
                editor.putBoolean("floating_bottom_bar", true)
                editor.putString("fbb_preset", "miuix")
                editor.putInt("floating_bottom_bar_radius", 30)
                editor.putInt("fbb_bottom_margin", 10)
                editor.putInt("fbb_side_margin", 10)
                editor.putInt("fbb_pad_v", 5)
                editor.putInt("fbb_pad_h", 6)
                editor.putString("fbb_indicator", "miuix")
                editor.putBoolean("fbb_glass", true)
                editor.putInt("fbb_glass_alpha", 28)
                editor.putBoolean("fbb_shadow", true)
                editor.putInt("fbb_indicator_color", 0)
                editor.putInt("fbb_icon_selected", 0)
                editor.putInt("fbb_icon_unselected", 0)
            }
            "material_expressive" -> {
                editor.putBoolean("miuix_enabled", false)
                editor.putBoolean("me_enabled", true)
                editor.putBoolean("ios27_enabled", false)
                // Clean up iOS 27 chat glass & apply Material You signature Pill Dock (freely editable afterwards)
                editor.putBoolean("liquid_glass_chat", false)
                editor.putBoolean("floating_bottom_bar", true)
                editor.putString("fbb_preset", "material_pill")
                editor.putInt("floating_bottom_bar_radius", 42)
                editor.putInt("fbb_bottom_margin", 14)
                editor.putInt("fbb_side_margin", 16)
                editor.putInt("fbb_pad_v", 6)
                editor.putInt("fbb_pad_h", 6)
                editor.putString("fbb_indicator", "material_pill")
                editor.putBoolean("fbb_glass", false)
                editor.putBoolean("fbb_shadow", true)
                editor.putInt("fbb_indicator_color", 0)
                editor.putInt("fbb_icon_selected", 0)
                editor.putInt("fbb_icon_unselected", 0)
            }
            "ios27" -> {
                editor.putBoolean("miuix_enabled", false)
                editor.putBoolean("me_enabled", false)
                editor.putBoolean("ios27_enabled", true)
                // Apply iOS 27 signature Liquid Glass Dock + chat islands + iOS context menu
                editor.putBoolean("liquid_glass_chat", true)
                editor.putBoolean("floatingmenu", true)
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
                editor.putBoolean("miuix_enabled", false)
                editor.putBoolean("me_enabled", false)
                editor.putBoolean("ios27_enabled", false)
                editor.putBoolean("liquid_glass_chat", false)
                editor.putBoolean("floating_bottom_bar", false)
                editor.putString("fbb_preset", "classic")
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
            // 0) MIUIx (MIUI 12-14) Theme Card
            ThemePackCard(
                title = stringResource(R.string.theme_miuix_title),
                badge = "BETA",
                description = stringResource(R.string.theme_miuix_desc),
                selected = current == "miuix",
                onSelect = { prefs.selectTheme("miuix") },
                preview = { MiuixMiniPreview(prefs.miuixAccentMode.value == "green") },
                footer = {
                    if (current == "miuix") {
                        Spacer(Modifier.height(10.dp))
                        MiuixOptionsPanel(prefs) {
                            ctx.startActivity(Intent(ctx, FloatingBarActivity::class.java))
                        }
                    }
                }
            )

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
                    if (current == "ios27" || current == "miuix") {
                        Text(
                            text = stringResource(if (current == "miuix") R.string.theme_miuix_conflict_note else R.string.theme_ios27_conflict_note),
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

@Composable
private fun MiuixMiniPreview(greenAccent: Boolean) {
    val accent = if (greenAccent) Color(0xFF25D366) else Color(0xFF0D84FF)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2B6EF3), Color(0xFF5D6FE8), Color(0xFFE38EA8))
                )
            )
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WhatsApp",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.28f)))
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.28f)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.34f),
                    border = BorderStroke(1.3.dp, accent)
                ) {
                    Text("Chats", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.20f)) {
                    Text("Updates", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.26f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                            Box(Modifier.size(width = 14.dp, height = 5.dp).clip(RoundedCornerShape(999.dp)).background(accent))
                        }
                        Box(Modifier.size(width = 14.dp, height = 5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.65f)))
                        Box(Modifier.size(width = 14.dp, height = 5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.65f)))
                    }
                }
                Spacer(Modifier.size(8.dp))
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(accent))
            }
        }
    }
}

@Composable
private fun MiuixOptionsPanel(prefs: ThemesPrefs, onOpenFloatingBar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val wpItems = listOf("none", "wp1", "wp2", "wp3", "wp4")

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.theme_miuix_options_header), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = cs.primary)
            Text(stringResource(R.string.miuix_wallpaper_picker), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wpItems.forEach { wpId ->
                    val selected = if (wpId == "none") {
                        !prefs.miuixWallpaperEnabled.value || prefs.miuixWallpaperId.value == "none"
                    } else {
                        prefs.miuixWallpaperEnabled.value && prefs.miuixWallpaperId.value == wpId
                    }
                    val bmp = remember(wpId) {
                        if (wpId == "none") null else runCatching {
                            ctx.assets.open("miuix_wallpapers/$wpId.jpg").use {
                                BitmapFactory.decodeStream(it)?.asImageBitmap()
                            }
                        }.getOrNull()
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cs.surfaceVariant,
                        border = BorderStroke(if (selected) 2.2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).height(64.dp).clickable {
                            if (wpId == "none") {
                                prefs.miuixWallpaperEnabled.value = false
                                prefs.miuixWallpaperId.value = "none"
                            } else {
                                prefs.miuixWallpaperEnabled.value = true
                                prefs.miuixWallpaperId.value = wpId
                            }
                        }
                    ) {
                        if (bmp != null) {
                            Image(bitmap = bmp, contentDescription = wpId, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.miuix_wallpaper_off), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (prefs.miuixWallpaperEnabled.value && prefs.miuixWallpaperId.value != "none") {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.miuix_wallpaper_dim), style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                        Text("${prefs.miuixWallpaperDim.value}%", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                    Slider(
                        value = prefs.miuixWallpaperDim.value.toFloat(),
                        onValueChange = { prefs.miuixWallpaperDim.value = it.toInt().coerceIn(10, 80) },
                        valueRange = 10f..80f
                    )
                }
            }

            Text(stringResource(R.string.miuix_accent_label), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isBlue = prefs.miuixAccentMode.value != "green"
                Surface(
                    onClick = { prefs.miuixAccentMode.value = "blue" },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isBlue) Color(0xFF0D84FF).copy(alpha = 0.16f) else cs.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(if (isBlue) 1.6.dp else 1.dp, if (isBlue) Color(0xFF0D84FF) else cs.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.miuix_accent_blue), style = MaterialTheme.typography.labelMedium, fontWeight = if (isBlue) FontWeight.Bold else FontWeight.Medium, color = if (isBlue) Color(0xFF0D84FF) else cs.onSurface, modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp))
                }
                Surface(
                    onClick = { prefs.miuixAccentMode.value = "green" },
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isBlue) Color(0xFF25D366).copy(alpha = 0.16f) else cs.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(if (!isBlue) 1.6.dp else 1.dp, if (!isBlue) Color(0xFF25D366) else cs.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.miuix_accent_green), style = MaterialTheme.typography.labelMedium, fontWeight = if (!isBlue) FontWeight.Bold else FontWeight.Medium, color = if (!isBlue) Color(0xFF25D366) else cs.onSurface, modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp))
                }
            }

            ThemeToggleRow(stringResource(R.string.miuix_opt_misans), prefs.miuixTypography.value) { prefs.miuixTypography.value = it }
            ThemeToggleRow(stringResource(R.string.miuix_opt_dialogs), prefs.miuixDialogs.value) { prefs.miuixDialogs.value = it }
            ThemeToggleRow(stringResource(R.string.miuix_opt_squircle), prefs.miuixSquircleUi.value) { prefs.miuixSquircleUi.value = it }
            ThemeToggleRow(stringResource(R.string.miuix_opt_bubbles), prefs.miuixBubbles.value) { prefs.miuixBubbles.value = it }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onOpenFloatingBar, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.miuix_tweak_floating_bar))
            }
        }
    }
}
