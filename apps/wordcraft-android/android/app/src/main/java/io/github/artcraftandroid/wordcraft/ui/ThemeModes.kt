package io.github.artcraftandroid.wordcraft.ui

/** Stored keys stay compatible with the existing light/dark preferences. */
internal val APP_THEMES = linkedMapOf("system" to "System Default", "colorful" to "Colorful", "light" to "White", "gray" to "Dark Gray", "dark" to "Black")
internal enum class Appearance(val dark: Boolean) { Light(false), Colorful(false), Gray(true), Black(true) }
internal fun appearanceFor(choice: String, systemDark: Boolean): Appearance = when (choice) {
    "light" -> Appearance.Light
    "colorful" -> Appearance.Colorful
    "gray" -> Appearance.Gray
    "dark" -> Appearance.Black
    else -> if (systemDark) Appearance.Black else Appearance.Light
}
