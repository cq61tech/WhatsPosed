package com.wmods.wppenhacer.ui.compose

import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wmods.wppenhacer.R
import java.io.File

@Composable
fun AppearanceScreen(prefs: WpPrefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tFontImported = stringResource(R.string.wpui_font_imported)
    val tFailed = stringResource(R.string.wpui_failed)
    val autoLabel = stringResource(R.string.wpui_font_auto)
    val defaultLabel = stringResource(R.string.wpui_font_default)
    var fontTick by remember { mutableIntStateOf(0) }
    val fontOptions = remember(fontTick, autoLabel, defaultLabel) {
        AppFonts.options(ctx, autoLabel, defaultLabel)
    }
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                var name = "font"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) name = c.getString(i) ?: name
                    }
                }
                name = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (!name.endsWith(".ttf", true) && !name.endsWith(".otf", true)) name += ".ttf"
                val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, name).outputStream().use { input.copyTo(it) }
                }
                AppFonts.clearCache()
                prefs.fontId.value = "file:$name"
                fontTick++
            }.isSuccess
            Toast.makeText(ctx, if (ok) tFontImported else tFailed, Toast.LENGTH_SHORT).show()
        }
    }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(WpIcons.Back, null, Modifier.size(26.dp)) }
            Text(
                stringResource(R.string.wpui_appearance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        SectionCard(stringResource(R.string.wpui_theme)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(
                    0 to R.string.wpui_theme_system,
                    2 to R.string.wpui_theme_light,
                    1 to R.string.wpui_theme_dark,
                )
                modes.forEachIndexed { i, (mode, res) ->
                    SegmentedButton(
                        selected = prefs.themeMode == mode,
                        onClick = { prefs.themeMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        label = { Text(stringResource(res)) },
                    )
                }
            }
            SwitchRow(
                stringResource(R.string.wpui_monet),
                stringResource(R.string.wpui_monet_summary),
                prefs.monet,
                enabled = Build.VERSION.SDK_INT >= 31,
            ) { prefs.monet = it }
            if (!prefs.monet || Build.VERSION.SDK_INT < 31) {
                Text(stringResource(R.string.wpui_accent), style = MaterialTheme.typography.bodyLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "green" to R.string.wpui_color_green,
                        "blue" to R.string.wpui_color_blue,
                        "cyan" to R.string.wpui_color_cyan,
                        "purple" to R.string.wpui_color_purple,
                        "orange" to R.string.wpui_color_orange,
                        "red" to R.string.wpui_color_red,
                        "pink" to R.string.wpui_color_pink,
                    ).forEach { (id, res) ->
                        FilterChip(
                            selected = prefs.preset == id,
                            onClick = { prefs.preset = id },
                            label = { Text(stringResource(res)) },
                        )
                    }
                }
            }
            SwitchRow(stringResource(R.string.wpui_amoled), null, prefs.amoled.value) { prefs.amoled.value = it }
        }

        SectionCard(stringResource(R.string.wpui_font)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fontOptions.forEach { o ->
                    FilterChip(
                        selected = prefs.fontId.value == o.id,
                        onClick = { prefs.fontId.value = o.id },
                        label = { Text(o.label) },
                    )
                }
            }
            WideOutlinedButton(stringResource(R.string.wpui_font_import)) {
                importFont.launch(arrayOf("*/*"))
            }
            SliderRow(stringResource(R.string.wpui_font_weight), prefs.fontWeight.value, 100f..900f, "") { prefs.fontWeight.value = it }
            SliderRow(stringResource(R.string.wpui_font_round), prefs.fontRound.value, 0f..100f, "%") { prefs.fontRound.value = it }
            SliderRow(stringResource(R.string.wpui_font_scale), prefs.fontScale.value, 80f..130f, "%") { prefs.fontScale.value = it }
            SliderRow(stringResource(R.string.wpui_font_spacing), prefs.fontSpacing.value, -50f..100f, " ‰") { prefs.fontSpacing.value = it }
            Text(stringResource(R.string.wp_hero_tagline), style = MaterialTheme.typography.headlineSmall)
        }

        SectionCard(stringResource(R.string.wpui_bottom_bar)) {
            SwitchRow(
                stringResource(R.string.wpui_dock_floating),
                stringResource(R.string.wpui_dock_floating_summary),
                prefs.dockFloating.value,
            ) { prefs.dockFloating.value = it }
            SliderRow(stringResource(R.string.wpui_dock_width), prefs.dockWidth.value, 50f..100f, "%", enabled = prefs.dockFloating.value) { prefs.dockWidth.value = it }
            SliderRow(stringResource(R.string.wpui_dock_height), prefs.dockHeight.value, 48f..96f, " dp") { prefs.dockHeight.value = it }
            SliderRow(stringResource(R.string.wpui_dock_opacity), prefs.dockOpacity.value, 30f..100f, "%") { prefs.dockOpacity.value = it }
            SliderRow(stringResource(R.string.wpui_dock_text), prefs.dockTextSize.value, 8f..14f, " sp", enabled = prefs.dockLabels.value) { prefs.dockTextSize.value = it }
            SliderRow(stringResource(R.string.wpui_dock_bottom), prefs.dockBottom.value, 0f..32f, " dp", enabled = prefs.dockFloating.value) { prefs.dockBottom.value = it }
            SwitchRow(stringResource(R.string.wpui_dock_labels), null, prefs.dockLabels.value) { prefs.dockLabels.value = it }
        }
    }
}
