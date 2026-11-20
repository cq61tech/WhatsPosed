package com.wmods.wppenhacer.ui.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.highcapable.yukihookapi.YukiHook
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private sealed interface UpdateState {
    data object Checking : UpdateState
    data object Failed : UpdateState
    data object Current : UpdateState
    data class Available(val tag: String) : UpdateState
}

private fun fetchUpdate(): UpdateState =
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://api.github.com/repos/cq61tech/WhatsPosed/releases/latest")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use UpdateState.Failed
            val tag = JSONObject(response.body?.string().orEmpty()).optString("tag_name", "")
            if (tag.isBlank()) return@use UpdateState.Current
            val parts = tag.split("-")
            val hash = if (parts.size > 1) parts[1].trim() else ""
            val isNew = hash.isNotEmpty() &&
                !BuildConfig.VERSION_NAME.lowercase().contains(hash.lowercase())
            if (isNew) UpdateState.Available(tag) else UpdateState.Current
        }
    } catch (_: Exception) {
        UpdateState.Failed
    }

private fun isSupportedVersion(version: String?, supported: List<String>): Boolean =
    version != null && supported.any { version.startsWith(it.replace(".xx", "")) }

private fun installedVersion(ctx: Context, pkg: String): String? =
    try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName
    } catch (_: Exception) {
        null
    }

