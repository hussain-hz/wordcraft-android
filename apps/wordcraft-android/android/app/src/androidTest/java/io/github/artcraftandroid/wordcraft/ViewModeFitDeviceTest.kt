package io.github.artcraftandroid.wordcraft

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.Screen
import io.github.artcraftandroid.wordcraft.ui.ViewMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewModeFitDeviceTest {
    @Test fun switchingFromMobileFitsTheWholePageWidthEveryTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = runBlocking { (context.applicationContext as WordcraftApplication).engine.await().getOrThrow() }
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, "mobile-fit.docx")
        engine.commandBlocking {
            newDocument("")
            typeText("اختبار ملاءمة الصفحة\nArabic ١٢٣ and English 123")
            file.writeBytes(saveBytes(file.name))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val device = UiDevice.getInstance(instrumentation)
        val shots = File(context.getExternalFilesDir(null), "view-mode-fit-qa").apply { mkdirs() }
        var model: AppViewModel? = null
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
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            var theme = "system"
            var startMobile = true
            try {
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[AppViewModel::class.java]
                    theme = model!!.themeChoice
                    startMobile = model!!.startInMobileView
                    model!!.chooseTheme("dark")
                    model!!.chooseStartInMobileView(true)
                    model!!.open(uri, false)
                }
                waitFor("Document opens in Mobile View") { model?.screen == Screen.Editor && model?.viewMode == ViewMode.Mobile && model?.document?.name == file.name }
                scenario.onActivity {
                    assertTrue("Fixture must carry a saved desktop zoom", model!!.docZoom > 0)
                    model!!.lightBackground = true
                }
                repeat(2) { cycle ->
                    scenario.onActivity { model!!.toggleMobileView() }
                    waitFor("Print layout is ready") { model?.viewMode == ViewMode.Print && model?.info?.pages?.firstOrNull()?.width?.let { it > 500f } == true }
                    scenario.onActivity { assertEquals("Ignore saved zoom when switching to pages", 0, model!!.docZoom) }
                    device.waitForIdle()
                    SystemClock.sleep(1000)
                    val shot = File(shots, "print-fit-$cycle.png")
                    assertTrue(device.takeScreenshot(shot))
                    val bitmap = BitmapFactory.decodeFile(shot.absolutePath)
                    try {
                        val y = bitmap.height / 3
                        fun white(x: Int): Boolean {
                            val color = bitmap.getPixel(x, y)
                            return android.graphics.Color.red(color) > 240 && android.graphics.Color.green(color) > 240 && android.graphics.Color.blue(color) > 240
                        }
                        assertTrue("White page must be visible", white(bitmap.width / 2))
                        assertFalse("Left page edge must fit inside the screen", white(0))
                        assertFalse("Right page edge must fit inside the screen", white(bitmap.width - 1))
                        assertTrue("Page should fill the available width", white(bitmap.width / 20) && white(bitmap.width * 19 / 20))
                    } finally { bitmap.recycle() }
                    if (cycle == 0) {
                        scenario.onActivity { model!!.zoom("in") }
                        device.waitForIdle()
                        scenario.onActivity { model!!.toggleMobileView() }
                        waitFor("Mobile layout is ready") { model?.viewMode == ViewMode.Mobile && model?.info?.pages?.firstOrNull()?.width?.let { it < 500f } == true }
                    }
                }
            } finally {
                scenario.onActivity {
                    model?.chooseTheme(theme)
                    model?.chooseStartInMobileView(startMobile)
                }
            }
        }
    }
}
