package io.github.artcraftandroid.wordcraft

import androidx.compose.ui.graphics.Color
import io.github.artcraftandroid.wordcraft.ui.Appearance
import io.github.artcraftandroid.wordcraft.ui.WordColors
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun blackSurfacesStayBlackWhileGrayRemainsDistinct() {
        val previous = WordColors.appearance
        try {
            WordColors.appearance = Appearance.Black
            val black = listOf(WordColors.HomeBack, WordColors.TitleBar, WordColors.StatusBand, WordColors.Bar, WordColors.Sheet, WordColors.Surface, WordColors.Canvas, WordColors.DarkPage)
            assertTrue(black.all { it == Color.Black })
            assertNotEquals(WordColors.Bar, WordColors.OnBar)
            assertNotEquals(WordColors.Bar, WordColors.BarEdge)
            WordColors.appearance = Appearance.Gray
            val gray = listOf(WordColors.HomeBack, WordColors.TitleBar, WordColors.StatusBand, WordColors.Bar, WordColors.Sheet, WordColors.Surface, WordColors.Canvas, WordColors.DarkPage)
            assertTrue(gray.zip(black).all { (g, b) -> g != b })
            WordColors.appearance = Appearance.Light
            assertEquals(Color.White, WordColors.TitleBar)
            WordColors.appearance = Appearance.Colorful
            assertEquals(WordColors.Accent, WordColors.TitleBar)
        } finally {
            WordColors.appearance = previous
        }
    }
}
