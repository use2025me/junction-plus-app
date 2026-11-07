package com.junctionplus.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Checks GitHub for a newer release when the app opens. If there is one, it tells
 * the user and opens the download link. The app never installs anything itself:
 * Android's own installer does that, so the app needs no install permission.
 */
class UpdateChecker(private val activity: Activity) {

    private val apiUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
    private val apkUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest/download/JunctionPlus.apk"

    fun check() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "JunctionPlusApp")
                if (conn.responseCode != 200) return@execute
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.optString("tag_name") // e.g. "v1.0.12"
                val latest = tag.substringAfterLast('.').toIntOrNull() ?: return@execute
                if (latest > BuildConfig.VERSION_CODE) {
                    val notes = json.optString("body").lineSequence().firstOrNull().orEmpty().take(200)
                    activity.runOnUiThread { offerUpdate(tag.removePrefix("v"), notes) }
                }
            } catch (_: Exception) {
                // No connection or GitHub unavailable: try again next time.
            }
        }
    }

    private fun offerUpdate(version: String, notes: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val tv = isTvDevice(activity)
        val message = buildString {
            append("Version $version is available. You have ${BuildConfig.VERSION_NAME}.")
            if (notes.isNotBlank()) append("\n\n").append(notes)
            if (tv) {
                append("\n\nTo update, open the Downloader app and enter code ")
                append(Config.DOWNLOADER_CODE)
                append(".")
            } else {
                append("\n\nTap Download, then open the file to install it over this version.")
            }
        }
        val builder = AlertDialog.Builder(activity, R.style.Theme_JunctionPlus_Dialog)
            .setTitle("Update available")
            .setMessage(message)
            .setNegativeButton("Later", null)
        if (tv) {
            builder.setPositiveButton("OK", null)
        } else {
            builder.setPositiveButton("Download") { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                } catch (_: Exception) {
                    // No browser available; nothing else to do.
                }
            }
        }
        builder.show()
    }
}
