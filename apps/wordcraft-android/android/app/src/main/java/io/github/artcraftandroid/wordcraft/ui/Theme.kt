package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Semantic app colours shared by all screens, distinct from document colours. */
object WordColors {
    internal var appearance by mutableStateOf(Appearance.Light)
    val dark get() = appearance.dark
    val darkStatusBar get() = dark || appearance == Appearance.Colorful
    private fun pick(light: Long, dark: Long, gray: Long = dark) = Color(when (appearance) {
        Appearance.Gray -> gray
        Appearance.Black -> dark
        else -> light
    })

    /** Tab and panel titles, their icons, the selection. */
    val Accent get() = pick(0xFF3B5BDB, 0xFF7C93F0)

    /** The darker accent (lighter in dark): the palette's open/close handle, the current tab's label. */
    val AccentDark get() = pick(0xFF2B44A8, 0xFFA5B4F5)

    /** Small coloured parts of icons (list bullets, the strip's more arrow). */
    val AccentLight get() = pick(0xFF4C6EF5, 0xFF7C93F0)
    val AccentDisabled get() = pick(0xFF99A7E1, 0xFF4A5583)

    /** The tab list: its rows, the current tab's row, and their labels. */
    val PopupFill get() = pick(0xFF3B5BDB, 0xFF000000, 0xFF3C3C3C)
    val PopupCurrent get() = pick(0xFFF3F3F3, 0xFF000000, 0xFF303030)
    val OnPopup = Color(0xFFFFFFFF)
    val OnPopupCurrent get() = pick(0xFF2B44A8, 0xFF5B84EC)

    /** The palette header's Tell Me, Undo and Redo. */
    val HeaderIcon get() = pick(0xFF3B5BDB, 0xFFCACACA)
    val HeaderIconDisabled get() = pick(0xFF99A7E1, 0xFF575757, 0xFF666666)

