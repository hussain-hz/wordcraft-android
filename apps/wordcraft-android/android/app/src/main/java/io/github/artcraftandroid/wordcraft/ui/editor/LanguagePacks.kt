package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.core.enableLanguagePackDownloads
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.WordColors
import io.github.artcraftandroid.wordcraft.ui.uiText
import kotlinx.coroutines.delay
import org.json.JSONObject

/** The engine owns the pinned index, downloads, checksums, installation and cancellation. */
@Composable
fun LanguagePacks(vm: AppViewModel, modifier: Modifier = Modifier) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf(false) }
    fun load(refresh: Boolean = false) {
        vm.query("proof.packs", JSONObject().put("refresh", refresh).toString()) {
            data = it
            failure = it == null
        }
    }
    fun action(id: String, locale: String) {
        vm.query(id, JSONObject().put("locale", locale).toString()) {
            if (it == null) failure = true else load()
        }
    }
    LaunchedEffect(Unit) { load() }
    val busy = data?.optBoolean("refreshing") == true || (data?.optJSONArray("installing")?.length() ?: 0) > 0
    LaunchedEffect(busy) {
        while (busy) { delay(750); load() }
    }
    val rows = data?.optJSONArray("packs")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    val jobs = data?.optJSONArray("installing")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("locale") }.toSet() }.orEmpty()
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(uiText("Language packs"), color = WordColors.Accent, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
            Text(uiText("Basic dictionaries work offline. Download additional languages when needed."), color = WordColors.Disabled, fontSize = 13.sp)
            Text(uiText(if (busy) "Downloading…" else "Refresh language packs"), color = WordColors.Accent, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 12.dp).clickable(enabled = !busy) {
                    if (enableLanguagePackDownloads()) load(true) else failure = true
                })
            if (failure || (data?.optJSONObject("errors")?.length() ?: 0) > 0) {
                Text(uiText("Could not update language packs. Please try again."), color = WordColors.Disabled, fontSize = 14.sp)
            } else if (!busy && rows.isEmpty()) {
                Text(uiText("Refresh to see available language packs."), color = WordColors.Disabled, fontSize = 14.sp)
            }
        }
        items(rows, key = { it.optString("locale") }) { pack ->
            val locale = pack.optString("locale")
            val installed = pack.optBoolean("installed")
            val installing = locale in jobs
            Column(Modifier.fillMaxWidth()) {
                Text(pack.optString("name"), color = WordColors.OnSurface, fontSize = 16.sp)
                Text("$locale · ${pack.optString("licence")}", color = WordColors.Disabled, fontSize = 12.sp)
                Text(uiText(if (installing) "Downloading…" else if (installed) "Remove language pack" else "Download language pack"),
                    color = WordColors.Accent, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(enabled = !installing) {
                        if (installed) action("proof.removePack", locale)
                        else if (enableLanguagePackDownloads()) action("proof.installPack", locale)
                        else failure = true
                    })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
