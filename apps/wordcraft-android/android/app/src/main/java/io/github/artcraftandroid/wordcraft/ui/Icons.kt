package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The stroke weight WordCraft draws Lucide's 24-unit icons with: lighter than Lucide's 2. */
const val ICON_STROKE = 1.6f

/** A Lucide icon from its SVG path strings (see `LucideIconsTask` in app/build.gradle.kts). */
internal fun lucide(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = name in setOf("arrow-left", "arrow-right", "chevron-left", "chevron-right"))
    for (d in paths) {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = ICON_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** Landing-page icons share the same Lucide grid and stroke as the document tools. */
internal object HomeIcons {
    val Update by lazy { lucide("app-update", "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0", "M12 7v10", "m8 13 4 4 4-4") }
    val User by lazy { lucide("user", "M20 21a8 8 0 0 0-16 0", "M16 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0") }
    val Star by lazy { lucide("star", "m12 3 2.8 5.7 6.3.9-4.55 4.4 1.07 6.3L12 17.3l-5.62 3 1.07-6.3L2.9 9.6l6.3-.9Z") }
    val Home by lazy { lucide("house", "m3 10 9-7 9 7", "M5 9v11h14V9", "M9 20v-8h6v8") }
    val New by lazy { lucide("circle-plus", "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0", "M12 8v8", "M8 12h8") }
}
