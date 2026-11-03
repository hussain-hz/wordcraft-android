package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DocumentPasswordDialog(vm: AppViewModel) {
    var password by remember(vm.passwordChoice) { mutableStateOf("") }
    SettingsChoiceDialog("Password", "Enter the document password", vm::cancelPassword) {
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(uiText("Password")) }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = WordColors.OnPill, unfocusedTextColor = WordColors.OnPill,
                focusedBorderColor = WordColors.Accent, unfocusedBorderColor = WordColors.SettingsValue,
                focusedLabelColor = WordColors.Accent, unfocusedLabelColor = WordColors.SettingsValue, cursorColor = WordColors.Accent))
        Text(uiText("Edits are saved as a new document."), color = WordColors.SettingsValue, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        vm.passwordError?.let { Text(uiText(it), color = WordColors.Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = vm::cancelPassword, enabled = !vm.passwordOpening) { Text(uiText("Cancel"), color = WordColors.Accent) }
            TextButton(onClick = { vm.unlockDocument(password); password = "" }, enabled = password.isNotEmpty() && !vm.passwordOpening) {
                Text(uiText("Open"), color = WordColors.Accent)
            }
        }
    }
}
