package io.github.artcraftandroid.wordcraft

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.artcraftandroid.wordcraft.core.enableLanguagePackDownloads
import io.github.artcraftandroid.wordcraft.core.initializeLanguagePacks
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Exercises encrypted opening and real, checksum-verified language-pack downloads. */
@RunWith(AndroidJUnit4::class)
class Engine015IntegrationTest {
    @Test fun encryptedDocumentRetriesPasswordAndLeavesOriginalIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, "engine015-protected.docx")
        val original = instrumentation.context.assets.open("encrypted-engine015.docx").use { it.readBytes() }
        file.writeBytes(original)
        val device = UiDevice.getInstance(instrumentation)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        fun text(en: String, ar: String) = requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("^(?:${Pattern.quote(en)}|${Pattern.quote(ar)})$"))), 15_000)) { "$en / $ar not found" }
        text("Enter the document password", "أدخل كلمة مرور المستند")
        val field = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        field.text = "wrong"
        text("Open", "فتح").click()
        text("The password isn't correct.", "كلمة المرور غير صحيحة.")
        requireNotNull(device.findObject(By.clazz("android.widget.EditText"))).text = "engine015"
        text("Open", "فتح").click()
        assertTrue(device.wait(Until.hasObject(By.textContains("engine015-protected")), 15_000))
        assertTrue("Document editor must be visible", device.wait(Until.hasObject(By.desc(Pattern.compile("Edit|تحرير"))), 15_000))
        device.waitForIdle()
        assertFalse(device.hasObject(By.text("Enter the document password")))
        val app = context.applicationContext as WordcraftApplication
        val engine = runBlocking { app.engine.await().getOrThrow() }
        engine.commandBlocking {
            assertTrue(documentInfo().pages.isNotEmpty())
            assertTrue(renderPage(0u, 1f).rgba.isNotEmpty())
        }
        assertArrayEquals("Protected source is unchanged", original, file.readBytes())
        val shots = File(context.getExternalFilesDir(null), "engine015-qa").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(shots, "07-password-opened.png")))
    }

    @Test fun realLanguagePackDownloadInstallAndRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WordcraftApplication
        val engine = runBlocking { app.engine.await().getOrThrow() }
        // Keep consent and downloaded data confined to this test, outside the user's store.
        val store = File(context.cacheDir, "engine015-test-language-store")
        initializeLanguagePacks(store.absolutePath)
        fun command(id: String, args: String = "{}") = JSONObject(engine.commandBlocking { execute(id, args) })
        fun settle(): JSONObject {
            val until = android.os.SystemClock.elapsedRealtime() + 45_000
            while (true) {
                val state = command("proof.packs")
                if (!state.optBoolean("refreshing") && state.getJSONArray("installing").length() == 0) return state
                check(android.os.SystemClock.elapsedRealtime() < until) { "Pack download timed out" }
                Thread.sleep(200)
            }
        }
        try {
            assertFalse(command("proof.packs").getBoolean("network"))
            assertTrue(enableLanguagePackDownloads())
            command("proof.packs", """{"refresh":true}""")
            val listed = settle()
            assertEquals(listed.toString(), 0, listed.getJSONObject("errors").length())
            val packs = listed.getJSONArray("packs")
            assertTrue("Pinned index must contain packs", packs.length() > 0)
            val smallest = (0 until packs.length()).map { packs.getJSONObject(it) }.minBy { it.getLong("size") }
            val locale = smallest.getString("locale")
            command("proof.installPack", JSONObject().put("locale", locale).toString())
            val installed = settle()
            assertEquals(installed.toString(), 0, installed.getJSONObject("errors").length())
            assertTrue((0 until installed.getJSONArray("installed").length()).any {
                installed.getJSONArray("installed").getJSONObject(it).getString("locale") == locale
            })
            command("proof.removePack", JSONObject().put("locale", locale).toString())
            assertEquals(0, command("proof.packs").getJSONArray("installed").length())
        } finally {
            initializeLanguagePacks(File(context.filesDir, "language-store").absolutePath)
            store.deleteRecursively()
        }
    }
}
