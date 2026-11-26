package com.wmods.wppenhacer.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CqStorePrompt {

    private const val STORE_PACKAGE = "com.cq61tech.cqstore"
    private const val STORE_URL = "https://github.com/cq61tech/cq-store/releases/latest"
    private const val PREFS_NAME = "cq_store_prompt"
    private const val KEY_DISMISSED = "dismissed"

    private var shownInSession = false

    fun showIfNeeded(activity: Activity) {
        if (shownInSession || activity.isFinishing || activity.isDestroyed) return

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DISMISSED, false)) return
        if (isStoreInstalled(activity)) return

        shownInSession = true

        val body = "Get all my modules, updates, and changelogs in one place instead of checking GitHub releases manually."
        val sub = "Don't miss any updates or new apps!"

        val message = SpannableStringBuilder().apply {
            append(body)
            append("\n\n")
            val start = length
            append(sub)
            setSpan(RelativeSizeSpan(0.85f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("We strongly recommend using CQ Store")
            .setMessage(message)
            .setPositiveButton("Get CQ Store") { _, _ ->
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL)))
                }
            }
            .setNegativeButton("Later", null)
            .setNeutralButton("Don't show again") { _, _ ->
                prefs.edit().putBoolean(KEY_DISMISSED, true).apply()
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun isStoreInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    STORE_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(STORE_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
