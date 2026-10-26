package io.github.artcraftandroid.wordcraft.ui

import io.github.artcraftandroid.wordcraft.ui.uiText
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import io.github.artcraftandroid.wordcraft.R
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.engine.RecentFile
import io.github.artcraftandroid.wordcraft.ui.editor.bottomEdge
import io.github.artcraftandroid.wordcraft.ui.editor.topEdge
import java.util.Calendar

/** Home follows the document app layout, with recent and pinned lists above three actions. */
@Composable
fun StartScreen(vm: AppViewModel, onOpen: () -> Unit) {
    var pinned by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var showingUpdate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val update = vm.appUpdate
    androidx.activity.compose.BackHandler(enabled = showingUpdate) { showingUpdate = false }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    androidx.activity.compose.BackHandler(enabled = searching) { searching = false; query = "" }
    val homeBackground = if (WordColors.dark) androidx.compose.ui.graphics.Color.Black else WordColors.Surface
    Box(Modifier.fillMaxSize().background(homeBackground).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_launcher_tile), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = WordColors.OnPill, fontSize = 18.sp),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { field ->
                            Box { if (query.isEmpty()) Text(uiText("Search documents"), color = WordColors.SettingsValue, fontSize = 18.sp); field() }
                        },
                    )
                    StartIcon(LucideIcons.X, "Close") { searching = false; query = "" }
                } else {
                    Text("WordCraft", color = WordColors.Accent, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (update != null) {
                        Box(Modifier.size(48.dp).clickable { showingUpdate = !showingUpdate }, contentAlignment = Alignment.Center) {
                            Icon(HomeIcons.Update, contentDescription = uiText("An update is available"), tint = WordColors.Accent, modifier = Modifier.size(26.dp))
                            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(6.dp).clip(CircleShape).background(WordColors.Accent))
                        }
                    }
                    StartIcon(LucideIcons.Search, "Search") { searching = true }
                }
                Box(Modifier.size(48.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFFE0E0E0)).clickable(onClick = vm::openSettings), contentAlignment = Alignment.Center) {
                    Icon(HomeIcons.User, contentDescription = uiText("Settings"), tint = androidx.compose.ui.graphics.Color(0xFF303030), modifier = Modifier.size(29.dp))
                }
            }
            Row(Modifier.fillMaxWidth().height(54.dp).bottomEdge(WordColors.Divider)) {
                HomeListTab("Recent", LucideIcons.Clock, !pinned, Modifier.weight(1f)) { pinned = false }
                HomeListTab("Pinned", HomeIcons.Star, pinned, Modifier.weight(1f)) { pinned = true }
            }
            val files = vm.recent.filter { (!pinned || it.uri in vm.pinnedDocuments) && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RecentList(vm, files, onOpen, when { query.isNotBlank() -> "No matching documents"; pinned -> "Pin documents to find them here."; else -> "Documents you open show here." })
            }
            Row(Modifier.fillMaxWidth().height(80.dp).topEdge(WordColors.Divider)) {
                BottomTab("Home", HomeIcons.Home, true, Modifier.weight(1f)) { pinned = false; searching = false; query = "" }
                BottomTab("New", HomeIcons.New, false, Modifier.weight(1f)) { creating = true }
                BottomTab("Open", LucideIcons.FolderOpen, false, Modifier.weight(1f), onOpen)
            }
        }
        if (creating) NewSheet(vm, onClose = { creating = false })
        if (showingUpdate && update != null) {
            Box(Modifier.fillMaxSize().background(WordColors.Scrim).clickable { showingUpdate = false }) {
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(WordColors.Sheet).clickable {}.padding(20.dp)) {
                    Text(uiText("A WordCraft update is available"), color = WordColors.OnPill, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(uiText("Version %1\$s", update.version), color = WordColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { showingUpdate = false }) { Text(uiText("Close"), color = WordColors.SettingsValue) }
                        androidx.compose.material3.TextButton(onClick = { vm.openUpdate(); showingUpdate = false }) { Text(uiText("View release"), color = WordColors.Accent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartIcon(icon: ImageVector, label: String? = null, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label?.let { uiText(it) }, tint = WordColors.OnBar, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun HomeListTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) WordColors.Accent else WordColors.OnBar
    Box(modifier.fillMaxHeight().selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
            Text(uiText(label), color = tint, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(WordColors.Accent))
    }
}

@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) WordColors.Accent else WordColors.OnBar
    Column(modifier.fillMaxHeight().selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(64.dp, 32.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) WordColors.Accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(uiText(label), color = tint, fontSize = 13.sp, maxLines = 1)
    }
}

/** Today, Yesterday, This week, Earlier. */
private fun groupOf(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val days = ((now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis - then.timeInMillis) / DateUtils.DAY_IN_MILLIS).toInt()
    return when {
        then.timeInMillis >= now.timeInMillis -> "Today"
        days < 1 -> "Yesterday"
        days < 7 -> "This week"
        else -> "Earlier"
    }
}

@Composable
private fun RecentList(vm: AppViewModel, files: List<RecentFile>, onOpen: () -> Unit, emptyMessage: String) {
    if (files.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(uiText(emptyMessage), color = WordColors.OnSurface, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            if (emptyMessage == "Documents you open show here.") Text(uiText("Open a document"),
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(WordColors.Accent).clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        return
    }
    val groups = files.groupBy { groupOf(it.opened) }
    LazyColumn(Modifier.fillMaxSize()) {
        for (g in listOf("Today", "Yesterday", "This week", "Earlier")) {
            val list = groups[g] ?: continue
            item(key = "h-$g") {
                Text(uiText(g), color = WordColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(list, key = { it.uri }) { r -> RecentRow(vm, r) }
        }
    }
}

@Composable
private fun RecentRow(vm: AppViewModel, r: RecentFile) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(82.dp).clickable { vm.openRecent(r) }.bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(LucideIcons.FileText, contentDescription = null, tint = WordColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name.substringBeforeLast('.', r.name), color = WordColors.OnPill, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                r.place,
                color = WordColors.OnSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            StartIcon(LucideIcons.EllipsisVertical, "More options") { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = WordColors.Sheet, shape = RoundedCornerShape(16.dp)) {
                val pinned = r.uri in vm.pinnedDocuments
                DropdownMenuItem(text = { Text(uiText(if (pinned) "Unpin" else "Pin")) }, leadingIcon = { Icon(HomeIcons.Star, contentDescription = null, tint = WordColors.Accent) }, onClick = {
                    menu = false
                    vm.togglePinned(r.uri)
                })
                DropdownMenuItem(text = { Text(uiText("Add to Home screen")) }, leadingIcon = { Icon(HomeIcons.Home, contentDescription = null, tint = WordColors.OnBar) }, onClick = {
                    menu = false
                    vm.pinToHome(r)
                })
                DropdownMenuItem(text = { Text(uiText("Remove from list")) }, leadingIcon = { Icon(LucideIcons.X, contentDescription = null, tint = WordColors.OnBar) }, onClick = {
                    menu = false
                    vm.removeRecent(r.uri)
                })
            }
        }
    }
}

@Composable
private fun OpenRow(icon: ImageVector, title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).clickable(enabled = enabled, onClick = onClick).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(icon, contentDescription = null, tint = WordColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(uiText(title), color = WordColors.OnPill, fontSize = 16.sp)
            Text(uiText(detail), color = WordColors.OnSurface, fontSize = 13.sp)
        }
    }
}

/** Create new: a blank document or one of WordCraft's own templates. */
@Composable
private fun NewSheet(vm: AppViewModel, onClose: () -> Unit) {
    // Back closes the sheet, not the app
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99000000)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(WordColors.Surface).clickable {}.padding(bottom = 12.dp),
        ) {
            Text(uiText("New"), color = WordColors.OnPill, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
            for ((label, template) in listOf("Blank document" to "blank", "Sample" to "sample", "Letter" to "letter", "Resume" to "resume", "Report" to "report")) {
                OpenRow(if (template == "blank") LucideIcons.FilePlus2 else LucideIcons.FileText, label, if (template == "blank") "An empty page" else "A WordCraft template", enabled = vm.engine != null) {
                    onClose()
                    vm.newDocument(template)
                }
            }
        }
    }
}
