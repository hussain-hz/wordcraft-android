package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.updates.AppVersion
import io.github.artcraftandroid.wordcraft.updates.newerRelease
import org.junit.Assert.*
import org.junit.Test

class AppVersionTest {
    @Test fun stableIsNewerThanBetaOfSameVersion() {
        assertTrue(newerRelease("v0.0.1", "0.0.1-beta.6", false, false, true))
    }
    @Test fun usesNumericSemverRatherThanTextOrEngineVersion() {
        assertTrue(newerRelease("v0.0.10", "0.0.9", false, false, true))
        assertFalse(newerRelease("v0.0.1", "0.0.1", false, false, true))
        assertFalse(newerRelease("v0.0.1", "0.1.0", false, false, true))
        assertEquals(0, AppVersion.parse("v1.2.3+build.1")!!.compareTo(AppVersion.parse("1.2.3")!!))
    }
    @Test fun ignoresDraftsPrereleasesMissingApksAndMalformedTags() {
        assertFalse(newerRelease("v0.0.2", "0.0.1", true, false, true))
        assertFalse(newerRelease("v0.0.2-beta.1", "0.0.1", false, true, true))
        assertFalse(newerRelease("v0.0.2", "0.0.1", false, false, false))
        assertFalse(newerRelease("nightly", "0.0.1", false, false, true))
        assertFalse(newerRelease("v0.0.2", "unknown", false, false, true))
        assertNull(AppVersion.parse("1.02.3"))
        assertNull(AppVersion.parse("1.2.3-beta.01"))
    }
    @Test fun prereleaseIdentifiersFollowSemver() {
        assertTrue(AppVersion.parse("1.0.0-beta.10")!! > AppVersion.parse("1.0.0-beta.9")!!)
        assertTrue(AppVersion.parse("1.0.0-rc")!! > AppVersion.parse("1.0.0-beta.10")!!)
    }
}
