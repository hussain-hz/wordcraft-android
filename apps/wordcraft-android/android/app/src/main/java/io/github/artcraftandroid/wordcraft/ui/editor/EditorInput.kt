package io.github.artcraftandroid.wordcraft.ui.editor

import android.content.Context
import android.text.InputType
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.inputmethod.EditorInfoCompat
import io.github.artcraftandroid.wordcraft.core.TextAround

/** What the keyboard does to the document (the editor's view model answers). */
interface EditorInputListener {
    /** Text to type at the caret, replacing the selection. */
    fun commit(text: String)

    /** Apply one exact Android buffer change to the engine before answering the IME. */
    fun edit(change: ImeEdit, composing: Boolean): Boolean

    /** Delete characters before and after the caret. */
    fun deleteAround(before: Int, after: Int)

    /** An engine command for a key (Enter, arrows, Delete…). */
    fun command(id: String, params: String = "")

    /** A hardware keyboard's shortcut (`Mod+B`…) through the key map; whether it did something. */
    fun shortcut(combo: String): Boolean

    /** The text around the selection in its paragraph, in UTF-16 units. */
    fun textAround(before: Int, after: Int): TextAround
}

/**
 * The keyboard's way into the document: a view with no size that holds the input focus. The
 * document itself lives in the engine, so keyboard actions (the on-screen keyboard's, through
 * its connection, and a hardware keyboard's keys) are forwarded to it rather than editing text
 * of the view's own.
 */
class EditorInputView(context: Context) : View(context) {
    var listener: EditorInputListener? = null
    private val imm = context.getSystemService(InputMethodManager::class.java)

    private var connection: EditorConnection? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Off while the document is only read: the system then doesn't bring the keyboard up for this
     * view by itself (as it does when a dialog closes and the focus comes back).
     */
    var textEditor = true