@Composable
private fun Tile(
    modifier: Modifier,
    container: Color,
    content: Color,
    icon: ImageVector,
    title: String,
    summary: String,
    actionIcon: ImageVector? = null,
    onAction: () -> Unit = {},
) {
    Surface(modifier = modifier, shape = CardShape, color = container, contentColor = content) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(icon, null, Modifier.size(30.dp))
                if (actionIcon != null) {
                    IconButton(onClick = onAction, Modifier.size(32.dp)) { Icon(actionIcon, null) }
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HomeContent(
    lifecycle: Lifecycle,
    onSearch: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onTools: () -> Unit,
    onBattery: () -> Unit,
    onFloatingBar: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        YukiHook.Status.observeFrameworkService(lifecycle) { tick++ }
        onDispose { }
    }
    val active = remember(tick) { YukiHook.Status.isModuleActive }

    var wppVersion by remember { mutableStateOf<String?>(null) }
    var bizVersion by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val pkg = i.getStringExtra("PKG")
                val v = i.getStringExtra("VERSION") ?: ""
                if (pkg == FeatureLoader.PACKAGE_WPP) wppVersion = v else bizVersion = v
            }
        }
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter("${BuildConfig.APPLICATION_ID}.RECEIVER_WPP"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ctx.sendBroadcast(Intent("${BuildConfig.APPLICATION_ID}.CHECK_WPP"))
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }

    var update by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    LaunchedEffect(Unit) { update = withContext(Dispatchers.IO) { fetchUpdate() } }

    val wppSupported = remember { ctx.resources.getStringArray(R.array.supported_versions_wpp).toList() }
    val bizSupported = remember { ctx.resources.getStringArray(R.array.supported_versions_business).toList() }
    val wppInstalled = remember { installedVersion(ctx, FeatureLoader.PACKAGE_WPP) }
    val bizInstalled = remember { installedVersion(ctx, FeatureLoader.PACKAGE_BUSINESS) }
    val showWpp = App.isOriginalPackage
    val batteryOk = remember {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.mipmap.launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = onSearch) { Icon(WpIcons.Search, null) }
            IconButton(onClick = onAppearance) { Icon(WpIcons.Palette, null) }
            IconButton(onClick = onAbout) { Icon(WpIcons.Info, null) }
        }

        Surface(
            shape = CardShape,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        ) {
            Box {
                Image(
                    painterResource(R.drawable.hero_sky),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))),
                    ),
                )
                Text(
                    stringResource(R.string.wp_hero_tagline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(22.dp),
                )
            }
        }

        Surface(
            shape = CardShape,
            color = if (active) cs.primaryContainer else cs.errorContainer,
            contentColor = if (active) cs.onPrimaryContainer else cs.onErrorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(
                    if (active) WpIcons.CheckCircle else WpIcons.Warning,
                    null,
                    Modifier.size(40.dp),
                )
                Column {
                    Text(
                        stringResource(if (active) R.string.module_enabled else R.string.module_disabled),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.version_s, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showWpp) {
                val v = wppVersion
                val ok = isSupportedVersion(v, wppSupported)
                Tile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    container = when {
                        v == null -> cs.surfaceContainerHigh
                        ok -> cs.secondaryContainer
                        else -> cs.tertiaryContainer
                    },
                    content = when {
                        v == null -> cs.onSurface
                        ok -> cs.onSecondaryContainer
                        else -> cs.onTertiaryContainer
                    },
                    icon = if (v != null) WpIcons.CheckCircle else WpIcons.Warning,
                    title = stringResource(R.string.whatsapp_in_background),
                    summary = when {
                        v == null -> stringResource(R.string.whatsapp_is_not_running_or_has_not_been_activated_in_lsposed)
                        ok -> stringResource(R.string.version_s, v)
                        else -> stringResource(R.string.version_s_not_listed, v)
                    },
                    actionIcon = if (v != null) WpIcons.Refresh else null,
                    onAction = { App.instance.restartApp(FeatureLoader.PACKAGE_WPP) },
                )
            }
            if (!showWpp || bizVersion != null) {
                val v = bizVersion
                val ok = isSupportedVersion(v, bizSupported)
                Tile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    container = when {
                        v == null -> cs.surfaceContainerHigh
                        ok -> cs.secondaryContainer
                        else -> cs.tertiaryContainer
                    },
                    content = when {
                        v == null -> cs.onSurface
                        ok -> cs.onSecondaryContainer
                        else -> cs.onTertiaryContainer
                    },
                    icon = if (v != null) WpIcons.CheckCircle else WpIcons.Warning,
                    title = stringResource(R.string.business_in_background),
                    summary = when {
                        v == null -> stringResource(R.string.business_is_not_running_or_has_not_been_activated_in_lsposed)
                        ok -> stringResource(R.string.version_s, v)
                        else -> stringResource(R.string.version_s_not_listed, v)
                    },
                    actionIcon = if (v != null) WpIcons.Refresh else null,
                    onAction = { App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS) },
                )
            }
        }

        val u = update
        Surface(
            shape = CardShape,
            color = when (u) {
                is UpdateState.Available -> cs.tertiaryContainer
                UpdateState.Failed -> cs.errorContainer
                else -> cs.surfaceContainerHigh
            },
            contentColor = when (u) {
                is UpdateState.Available -> cs.onTertiaryContainer
                UpdateState.Failed -> cs.onErrorContainer
                else -> cs.onSurface
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    when (u) {
                        is UpdateState.Available -> WpIcons.Update
                        UpdateState.Failed -> WpIcons.Warning
                        else -> WpIcons.CheckCircle
                    },
                    null,
                    Modifier.size(32.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when (u) {
                                is UpdateState.Available -> R.string.update_available
                                UpdateState.Failed -> R.string.update_check_failed
                                else -> R.string.up_to_date
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (u) {
                            is UpdateState.Available -> stringResource(R.string.update_available_summary, u.tag)
                            UpdateState.Failed -> stringResource(R.string.update_check_failed_summary)
                            else -> stringResource(R.string.current_version_s, BuildConfig.VERSION_NAME)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.wpui_packages)) {
            InfoRow(
                "WhatsApp",
                wppInstalled?.let { if (isSupportedVersion(it, wppSupported)) it else "$it  •  " + stringResource(R.string.wpui_unsupported) }
                    ?: stringResource(R.string.wpui_not_installed),
            )
            InfoRow(
                "WhatsApp Business",
                bizInstalled?.let { if (isSupportedVersion(it, bizSupported)) it else "$it  •  " + stringResource(R.string.wpui_unsupported) }
                    ?: stringResource(R.string.wpui_not_installed),
            )
            InfoRow(stringResource(R.string.wpui_supported_wpp), wppSupported.joinToString(", "))
            InfoRow(stringResource(R.string.wpui_supported_business), bizSupported.joinToString(", "))
        }

        SectionCard(stringResource(R.string.wpui_device)) {
            InfoRow(stringResource(R.string.wpui_device_name), "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
            InfoRow(stringResource(R.string.wpui_codename), Build.DEVICE)
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }

        SectionCard(stringResource(R.string.wpfb_title)) {
            Text(
                stringResource(R.string.wpfb_home_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            WideOutlinedButton(stringResource(R.string.wpfb_open), onClick = onFloatingBar)
        }

        SectionCard(stringResource(R.string.wpui_tools)) {
            Text(
                stringResource(R.string.wpui_tools_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            WideOutlinedButton(stringResource(R.string.wpui_open_tools), onClick = onTools)
            if (!batteryOk) {
                WideOutlinedButton(stringResource(R.string.wpui_battery), onClick = onBattery)
            }
        }
    }
}

@Composable
fun HomeScreen(
    lifecycle: Lifecycle,
    onSearch: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onTools: () -> Unit,
    onBattery: () -> Unit,
    onFloatingBar: () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HomeContent(lifecycle, onSearch, onAppearance, onAbout, onTools, onBattery, onFloatingBar)
    }
}
