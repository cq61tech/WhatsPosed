package com.wmods.wppenhacer

import android.app.Activity
import android.util.Log
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import io.noties.markwon.Markwon
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UpdateChecker(private val mActivity: Activity) : Runnable {

    data class ReleaseInfo(
        val version: String,
        val changelog: String,
        val publishedAt: String,
        val releaseUrl: String,
        val fetchedAtMs: Long,
    )

    companion object {
        private const val LOG_TAG = "WhatsPosedUpdate"
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/cq61tech/WhatsPosed/releases/latest"
        private const val RELEASES_URL =
            "https://github.com/cq61tech/WhatsPosed/releases/latest"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        @Volatile
        private var cachedRelease: ReleaseInfo? = null
        private val dialogShowing = AtomicBoolean(false)

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        private fun logInfo(msg: String) {
            Log.i(LOG_TAG, msg)
            runCatching { YukiLog.log("[$LOG_TAG] $msg") }
        }

        private fun logError(msg: String, tr: Throwable? = null) {
            Log.e(LOG_TAG, msg, tr)
            runCatching {
                YukiLog.log("[$LOG_TAG] ERROR: $msg" + (tr?.let { " -> ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
                if (tr != null) YukiLog.log(tr)
            }
        }

        fun cleanVersion(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.trim()
                .substringBefore(" ")
                .removePrefix("v")
                .removePrefix("V")
                .trim()
        }

        private data class ParsedSemVer(val parts: List<Int>, val isPreRelease: Boolean)

        private fun parseSemVer(raw: String?): ParsedSemVer? {
            val cleaned = cleanVersion(raw)
            if (cleaned.isEmpty()) return null
            val isPre = cleaned.contains("-")
            val core = cleaned.substringBefore("-")
            val nums = core.split(".").map { seg ->
                seg.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            }
            if (nums.isEmpty()) return null
            return ParsedSemVer(nums, isPre)
        }

        /**
         * Compares plain release tags like "0.6.6" against installed names like
         * "0.6.6 (B66CF1B4)" or "0.6.6-beta (B66CF1B4)".
         */
        fun isRemoteNewer(remoteTag: String?, localVersionName: String?): Boolean {
            val remote = parseSemVer(remoteTag) ?: return false
            val local = parseSemVer(localVersionName) ?: return false
            val maxLen = maxOf(remote.parts.size, local.parts.size)
            for (i in 0 until maxLen) {
                val r = remote.parts.getOrElse(i) { 0 }
                val l = local.parts.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            // Same numeric version: stable remote (0.6.6) is newer than local pre-release (0.6.6-beta)
            return !remote.isPreRelease && local.isPreRelease
        }

        fun fetchLatestRelease(): ReleaseInfo? {
            val now = System.currentTimeMillis()
            val cached = cachedRelease
            if (cached != null && now - cached.fetchedAtMs < CACHE_TTL_MS) {
                logInfo("Using cached release: version=${cached.version}")
                return cached
            }

            logInfo("Checking latest release from $LATEST_RELEASE_API")
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "WhatsPosed/${cleanVersion(BuildConfig.VERSION_NAME)}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                logInfo("GitHub API HTTP ${response.code}")
                if (!response.isSuccessful) {
                    logError("GitHub API request failed with HTTP ${response.code}")
                    return null
                }
                val content = response.body?.string().orEmpty()
                val release = JSONObject(content)
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    logInfo("Skipping draft/prerelease tag=${release.optString("tag_name")}")
                    return null
                }
                val rawTag = release.optString("tag_name", "").trim()
                val version = cleanVersion(rawTag)
                if (version.isBlank()) {
                    logError("Empty tag_name in release payload")
                    return null
                }
                val info = ReleaseInfo(
                    version = version,
                    changelog = release.optString("body", "No changelog available.").trim()
                        .ifEmpty { "No changelog available." },
                    publishedAt = release.optString("published_at", ""),
                    releaseUrl = release.optString("html_url", "").ifBlank { RELEASES_URL },
                    fetchedAtMs = now
                )
                cachedRelease = info
                logInfo("Fetched release: tag='$rawTag' normalized='$version' published='${info.publishedAt}'")
                return info
            }
        }
    }

    override fun run() {
        try {
            val release = fetchLatestRelease() ?: return

            val localVersion = runCatching {
                mActivity.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0).versionName
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME

            val isNewVersion = isRemoteNewer(release.version, localVersion)
            val ignoredVersion = WppCore.getPrivString("ignored_version", "")
            val isIgnored = cleanVersion(ignoredVersion) == release.version

            logInfo(
                "Compare local='$localVersion' vs remote='${release.version}' -> " +
                    "isNew=$isNewVersion, ignored='$ignoredVersion' (isIgnored=$isIgnored)"
            )

            if (isNewVersion && !isIgnored) {
                mActivity.runOnUiThread {
                    if (mActivity.isFinishing || mActivity.isDestroyed) {
                        logInfo("Activity finishing/destroyed, skipping update dialog")
                        return@runOnUiThread
                    }
                    if (!dialogShowing.compareAndSet(false, true)) {
                        logInfo("Update dialog already showing, skipping duplicate")
                        return@runOnUiThread
                    }
                    showUpdateDialog(release, localVersion)
                }
            }
        } catch (e: Exception) {
            logError("Update check failed", e)
        }
    }

    private fun showUpdateDialog(release: ReleaseInfo, localVersion: String) {
        try {
            logInfo("Showing update popup for v${release.version} (installed: $localVersion)")
            val markwon = Markwon.create(mActivity)
            val dialog = AlertDialogWpp(mActivity)
            val formattedDate = formatPublishedDate(release.publishedAt)

            val message = buildString {
                append("📦 **New Version:** `").append(release.version).append("`\n")
                append("📱 **Installed:** `").append(cleanVersion(localVersion)).append("`\n")
                if (formattedDate.isNotEmpty()) {
                    append("📅 **Released:** ").append(formattedDate).append("\n")
                }
                append("\n### What's New\n\n").append(release.changelog)
            }

            dialog.setTitle("🎉 WhatsPosed Update Available!")
            dialog.setMessage(markwon.toMarkdown(message))
            dialog.setNegativeButton("Ignore") { d, _ ->
                logInfo("User ignored version ${release.version}")
                WppCore.setPrivString("ignored_version", release.version)
                dialogShowing.set(false)
                d.dismiss()
            }
            dialog.setPositiveButton("Update Now") { d, _ ->
                logInfo("User tapped Update Now -> ${release.releaseUrl}")
                dialogShowing.set(false)
                Utils.openLink(mActivity, release.releaseUrl)
                d.dismiss()
            }
            dialog.setOnDismissListener {
                dialogShowing.set(false)
            }
            dialog.show()
        } catch (e: Exception) {
            dialogShowing.set(false)
            logError("Failed to display update dialog", e)
        }
    }

    private fun formatPublishedDate(isoDate: String?): String {
        if (isoDate.isNullOrEmpty()) return ""
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = isoFormat.parse(isoDate)
            if (date != null) {
                SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
            } else ""
        } catch (e: Exception) {
            logError("Date parse failed for '$isoDate'", e)
            ""
        }
    }
}
