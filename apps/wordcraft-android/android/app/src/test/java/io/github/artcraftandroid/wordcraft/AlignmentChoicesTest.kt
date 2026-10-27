package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.ui.editor.alignmentChoices
import org.junit.Assert.*
import org.junit.Test

class AlignmentChoicesTest {
    @Test fun arabicLeadingAlignmentButtonIsRightAndEnglishLeadingButtonIsLeft() {
        assertEquals(listOf("right", "center", "left", "justify"), alignmentChoices(true))
        assertEquals(listOf("left", "center", "right", "justify"), alignmentChoices(false))
        assertEquals(listOf("right", "center", "left"), alignmentChoices(true, false))
    }
}
