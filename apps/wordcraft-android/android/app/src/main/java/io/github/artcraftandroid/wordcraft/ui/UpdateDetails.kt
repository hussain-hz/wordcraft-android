package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.withLink
import androidx.compose.ui.window.DialogProperties
import io.github.artcraftandroid.wordcraft.updates.releaseNotes

@Composable
internal fun UpdateDetails(vm: AppViewModel) {
    val update = vm.appUpdate ?: return
    val config = LocalConfiguration.current
    val notes = releaseNotes(update.notes, config.locales[0].language == "ar")
    Dialog(onDismissRequest = { vm.showingUpdate = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = (config.screenHeightDp * 0.82f).dp)
                .background(WordColors.Sheet, RoundedCornerShape(20.dp)).padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("A WordCraft update is available"), color = WordColors.OnPill, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(uiText("Version %1\$s", update.version), color = WordColors.SettingsValue, fontSize = 14.sp)
                    }
                    IconButton(onClick = { vm.showingUpdate = false }) { Icon(LucideIcons.X, uiText("Close"), tint = WordColors.OnPill) }
                }
                Text(uiText("What's new"), color = WordColors.Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (notes.isBlank()) Text(uiText("Release notes are not available."), color = WordColors.SettingsValue)
                    else notes.lines().forEach { line ->
                        val heading = line.startsWith("#")
                        val text = line.trim().trimStart('#').trim().replace("**", "")
                            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                            .removePrefix("<!--").removeSuffix("-->")
                        if (text.isNotBlank()) Text(text, color = WordColors.OnPill, fontSize = 14.sp, lineHeight = 22.sp,
                            fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (vm.downloadingUpdate) {
                    val fraction = if (vm.updateTotal > 0) (vm.updateBytes.toFloat() / vm.updateTotal).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = WordColors.Accent, trackColor = WordColors.Divider)
                    Text(uiText("Downloading update: %1\$d%%", (fraction * 100).toInt()), color = WordColors.SettingsValue, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = vm::cancelUpdateDownload, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel download"), color = WordColors.Accent) }
                } else {
                    if (vm.updateDownloadError) Text(uiText("Couldn't download or verify the update. Please try again."), color = WordColors.OnPill, fontSize = 14.sp)
                    Button(onClick = { if (vm.downloadedUpdate != null) vm.installDownloadedUpdate() else vm.downloadUpdate() },
                        modifier = Modifier.fillMaxWidth(), enabled = update.asset != null,
                        colors = ButtonDefaults.buttonColors(containerColor = WordColors.Accent)) {
                        Text(uiText(if (vm.downloadedUpdate != null) "Install update" else "Download update"))
                    }
                    if (vm.downloadedUpdate != null) Text(uiText("Android may ask you to allow updates from WordCraft. Your documents and settings will be kept."), color = WordColors.SettingsValue, fontSize = 12.sp, lineHeight = 18.sp)
                }
                val more = uiText("For more details, ")
                val here = uiText("click here")
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    append(more.trimEnd()); append(" ")
                    withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("release", styles = androidx.compose.ui.text.TextLinkStyles(
                        style = androidx.compose.ui.text.SpanStyle(color = WordColors.Accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    )) { vm.openUpdate() }) { append(here) }
                }, color = WordColors.SettingsValue, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp))
            }
        }
    }
}
