package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.updates.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val asset = UpdateAsset("WordCraft-0.0.5-release.apk", RELEASE_PREFIX + "download/v0.0.5/WordCraft-0.0.5-release.apk", 74_000_000, "a".repeat(64))
    @Test fun acceptsProductionReleaseAssetWithDigest() { assertTrue(validAsset(asset)) }
    @Test fun rejectsLegacyDebugAndNonApkAssets() {
        assertFalse(validAsset(asset.copy(name = "WordCraft-0.0.5-beta-upgrade.apk")))
        assertFalse(validAsset(asset.copy(name = "WordCraft-0.0.5-releaseXapk")))
        assertFalse(validAsset(asset.copy(name = "SHA256SUMS.txt")))
    }
    @Test fun rejectsUntrustedUrls() {
        for (url in listOf("http://github.com/hussain-hz/wordcraft-android/releases/download/a.apk", "https://github.com.evil.test/a.apk", "https://github.com/other/repo/releases/download/a.apk", asset.url + "?redirect=evil", asset.url + "#x")) assertFalse(url, validAsset(asset.copy(url = url)))
    }
    @Test fun rejectsUnboundedFilesAndMissingDigest() {
        for (size in listOf(-1L, 0L, MAX_APK_SIZE + 1)) assertFalse(validAsset(asset.copy(size = size)))
        for (hash in listOf("", "a".repeat(63), "g".repeat(64))) assertFalse(validAsset(asset.copy(sha256 = hash)))
    }
    @Test fun selectsArabicAndEnglishNotesWithoutMixingThem() {
        val body = "<!-- lang:ar -->\nإصلاح الكتابة\n<!-- lang:en -->\nTyping fix"
        assertEquals("إصلاح الكتابة", releaseNotes(body, true))
        assertEquals("Typing fix", releaseNotes(body, false))
    }
    @Test fun supportsReversedSectionOrder() {
        val body = "<!-- lang:en -->\nTyping fix\n<!-- lang:ar -->\nإصلاح الكتابة"
        assertEquals("إصلاح الكتابة", releaseNotes(body, true))
        assertEquals("Typing fix", releaseNotes(body, false))
    }
    @Test fun preservesSingleLanguageHistoricalNotesAndHandlesEmptyNotes() {
        assertEquals("إصلاح الكتابة", releaseNotes(" إصلاح الكتابة ", false))
        assertEquals("", releaseNotes("", true))
    }
}
