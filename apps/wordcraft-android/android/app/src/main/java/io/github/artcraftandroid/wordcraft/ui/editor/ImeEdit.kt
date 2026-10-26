package io.github.artcraftandroid.wordcraft.ui.editor

/** UTF-16 offsets, as Android's InputConnection defines them; never visual RTL coordinates. */
internal data class ImeSnapshot(val text: String, val start: Int, val end: Int)

data class ImeEdit(
    val replace: Boolean,
    val start: Int,
    val end: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

internal fun imeEdit(before: ImeSnapshot, after: ImeSnapshot): ImeEdit? {
    val oldStart = minOf(before.start, before.end)
    val oldEnd = maxOf(before.start, before.end)
    if (before.text == after.text) {
        if (before.start == after.start && before.end == after.end) return null
        return ImeEdit(false, 0, 0, "", after.start - oldStart, after.end - oldEnd)
    }
    var prefix = before.text.commonPrefixWith(after.text).length
    fun boundary(s: String, at: Int) = at == 0 || at == s.length || !s[at].isLowSurrogate() || !s[at - 1].isHighSurrogate()
    while (!boundary(before.text, prefix) || !boundary(after.text, prefix)) prefix--
    var suffix = 0
    while (before.text.length - suffix > prefix && after.text.length - suffix > prefix &&
        before.text[before.text.lastIndex - suffix] == after.text[after.text.lastIndex - suffix]) suffix++
    while (!boundary(before.text, before.text.length - suffix) || !boundary(after.text, after.text.length - suffix)) suffix--
    val newEnd = after.text.length - suffix
    return ImeEdit(true, prefix - oldStart, before.text.length - suffix - oldEnd,
        after.text.substring(prefix, newEnd), after.start - newEnd, after.end - newEnd)
}
