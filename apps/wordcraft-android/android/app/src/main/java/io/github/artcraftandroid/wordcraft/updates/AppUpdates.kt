package io.github.artcraftandroid.wordcraft.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.artcraftandroid.wordcraft.R
import io.github.artcraftandroid.wordcraft.ui.localizedUiText
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
            if (update == null) NotificationManagerCompat.from(context).cancel(NOTIFICATION)
            update
        } finally {
            connection.disconnect()
        }
    }

    /** Notify once per release, only after Android's notification permission is granted. */
    fun notify(context: Context, update: AppUpdate) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val language = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("interfaceLanguage", "").orEmpty()
        val localized = if (language.isEmpty()) context else context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(language))
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localizedUiText(localized, "App updates"), NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        if (prefs(context).getString("notified", null) == update.version) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl))
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle(localizedUiText(localized, "A WordCraft update is available"))
            .setContentText(localizedUiText(localized, "Version %1\$s", update.version))
            .setContentIntent(pending).setAutoCancel(true).build()
        try {
            manager.notify(NOTIFICATION, notification)
            prefs(context).edit().putString("notified", update.version).apply()
        } catch (_: SecurityException) { /* Permission may have changed while checking. */ }
    }
}
