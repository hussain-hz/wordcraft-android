package io.github.artcraftandroid.wordcraft

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.artcraftandroid.wordcraft.core.MobileSession
import io.github.artcraftandroid.wordcraft.core.engineVersion
import io.github.artcraftandroid.wordcraft.engine.Engine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

/** Runs the real Android native library, bindings, document fonts and rasterizer on the emulator. */
@RunWith(AndroidJUnit4::class)
class Engine015DeviceTest {
    companion object {
        private lateinit var worker: Engine
        @BeforeClass @JvmStatic fun startEngine() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WordcraftApplication
            worker = runBlocking { app.engine.await().getOrThrow() }
        }
    }

    private fun <T> native(block: MobileSession.() -> T): T = worker.commandBlocking(block)
    private fun docxText(bytes: ByteArray): String = ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "word/document.xml") return@use zip.readBytes().toString(Charsets.UTF_8)
        }
        error("Missing DOCX body")
    }

    @Test fun arabicEditJoinsAcrossFormattingInBothViewsAndAfterSaving(): Unit = native {
        assertEquals("0.15.0", engineVersion())
        newDocument("")
        typeText("فتحه")
        execute("caret.set", """{"pos":{"block":0,"off":6}}""")
        execute("format.color", """{"color":"C80000"}""")
        typeText("ت")
        execute("file.options", """{"checkSpelling":false,"markGrammar":false}""")
        val reference = MobileSession()
        reference.start(java.util.Locale.getDefault().toLanguageTag())
        reference.newDocument("")
        reference.typeText("فتحته")
        reference.execute("file.options", """{"checkSpelling":false,"markGrammar":false}""")
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "engine015-joining.docx")
        file.writeBytes(saveBytes("joining.docx"))
        val referenceFile = File(file.parentFile, "engine015-reference.docx")
        referenceFile.writeBytes(reference.saveBytes("reference.docx"))
        assertTrue(docxText(file.readBytes()).contains("ت"))
        for (reopen in listOf(false, true)) {
            if (reopen) { open(file.absolutePath, null); reference.open(referenceFile.absolutePath, null) }
            for (mode in listOf("print", "mobile")) {
                setView(mode, 320f)
                reference.setView(mode, 320f)
                val actual = renderPage(0u, 1f)
                val expected = reference.renderPage(0u, 1f)
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                assertEquals(expected.rgba.size, actual.rgba.size)
                for (i in 1 until actual.rgba.size step 4) {
                    if (expected.rgba[i] != actual.rgba[i]) fail("Arabic geometry: $mode reopened=$reopen pixel=$i")
                }
            }
        }
        file.delete()
        referenceFile.delete()
        Unit
    }

    @Test fun digitScriptsAndEnglishSurviveDocxRoundTrip() = native {
        newDocument("")
        typeText("العربية ١٢٣٤٥٦٧٨٩٠ English 1234567890 فارسی ۱۲۳")
        val bytes = saveBytes("digits.docx")
        val xml = docxText(bytes)
        for (text in listOf("١٢٣٤٥٦٧٨٩٠", "1234567890", "۱۲۳", "English")) assertTrue(text, xml.contains(text))
        for (mode in listOf("print", "mobile")) {
            setView(mode, 320f)
            val image = renderPage(0u, 1f)
            assertEquals(image.width.toInt() * image.height.toInt() * 4, image.rgba.size)
        }
    }

    @Test fun pictureAndChartGalleriesRenderOnAndroid() = native {
        newDocument("")
        val pictures = pictureStyleGallery(160u)
        assertEquals(24, pictures.size)
        assertTrue(pictures.all { it.image.width == 160u && it.image.rgba.isNotEmpty() })
        assertEquals(24, pictures.map { it.image.rgba.contentHashCode() }.distinct().size)
        execute("insert.chart", """{"type":"column"}""")
        val charts = chartGallery("type:line", 240u)
        assertTrue(charts.isNotEmpty())
        assertTrue(charts.all { it.image.rgba.isNotEmpty() })
        assertTrue(saveBytes("chart.docx").isNotEmpty())
    }

    @Test fun tablesShapesUndoRedoAndPrivateLanguageStoreWork() = native {
        newDocument("")
        execute("insert.table", """{"rows":2,"cols":2}""")
        typeText("جدول عربي English")
        assertTrue(docxText(saveBytes("table.docx")).contains("جدول عربي"))
        newDocument("")
        typeText("Undo Redo")
        execute("edit.undo", "{}")
        assertTrue(documentInfo().canRedo)
        execute("edit.redo", "{}")
        assertTrue(docxText(saveBytes("redo.docx")).contains("Undo Redo"))
        execute("insert.shape", """{"preset":"roundRect","width":90,"height":60}""")
        assertTrue(renderPage(0u, 1f).rgba.isNotEmpty())
        val packs = JSONObject(execute("proof.packs", "{}"))
        assertTrue(packs.getBoolean("available"))
        // Startup must not enable network access or start downloads.
        assertFalse(packs.getBoolean("network"))
        assertEquals(0, packs.getJSONArray("installing").length())
    }
    @Test fun phonePrintPdfKeepsContentAndOmitsInteractiveAnnotations() = native {
        newDocument("")
        execute("insert.link", """{"url":"https://example.com","text":"Print العربية ١٢٣"}""")
        val shared = exportBytes("share.pdf").toString(Charsets.ISO_8859_1)
        val printed = printBytes().toString(Charsets.ISO_8859_1)
        assertTrue(shared.contains("/Annots"))
        assertTrue(printed.startsWith("%PDF"))
        assertFalse(printed.contains("/Annots"))
        assertTrue(documentInfo().dirty)
    }

}
