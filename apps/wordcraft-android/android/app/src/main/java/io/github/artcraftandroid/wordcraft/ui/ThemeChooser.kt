package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Appearance and language use the same settings window and choice rows. */
@Composable
internal fun ThemeChooser(vm: AppViewModel, close: () -> Unit) {
    SettingsChoiceDialog("Theme", "Choose the app appearance", close) {
        for ((key, label) in APP_THEMES) {
            SettingsChoiceRow(label, vm.themeChoice == key, choose = { vm.chooseTheme(key); close() },
                preview = { ThemePreview(appearanceFor(key, isSystemInDarkTheme())) })
        }
    }
}

@Composable
private fun ThemePreview(appearance: Appearance) {
    val dark = appearance.dark
    val body = when (appearance) { Appearance.Gray -> Color(0xFF303030); Appearance.Black -> Color.Black; else -> Color.White }
    val bar = when (appearance) { Appearance.Colorful -> Color(0xFF3B5BDB); Appearance.Gray -> Color(0xFF454545); Appearance.Black -> Color.Black; else -> Color(0xFFF0F0F0) }
    Column(Modifier.size(52.dp, 38.dp).clip(RoundedCornerShape(6.dp)).background(body).border(1.dp, WordColors.CheckEdge, RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxWidth().height(9.dp).background(bar))
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth(0.8f).height(3.dp).background(if (dark) Color(0xFFBEBEBE) else Color(0xFF8A8A8A), RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth(0.55f).height(3.dp).background(if (dark) Color(0xFF777777) else Color(0xFFCBCBCB), RoundedCornerShape(2.dp)))
        }
    }
}
