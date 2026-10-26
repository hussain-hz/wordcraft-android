package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared centered settings window retaining the approved previews/checkmark design. */
@Composable
internal fun SettingsChoiceDialog(title: String, description: String, close: () -> Unit, choices: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
            .systemBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight * 0.9f)
                .clip(RoundedCornerShape(20.dp)).background(WordColors.Sheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(title), color = WordColors.OnPill, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = close) { Icon(LucideIcons.X, uiText("Close"), tint = WordColors.OnPill) }
                }
                Text(uiText(description), color = WordColors.SettingsValue, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup(), content = choices)
            }
        }
    }
}

@Composable
internal fun SettingsChoiceRow(label: String, selected: Boolean, choose: () -> Unit, preview: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (preview != null) 68.dp else 58.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) WordColors.Accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
        .selectable(selected, role = Role.RadioButton, onClick = choose)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (preview != null) { preview(); Spacer(Modifier.width(14.dp)) }
        Text(uiText(label), color = WordColors.OnPill, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(LucideIcons.Check, contentDescription = null, tint = WordColors.Accent, modifier = Modifier.size(24.dp))
        else Spacer(Modifier.size(24.dp))
    }
}

@Composable
internal fun LanguageChooser(vm: AppViewModel, close: () -> Unit) {
    SettingsChoiceDialog("Language", "Choose the app language", close) {
        for ((tag, label) in listOf("" to "System Default", "ar" to "العربية", "en" to "English")) {
            SettingsChoiceRow(label, vm.languageChoice == tag, choose = { close(); vm.chooseLanguage(tag) })
        }
    }
}