    /** Bottom sheets (the header's overflow). */
    val Sheet get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF3A3A3A)

    /** The floating toolbar's icons, and the circle behind a button that is on. */
    val OnPill get() = pick(0xFF242424, 0xFFFFFFFF)
    val PillOn get() = pick(0xFFDADADA, 0xFF575757, 0xFF666666)

    /** Bars at the top, the floating toolbar and pill. */
    val Bar get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF454545)
    val BarEdge get() = pick(0xFFE4E4E4, 0xFF303030, 0xFF565656)

    /** Behind the status bar. */
    val StatusBand get() = if (appearance == Appearance.Colorful) Accent else pick(0xFFFFFFFF, 0xFF000000, 0xFF3C3C3C)
    val TitleBar get() = if (appearance == Appearance.Colorful) Accent else Bar
    val OnTitleBar get() = if (appearance == Appearance.Colorful) Color.White else OnBar
    val TitleBrand get() = if (appearance == Appearance.Colorful) Color.White else Accent
    val TitleDisabled get() = if (appearance == Appearance.Colorful) Color.White.copy(alpha = 0.42f) else Disabled
    val HomeBack get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF303030)
    val AvatarFill get() = if (appearance == Appearance.Colorful) Color.White.copy(alpha = 0.18f) else pick(0xFFE0E0E0, 0xFFE0E0E0, 0xFF555555)
    val OnAvatar get() = if (appearance == Appearance.Colorful || appearance == Appearance.Gray) Color.White else Color(0xFF303030)

    /** Text and icons on the bars and in the palette. */
    val OnBar get() = pick(0xFF505050, 0xFFE1E1E1)
    val Disabled get() = pick(0xFFB9B9B9, 0xFF666666, 0xFF777777)

    /** The palette's header. */
    val Strip get() = pick(0xFFE6E6E6, 0xFF000000, 0xFF3C3C3C)
    val OnStrip get() = pick(0xFF3A3A38, 0xFFD4D4D4)
    val StripMore get() = pick(0xFFD2D2D2, 0xFF303030, 0xFF565656)
    val HeaderDivider get() = pick(0xFFC5C5C5, 0xFF303030, 0xFF565656)

    /** The palette's lists. */
    val Surface get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF303030)
    val OnSurface get() = pick(0xFF505050, 0xFFE1E1E1)
    val Divider get() = pick(0xFFF3F3F3, 0xFF292929, 0xFF454545)
    val Section get() = pick(0xFFE6E6E6, 0xFF404040, 0xFF505050)
    val Pressed get() = pick(0xFFD2D2D2, 0xFF303030, 0xFF565656)

    /** Around and between the pages, and the pages themselves (black paper in Black, near-black paper in Gray). */
    val Canvas get() = pick(0xFFF7F7F7, 0xFF000000, 0xFF303030)
    val Page get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF303030)
    val darkPaperChannel get() = if (appearance == Appearance.Black) 0 else 0x14
    val DarkPage get() = Color(0xFF000000 or (darkPaperChannel * 0x010101).toLong())

    /** Settings: the page behind them, section titles' rules. */
    val SettingsBack get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF303030)
    val OnSettings get() = pick(0xFF242424, 0xFFE1E1E1)
    val SettingsValue get() = pick(0xFF616161, 0xFFACACAC, 0xFFC7C7C7)
    val PageBorder get() = pick(0xFFC6C6C6, 0xFF303030, 0xFF565656)

    /** Find: its bar, the field in it, the hint, and the matches (the selected one darker). */
    val FindBar get() = pick(0xFFE6E6E6, 0xFF303030, 0xFF565656)
    val FindField get() = pick(0xFFFFFFFF, 0xFF000000, 0xFF3C3C3C)
    val FindHint get() = pick(0xFF8A8A8A, 0xFFACACAC, 0xFFC7C7C7)
    val MatchCurrent get() = pick(0xFFC7BA64, 0xFF867836)
    val MatchOther get() = pick(0xFFFFEE80, 0xFF4D4A2A)

    /** Find settings: an unticked box, its edge, and the line under Replace. */
    val CheckOff get() = pick(0xFFFFFFFF, 0xFF6E6E6E)
    val CheckEdge get() = pick(0xFF8A8A8A, 0xFF666666, 0xFF777777)
    val SettingsLine get() = pick(0xFFC6C6C6, 0xFF969696)

    /** Over the pages behind a bottom sheet. */
    val Scrim = Color(0x52000000)

    val Selection get() = pick(0x553B5BDB, 0x667C93F0)
    val SwitcherDivider get() = pick(0xFFCDCDCD, 0xFF303030, 0xFF565656)
    val Caret get() = pick(0xFF000000, 0xFFFFFFFF)
    val FontColorDefault = Color(0xFFFF0000)
}

/** Four explicit appearances plus the system's light/black setting. */
@Composable
fun WordcraftTheme(choice: String = "system", content: @Composable () -> Unit) {
    val appearance = appearanceFor(choice, isSystemInDarkTheme())
    if (WordColors.appearance != appearance) WordColors.appearance = appearance
    val dark = appearance.dark
    MaterialTheme(
        colorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = WordColors.Accent,
            onPrimary = if (dark) Color(0xFF17235F) else Color.White,
            primaryContainer = if (dark) Color(0xFF37446D) else Color(0xFFE9EDFF),
            onPrimaryContainer = if (dark) Color(0xFFE8EDFF) else Color(0xFF1D307A),
            secondary = WordColors.AccentDark,
            surfaceTint = WordColors.Accent,
            background = WordColors.HomeBack, onBackground = WordColors.OnSurface,
            surface = WordColors.Surface, onSurface = WordColors.OnSurface,
            surfaceVariant = WordColors.Section, onSurfaceVariant = WordColors.SettingsValue,
            surfaceContainerLowest = WordColors.SettingsBack,
            surfaceContainerLow = WordColors.Sheet, surfaceContainer = WordColors.Sheet,
            surfaceContainerHigh = WordColors.Section, surfaceContainerHighest = WordColors.Pressed,
            outline = WordColors.CheckEdge, outlineVariant = WordColors.Divider,
        ),
    ) {
        // pressed controls turn grey, as in the reference app, rather than rippling; text is set
        // without the extra letter spacing Material adds
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides PressedIndication,
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            content = content,
        )
    }
}
