package io.github.artcraftandroid.wordcraft

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidUpstreamDeviceTest {
    @Test fun findTracksVisibleDeletionsAndMarksOnlyTextHeight() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WordcraftApplication
        val engine = runBlocking { app.engine.await().getOrThrow() }
        engine.commandBlocking {
            newDocument("")
            typeText("قبل weather بعد")
            execute("review.trackChanges", """{"value":true}""")
            execute("select.text", """{"text":"weather"}""")
            execute("text.delete", "{}")
            for ((mode, count) in listOf("allMarkup" to 1u, "noMarkup" to 0u, "simpleMarkup" to 0u, "original" to 1u)) {
                execute("review.markup", """{"value":"$mode"}""")
                assertEquals(mode, count, findAgain("""{"text":"weather"}""").count)
            }
            execute("edit.replaceAll", """{"text":"weather","with":"rain"}""")
            execute("review.markup", """{"value":"allMarkup"}""")
            assertEquals(1u, findAgain("""{"text":"weather"}""").count)
            newDocument("")
            typeText("cat عربي cat")
            execute("para.lineSpacing", """{"value":2.0}""")
            execute("caret.docStart", "{}")
            val found = findAgain("""{"text":"cat"}""")
            assertEquals(2u, found.count)
            assertEquals(2f * found.currentRects.first().height, found.currentLines.first().height, 0.02f)
        }
    }

    @Test fun reviewModeRepaintsTheDocumentAndPersistsAcrossNewScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WordcraftApplication
        val engine = runBlocking { app.engine.await().getOrThrow() }
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, "upstream-review.docx")
        engine.commandBlocking {
            newDocument("")
            typeText("قبل weather بعد")
            execute("review.trackChanges", """{"value":true}""")
            execute("select.text", """{"text":"weather"}""")
            execute("text.delete", "{}")
            file.writeBytes(saveBytes(file.name))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val device = UiDevice.getInstance(instrumentation)
        val shots = File(context.getExternalFilesDir(null), "upstream-qa").apply { mkdirs() }
        fun waitFor(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 15_000
            while (true) {
                var ready = false
                instrumentation.runOnMainSync { ready = condition() }
                if (ready) return
                check(SystemClock.elapsedRealtime() < end) { message }
                SystemClock.sleep(100)
            }
        }
        fun documentPixels(name: String): IntArray {
            device.waitForIdle()
            SystemClock.sleep(500) // allow the asynchronous render tile to reach the canvas
            val shot = File(shots, "$name.png")
            assertTrue(device.takeScreenshot(shot))
            val bitmap = BitmapFactory.decodeFile(shot.absolutePath)
            val top = (bitmap.height * 0.08f).toInt()
            val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, (bitmap.height * 0.7f).toInt())
            val pixels = IntArray(crop.width * crop.height)
            crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
            crop.recycle(); bitmap.recycle()
            return pixels
        }
        var model: AppViewModel? = null
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[AppViewModel::class.java]
                model!!.open(uri, false)
            }
            waitFor("Open review document") { model?.document?.name == file.name && model?.info != null }
            scenario.onActivity { model!!.chooseMarkupView("allMarkup") }
            waitFor("All markup") { model?.review?.markup == "allMarkup" }
            val shown = documentPixels("01-all-markup")
            scenario.onActivity { model!!.chooseMarkupView("noMarkup") }
            waitFor("No markup") { model?.review?.markup == "noMarkup" }
            val hidden = documentPixels("02-no-markup")
            assertFalse("Changing review mode must repaint the document tiles", shown.contentEquals(hidden))
        }
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[AppViewModel::class.java]
                model!!.newDocument("")
            }
            waitFor("Persisted review mode in another screen/document") { model?.review?.markup == "noMarkup" && model?.document != null }
            assertEquals("noMarkup", context.getSharedPreferences("settings", 0).getString("markupView", null))
            scenario.onActivity { model!!.chooseMarkupView("allMarkup") }
            waitFor("Restore review preference") { model?.review?.markup == "allMarkup" }
        }
    }
}
