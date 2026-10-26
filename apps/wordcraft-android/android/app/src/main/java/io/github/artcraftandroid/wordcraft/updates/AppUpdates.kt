package io.github.artcraftandroid.wordcraft.updates

import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.content.ComponentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object AppUpdates {
    private const val API = "https://api.github.com/repos/hussain-hz/wordcraft-android/releases/latest"
    private const val RELEASE_PREFIX = "https://github.com/hussain-hz/wordcraft-android/releases/"
    private const val CHANNEL = "app_updates"
    private const val JOB = 19110
    private const val NOTIFICATION = 19111
    private fun prefs(context: Context) = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    fun installedVersion(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Persisted daily job: Android controls the actual execution time and requires a network. */
    fun schedule(context: Context) {
        // Remove the previous release's system notification and channel when upgrading.
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIFICATION)
            deleteNotificationChannel(CHANNEL)
        }
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB) == null) {
            scheduler.schedule(JobInfo.Builder(JOB, ComponentName(context, UpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(1))
                .setPersisted(true).build())
        }
    }

    fun cached(context: Context): AppUpdate? {
        val p = prefs(context)
        val version = p.getString("version", null) ?: return null
        val url = p.getString("url", null) ?: return null
        return if (url.startsWith(RELEASE_PREFIX) && newerRelease(version, installedVersion(context), false, false, true)) AppUpdate(version, url) else null
    }

    /** No tokens, document data or identifiers are sent; only the public latest-release API. */
    suspend fun check(context: Context, force: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val elapsed = now - p.getLong("checked", 0)
        if (!force && elapsed in 0 until TimeUnit.HOURS.toMillis(6)) return@withContext cached(context)
        val connection = URL(API).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "WordCraft-Android/${installedVersion(context)}")
        try {
            val update = when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null // No published stable release yet.
                HttpURLConnection.HTTP_OK -> {
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 2_000_000) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    check(bytes.size <= 2_000_000) { "Release response too large" }
                    val json = JSONObject(bytes.toString(Charsets.UTF_8))
                    val tag = json.optString("tag_name")
                    val url = json.optString("html_url")
                    val assets = json.optJSONArray("assets")
                    val hasApk = (0 until (assets?.length() ?: 0)).any { i ->
                        val asset = assets!!.optJSONObject(i)
                        asset != null && asset.optString("state") == "uploaded" && asset.optLong("size") > 0 &&
                            asset.optString("name").endsWith(".apk", ignoreCase = true) &&
                            asset.optString("browser_download_url").startsWith(RELEASE_PREFIX + "download/")
                    }
                    if (url.startsWith(RELEASE_PREFIX + "tag/") && newerRelease(tag, installedVersion(context), json.optBoolean("draft"), json.optBoolean("prerelease"), hasApk)) AppUpdate(tag, url) else null
                }
                else -> error("Release check failed: HTTP ${connection.responseCode}")
            }
            p.edit().putLong("checked", now).putString("version", update?.version).putString("url", update?.pageUrl).apply()
            if (update == null) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
            update
        } finally {
            connection.disconnect()
        }
    }

}
