package io.github.artcraftandroid.wordcraft.updates

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Private, bounded downloads. No storage/notification permission or executable server content. */
object UpdateDownload {
    suspend fun download(context: Context, update: AppUpdate, progress: suspend (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val asset = requireNotNull(update.asset)
        require(validAsset(asset))
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val part = File.createTempFile("update-", ".part", dir)
        var connection: HttpURLConnection? = null
        try {
            var url = URL(asset.url)
            for (redirect in 0..5) {
                currentCoroutineContext().ensureActive()
                require(url.protocol == "https" && url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") && url.userInfo == null)
                val c = (url.openConnection() as HttpURLConnection).also { connection = it }
                c.connectTimeout = 15_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "WordCraft-Android")
                c.setRequestProperty("Accept-Encoding", "identity")
                if (c.responseCode in listOf(301, 302, 303, 307, 308)) {
                    val next = URL(url, c.getHeaderField("Location") ?: error("Missing redirect"))
                    c.disconnect(); connection = null; url = next
                    continue
                }
                check(c.responseCode == 200)
                check(c.contentLengthLong < 0 || c.contentLengthLong == asset.size)
                var count = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                var last = 0L
                c.inputStream.use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n; check(count <= asset.size && count <= MAX_APK_SIZE)
                        output.write(buffer, 0, n); digest.update(buffer, 0, n)
                        val now = System.currentTimeMillis()
                        if (now - last >= 150) { progress(count, asset.size); last = now }
                    }
                } }
                check(count == asset.size)
                check(digest.digest().joinToString("") { "%02x".format(it) }.equals(asset.sha256, true))
                progress(count, asset.size)
                verifyPackage(context, part, update)
                currentCoroutineContext().ensureActive()
                val ready = File(dir, "update.apk")
                check(part.renameTo(ready))
                return@withContext ready
            }
            error("Too many redirects")
        } finally {
            connection?.disconnect()
            part.delete()
        }
    }

    /** Check again before every installer handoff, including after the permission screen. */
    fun verifyPackage(context: Context, file: File, update: AppUpdate) {
        val asset = requireNotNull(update.asset)
        check(file.length() == asset.size)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) }.equals(asset.sha256, true))
        val pm = context.packageManager
        val candidate = requireNotNull(pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES))
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(candidate.packageName == context.packageName)
        check(candidate.longVersionCode > installed.longVersionCode)
        check(AppVersion.parse(candidate.versionName.orEmpty()) == AppVersion.parse(update.version))
        val old = requireNotNull(installed.signingInfo).apkContentsSigners.map { it.toCharsString() }.toSet()
        val new = requireNotNull(candidate.signingInfo).apkContentsSigners.map { it.toCharsString() }.toSet()
        check(old.isNotEmpty() && old == new)
    }
}