    override fun onCheckIsTextEditor(): Boolean = textEditor

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        val around = listener?.textAround(500, 500) ?: TextAround("", "", "")
        outAttrs.initialSelStart = around.before.length
        outAttrs.initialSelEnd = around.before.length + around.selected.length
        EditorInfoCompat.setInitialSurroundingText(outAttrs, around.before + around.selected + around.after)
        connection?.deactivate()
        return EditorConnection(this, around).also { connection = it }
    }

    /** Take the focus and show the keyboard. */
    fun showKeyboard() {
        if (!textEditor) {
            textEditor = true
            imm?.restartInput(this)
        }
        requestFocus()
        imm?.showSoftInput(this, 0)
    }

    fun hideKeyboard() {
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    /** The caret moved other than by typing: the keyboard forgets its word and starts afresh. */
    fun restartInput() {
        connection?.deactivate()
        connection = null
        imm?.restartInput(this)
    }

    override fun onDetachedFromWindow() {
        connection?.deactivate()
        connection = null
        super.onDetachedFromWindow()
    }

    /** A hardware keyboard's key (the on-screen keyboard's arrive through the connection). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = connection?.sendKeyEvent(event) ?: (handleKey(event) || super.onKeyDown(keyCode, event))

    /** One key pressed: a shortcut, an editing key, or a character to type. */
    private fun handleKey(event: KeyEvent): Boolean {
        val l = listener ?: return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            val name = keyName(event.keyCode) ?: return false
            val combo = buildString {
                if (event.isCtrlPressed || event.isMetaPressed) append("Mod+")
                if (event.isAltPressed) append("Alt+")
                if (event.isShiftPressed) append("Shift+")
                append(name)
            }
            return l.shortcut(combo)
        }
        val extend = if (event.isShiftPressed) """{"extend":true}""" else ""
        fun move(id: String) {
            l.command(id, extend)
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> l.deleteAround(1, 0)
            KeyEvent.KEYCODE_FORWARD_DEL -> l.deleteAround(0, 1)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.isShiftPressed) l.command("text.lineBreak") else l.commit("\n")
            }
            KeyEvent.KEYCODE_TAB -> {
                    l.command(if (event.isShiftPressed) "text.backTab" else "text.tab")
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> move("caret.left")
            KeyEvent.KEYCODE_DPAD_RIGHT -> move("caret.right")
            KeyEvent.KEYCODE_DPAD_UP -> move("caret.up")
            KeyEvent.KEYCODE_DPAD_DOWN -> move("caret.down")
            KeyEvent.KEYCODE_MOVE_HOME -> move("caret.home")
            KeyEvent.KEYCODE_MOVE_END -> move("caret.end")
            KeyEvent.KEYCODE_PAGE_UP -> move("caret.pageUp")
            KeyEvent.KEYCODE_PAGE_DOWN -> move("caret.pageDown")
            else -> {
                val ch = event.unicodeChar
                if (ch == 0 || Character.isISOControl(ch)) return false
                    l.commit(String(Character.toChars(ch)))
            }
        }
        return true
    }

    /** The desktop key map's name for a key (`B`, `Z`, `Enter`, `Left`…), or null. */
    private fun keyName(code: Int): String? = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (code - KeyEvent.KEYCODE_0)).toString()
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_MOVE_HOME -> "Home"
        KeyEvent.KEYCODE_MOVE_END -> "End"
        KeyEvent.KEYCODE_PAGE_UP -> "PageUp"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PageDown"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> "="
        KeyEvent.KEYCODE_MINUS -> "-"
        KeyEvent.KEYCODE_PERIOD -> "."
        KeyEvent.KEYCODE_COMMA -> ","
        KeyEvent.KEYCODE_LEFT_BRACKET -> "["
        KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${code - KeyEvent.KEYCODE_F1 + 1}"
        else -> null
    }

    private class EditorConnection(private val view: EditorInputView, around: TextAround) : BaseInputConnection(view, true) {
        private val buffer = SpannableStringBuilder(around.before + around.selected + around.after)
        private var closed = false
        private var mutationDepth = 0
        private var batchDepth = 0
        private var extractedToken: Int? = null
        init { Selection.setSelection(buffer, around.before.length, around.before.length + around.selected.length) }
        override fun getEditable(): Editable = buffer
        fun deactivate() { closed = true }
        private fun snapshot() = ImeSnapshot(buffer.toString(), Selection.getSelectionStart(buffer).coerceAtLeast(0), Selection.getSelectionEnd(buffer).coerceAtLeast(0))
        private fun reportSelection() {
            if (!closed && batchDepth == 0 && mutationDepth == 0) {
                view.imm?.updateSelection(view, Selection.getSelectionStart(buffer), Selection.getSelectionEnd(buffer),
                    getComposingSpanStart(buffer), getComposingSpanEnd(buffer))
                extractedToken?.let { view.imm?.updateExtractedText(view, it, extracted()) }
            }
        }
        private inline fun mutate(action: () -> Boolean): Boolean {
            if (closed || view.connection !== this) return false
            val old = if (mutationDepth == 0) SpannableStringBuilder(buffer) else null
            val before = snapshot()
            mutationDepth++
            val result = try { action() } finally { mutationDepth-- }
            if (mutationDepth == 0) {
                val edit = imeEdit(before, snapshot())
                if (edit != null && view.listener?.edit(edit, getComposingSpanStart(buffer) >= 0) != true) {
                    buffer.replace(0, buffer.length, old!!)
                    Selection.setSelection(buffer, before.start, before.end)
                    reportSelection()
                    return false
                }
                if (edit != null && getComposingSpanStart(buffer) < 0) {
                    val fresh = view.listener?.textAround(500, 500)
                    if (fresh != null) {
                        buffer.replace(0, buffer.length, fresh.before + fresh.selected + fresh.after)
                        Selection.setSelection(buffer, fresh.before.length, fresh.before.length + fresh.selected.length)
                    }
                }
                reportSelection()
            }
            return result
        }
        override fun beginBatchEdit(): Boolean { if (closed) return false; batchDepth++; return true }
        override fun endBatchEdit(): Boolean { if (batchDepth > 0) batchDepth--; reportSelection(); return true }
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = mutate { super.commitText(text, newCursorPosition) }
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = mutate { super.setComposingText(text, newCursorPosition) }
        override fun setComposingRegion(start: Int, end: Int): Boolean = mutate { super.setComposingRegion(start, end) }
        override fun finishComposingText(): Boolean = mutate { super.finishComposingText() }
        override fun setSelection(start: Int, end: Int): Boolean = mutate { super.setSelection(start, end) }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = mutate { super.deleteSurroundingText(beforeLength, afterLength) }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = mutate { super.deleteSurroundingTextInCodePoints(beforeLength, afterLength) }
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (closed) return false
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> return deleteSurroundingTextInCodePoints(1, 0)
                    KeyEvent.KEYCODE_FORWARD_DEL -> return deleteSurroundingTextInCodePoints(0, 1)
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (!event.isShiftPressed) return commitText("\n", 1)
                }
                if (!event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && event.unicodeChar != 0 && !Character.isISOControl(event.unicodeChar)) {
                    return commitText(String(Character.toChars(event.unicodeChar)), 1)
                }
                finishComposingText()
                view.handleKey(event)
                view.restartInput()
            }
            return true
        }
        private fun extracted(): ExtractedText = ExtractedText().apply {
            text = buffer.toString(); startOffset = 0; partialStartOffset = -1; partialEndOffset = -1
            selectionStart = Selection.getSelectionStart(buffer); selectionEnd = Selection.getSelectionEnd(buffer)
        }
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            if (flags and InputConnection.GET_EXTRACTED_TEXT_MONITOR != 0) extractedToken = request?.token
            return extracted()
        }
        override fun performEditorAction(actionCode: Int): Boolean = commitText("\n", 1)
        override fun closeConnection() { deactivate(); super.closeConnection() }
    }
}
