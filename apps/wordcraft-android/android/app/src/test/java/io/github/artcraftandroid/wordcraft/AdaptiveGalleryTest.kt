package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.ui.editor.galleryColumns
import io.github.artcraftandroid.wordcraft.ui.editor.colorFamilies
import io.github.artcraftandroid.wordcraft.ui.editor.distinctColorFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGalleryTest {
    @Test
    fun phonePanelsReflowThePreviouslyClippedRows() {
        // 360 dp screen with symmetric 16 dp panel insets.
        assertEquals(6, galleryColumns(328f, 49f, 10))
        assertEquals(5, galleryColumns(328f, 61f, 8))
        assertEquals(5, galleryColumns(328f, 61.3f, 8))
        assertEquals(4, galleryColumns(328f, 70f, 7))
    }

    @Test
    fun tabletPanelsRetainTheIntendedMaximumColumns() {
        assertEquals(10, galleryColumns(800f, 49f, 10))
        assertEquals(8, galleryColumns(800f, 61.3f, 8))
    }

    @Test
    fun allPhoneAndSplitScreenWidthsHaveUsableTargets() {
        for (width in listOf(240f, 288f, 328f, 380f, 568f)) {
            for ((preferred, cap) in listOf(49f to 10, 61f to 8, 61.3f to 8, 70f to 7)) {
                val columns = galleryColumns(width, preferred, cap)
                assertTrue(columns in 1..cap)
                assertTrue(width / columns >= preferred)
            }
        }
        assertEquals(1, galleryColumns(0f, 49f, 10))
    }
    @Test
    fun shadesStayBesideTheirOwnHue() {
        val rows = listOf(listOf("red", "blue"), listOf("light red", "light blue"), listOf("dark red", "dark blue"))
        assertEquals(listOf(listOf("red", "light red", "dark red"), listOf("blue", "light blue", "dark blue")), colorFamilies(rows))
        assertEquals(6, galleryColumns(288f, 44f, 6))
    }

    @Test
    fun incompleteThemeRowsKeepEveryProvidedColor() {
        assertEquals(listOf(listOf(1, 3), listOf(2)), colorFamilies(listOf(listOf(1, 2), listOf(3))))
        assertTrue(colorFamilies(emptyList<List<String>>()).isEmpty())
    }
    @Test
    fun repeatedNeutralFamiliesAreMergedWithoutLosingUniqueColors() {
        val rows = listOf(listOf("FFFFFF", "000000", "0000FF"), listOf("E5E5E5", "e5e5e5", "8080FF"))
        val groups = distinctColorFamilies(rows) { it }
        assertEquals(1, groups.count { it.neutral })
        assertTrue(groups.last().neutral)
        assertEquals(listOf("0000FF", "8080FF"), groups.first().shades)
        val displayed = groups.flatMap { it.shades }.map { it.uppercase() }
        assertEquals(displayed.size, displayed.distinct().size)
        assertEquals(rows.flatten().map { it.uppercase() }.toSet(), displayed.toSet())
    }
}
