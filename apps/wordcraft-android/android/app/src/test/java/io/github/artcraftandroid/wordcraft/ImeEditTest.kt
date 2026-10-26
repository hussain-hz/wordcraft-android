package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.ui.editor.*
import org.junit.Assert.*
import org.junit.Test

class ImeEditTest {
    private fun verify(before: ImeSnapshot, after: ImeSnapshot): ImeEdit {
        val edit = imeEdit(before, after)!!
        if (edit.replace) {
            val start = minOf(before.start, before.end) + edit.start
            val end = maxOf(before.start, before.end) + edit.end
            assertEquals(after.text, before.text.replaceRange(start, end, edit.text))
            assertEquals(after.start, start + edit.text.length + edit.selectionStart)
            assertEquals(after.end, start + edit.text.length + edit.selectionEnd)
        }
        return edit
    }
    @Test fun arabicAppendReplacesOnlyTheChangingLetters() {
        val old = "بسم الله الرحمن الرحيم "
        var before = ImeSnapshot(old, old.length, old.length)
        for (word in listOf("ا", "ال", "الح", "الحمد")) {
            val after = ImeSnapshot(old + word, old.length + word.length, old.length + word.length)
            val edit = verify(before, after)
            assertEquals(0, edit.end)
            before = after
        }
    }
    @Test fun existingWordCorrectionDoesNotAppendTheOldWordAgain() {
        for ((old, changed) in listOf("Hello world" to "Hello words", "بسم الله الرحمن الرحيم" to "بسم الله الرحمن الكريم")) {
            verify(ImeSnapshot(old, old.length, old.length), ImeSnapshot(changed, changed.length, changed.length))
        }
    }
    @Test fun insertionInTheMiddleKeepsTheSuffixAndNonDefaultCursor() {
        verify(ImeSnapshot("hello world", 6, 6), ImeSnapshot("hello new world", 6, 6))
    }
    @Test fun selectedTextReplacementUsesBothOldSelectionEnds() {
        verify(ImeSnapshot("Hello world", 6, 11), ImeSnapshot("Hello everyone", 14, 14))
    }
    @Test fun equalTextOnFinishOrCommitIsNotInsertedTwice() {
        val state = ImeSnapshot("الحمد", 5, 5)
        assertNull(imeEdit(state, state))
    }
    @Test fun emojiDifferencesNeverSplitASurrogatePair() {
        val edit = verify(ImeSnapshot("a😀z", 3, 3), ImeSnapshot("a😁z", 3, 3))
        assertEquals(-2, edit.start)
        assertEquals("😁", edit.text)
        verify(ImeSnapshot("a😀z", 3, 3), ImeSnapshot("az", 1, 1))
    }
    @Test fun selectionOnlyAndReverseSelectionsUseLogicalUtf16Offsets() {
        val edit = imeEdit(ImeSnapshot("مرحبا", 5, 5), ImeSnapshot("مرحبا", 4, 1))!!
        assertFalse(edit.replace)
        assertEquals(-1, edit.selectionStart)
        assertEquals(-4, edit.selectionEnd)
    }
}
