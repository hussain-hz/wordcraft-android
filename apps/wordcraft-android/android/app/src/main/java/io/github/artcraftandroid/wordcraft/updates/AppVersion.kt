package io.github.artcraftandroid.wordcraft.updates

/** SemVer ordering, independent of engine versions and Android ABI version-code suffixes. */
data class AppVersion(val major: Long, val minor: Long, val patch: Long, val pre: List<String>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for ((a, b) in listOf(major to other.major, minor to other.minor, patch to other.patch)) {
            a.compareTo(b).takeIf { it != 0 }?.let { return it }
        }
        if (pre.isEmpty() || other.pre.isEmpty()) return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0
            pre.isEmpty() -> 1
            else -> -1
        }
        for ((a, b) in pre.zip(other.pre)) {
            val an = a.toLongOrNull(); val bn = b.toLongOrNull()
            val result = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return pre.size.compareTo(other.pre.size)
    }

    companion object {
        fun parse(value: String): AppVersion? {
            val match = Regex("""v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""").matchEntire(value) ?: return null
            val nums = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
            val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (pre.any { it.all(Char::isDigit) && (it.length > 1 && it[0] == '0' || it.toLongOrNull() == null) }) return null
            return AppVersion(nums[0], nums[1], nums[2], pre)
        }
    }
}

data class UpdateAsset(val name: String, val url: String, val size: Long, val sha256: String)
data class AppUpdate(val version: String, val pageUrl: String, val notes: String = "", val asset: UpdateAsset? = null)

internal const val RELEASE_PREFIX = "https://github.com/hussain-hz/wordcraft-android/releases/"
internal const val MAX_APK_SIZE = 350L * 1024 * 1024

internal fun validAsset(asset: UpdateAsset): Boolean =
    asset.name.matches(Regex("WordCraft-[0-9A-Za-z.+-]+-release\\.apk")) &&
        asset.url.startsWith(RELEASE_PREFIX + "download/") &&
        runCatching { java.net.URI(asset.url).let { it.host == "github.com" && it.userInfo == null && it.fragment == null && it.query == null } }.getOrDefault(false) &&
        asset.size in 1..MAX_APK_SIZE && asset.sha256.matches(Regex("[0-9a-fA-F]{64}"))

/** Bilingual release bodies use explicit section markers; never guess a translation. */
internal fun releaseNotes(body: String, arabic: Boolean): String {
    val ar = "<!-- lang:ar -->"; val en = "<!-- lang:en -->"
    if (!body.contains(ar) || !body.contains(en)) return body.trim()
    val marker = if (arabic) ar else en
    return body.substringAfter(marker).substringBefore("<!-- lang:").trim()
}

internal fun newerRelease(tag: String, current: String, draft: Boolean, prerelease: Boolean, hasApk: Boolean): Boolean {
    if (draft || prerelease || !hasApk) return false
    val candidate = AppVersion.parse(tag) ?: return false
    val installed = AppVersion.parse(current) ?: return false
    return candidate.pre.isEmpty() && candidate > installed
}
