package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Columns fit the available panel width instead of assuming a tablet-sized gallery. */
internal fun galleryColumns(availableWidth: Float, preferredWidth: Float, maxColumns: Int): Int {
    require(preferredWidth > 0f && maxColumns > 0)
    return (availableWidth / preferredWidth).toInt().coerceIn(1, maxColumns)
}

/** The engine returns shade rows; each displayed family keeps one hue and all its shades together. */
internal fun <T> colorFamilies(rows: List<List<T>>): List<List<T>> =
    (0 until (rows.maxOfOrNull { it.size } ?: 0)).map { column -> rows.mapNotNull { it.getOrNull(column) } }

internal data class ColorFamily<T>(val shades: List<T>, val neutral: Boolean)

/** Merge neutral theme families and show each actual RGB value once, preserving every unique choice. */
internal fun <T> distinctColorFamilies(rows: List<List<T>>, hexOf: (T) -> String): List<ColorFamily<T>> {
    fun key(item: T) = hexOf(item).removePrefix("#").uppercase(java.util.Locale.ROOT)
    fun neutral(item: T): Boolean {
        val rgb = key(item).toIntOrNull(16) ?: return false
        val red = (rgb shr 16) and 255
        val green = (rgb shr 8) and 255
        val blue = rgb and 255
        return red == green && green == blue
    }
    val (greys, colors) = colorFamilies(rows).partition { family -> family.all(::neutral) }
    val seen = mutableSetOf<String>()
    fun unique(items: List<T>) = items.filter { seen.add(key(it)) }
    val neutralShades = unique(greys.flatten())
    val families = colors.map { ColorFamily(unique(it), false) }.filter { it.shades.isNotEmpty() }
    return if (neutralShades.isEmpty()) families else families + ColorFamily(neutralShades, true)
}

/** Rows inherit layout direction; every item remains reachable through the panel's vertical scroll. */
@Composable
internal fun <T> AdaptiveGallery(
    items: List<T>,
    maxColumns: Int,
    preferredWidth: Dp,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = galleryColumns(maxWidth.value, preferredWidth.value, maxColumns)
        val cellWidth = maxWidth / columns
        Column(Modifier.fillMaxWidth()) {
            for (row in items.chunked(columns)) {
                Row(Modifier.fillMaxWidth()) {
                    for (item in row) {
                        Box(Modifier.width(cellWidth).height(rowHeight), contentAlignment = Alignment.Center) {
                            content(item)
                        }
                    }
                }
            }
        }
    }
}
