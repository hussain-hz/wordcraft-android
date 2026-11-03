package io.github.artcraftandroid.wordcraft

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Visible checks on the dedicated emulator, with screenshots retained for review. */
@RunWith(AndroidJUnit4::class)
class Engine015UiSmokeTest {
    @Test fun homeNewThemesLanguagesAndArabicDocument() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WordcraftApplication
        val engine = runBlocking { app.engine.await().getOrThrow() }
        val share = File(context.cacheDir, "share").apply { mkdirs() }
        val document = File(share, "engine015-arabic.docx")
        runBlocking {
            engine.command {
                newDocument("")
                typeText("اختبار المحرك 0.15.0\nبسم الله الرحمن الرحيم\nالأرقام العربية ١٢٣٤٥٦٧٨٩٠ والإنجليزية 1234567890\nEnglish mixed with العربية")
                execute("select.all", "{}")
                execute("para.rtl", "{}")
                execute("para.alignRight", "{}")
                document.writeBytes(saveBytes(document.name))
            }
        }
        val device = UiDevice.getInstance(instrumentation)
        val shots = File(context.getExternalFilesDir(null), "engine015-qa").apply { mkdirs() }
        fun text(english: String, arabic: String): UiObject2 = requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("^(?:${Pattern.quote(english)}|${Pattern.quote(arabic)})$"))), 15_000)) { "$english / $arabic not found" }
        fun shot(name: String) { device.waitForIdle(); assertTrue(device.takeScreenshot(File(shots, "$name.png"))) }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        text("WordCraft", "WordCraft")
        shot("01-home")
        text("New", "جديد").click()
        text("Blank document", "مستند فارغ")
        assertFalse("New must be its own page", device.hasObject(By.text("Recent")) || device.hasObject(By.text("الأخيرة")))
        shot("02-new-tab")
        text("Home", "الرئيسية").click()
        val settings = requireNotNull(device.wait(Until.findObject(By.desc(Pattern.compile("Settings|الإعدادات"))), 10_000))
        settings.click()
        text("Language", "اللغة").click()
        text("Choose the app language", "اختر لغة التطبيق")
        device.waitForIdle()
        text("English", "English").click()
        text("Theme", "المظهر").click()
        text("Choose the app appearance", "اختر مظهر التطبيق")
        device.waitForIdle()
        text("Black", "أسود").click()
        shot("03-settings-black-en")
        text("Language", "اللغة").click()
        text("Choose the app language", "اختر لغة التطبيق")
        device.waitForIdle()
        text("العربية", "العربية").click()
        text("المظهر", "المظهر")
        shot("04-settings-black-ar")
        device.pressBack()
        text("WordCraft", "WordCraft")
        shot("05-home-black-ar")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", document)
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        assertTrue(device.wait(Until.hasObject(By.textContains("engine015-arabic")), 15_000))
        assertTrue("Document editor must be visible", device.wait(Until.hasObject(By.desc(Pattern.compile("Edit|تحرير"))), 15_000))
        device.waitForIdle()
        shot("06-arabic-document")
    }
}
