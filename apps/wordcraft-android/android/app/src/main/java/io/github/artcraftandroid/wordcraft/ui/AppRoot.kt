package io.github.artcraftandroid.wordcraft.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.artcraftandroid.wordcraft.core.engineVersion
import io.github.artcraftandroid.wordcraft.ui.editor.EditorScreen
import io.github.artcraftandroid.wordcraft.ui.editor.SettingsScreen
import io.github.artcraftandroid.wordcraft.ui.editor.TextEncodingScreen

/**
 * The app's screens, and its one-time messages. Going on to a screen slides it in from the side
 * over the whole width; going back slides the other way.
 */
@Composable
fun AppRoot(vm: AppViewModel, onOpen: () -> Unit) {
    val base = LocalContext.current
    val current = androidx.compose.ui.platform.LocalConfiguration.current
    val configuration = androidx.compose.runtime.remember(current, vm.languageChoice) {
        android.content.res.Configuration(current).apply {
            if (vm.languageChoice.isNotEmpty()) setLocale(java.util.Locale.forLanguageTag(vm.languageChoice))
        }
    }
    val localized = androidx.compose.runtime.remember(base, configuration) { base.createConfigurationContext(configuration) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalContext provides localized,
        androidx.compose.ui.platform.LocalConfiguration provides configuration,
        androidx.compose.ui.platform.LocalLayoutDirection provides if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        AppScreens(vm, onOpen)
    }
}

@Composable
private fun AppScreens(vm: AppViewModel, onOpen: () -> Unit) {
    val context = LocalContext.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, localizedUiText(context, it), Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }
    Box {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                val dir = (if (targetState.ordinal > initialState.ordinal) 1 else -1) * (if (rtl) -1 else 1)
                slideInHorizontally(tween(Motion.BASIC, easing = Motion.EaseOutSharp)) { it * dir } togetherWith
                    slideOutHorizontally(tween(Motion.BASIC, easing = Motion.EaseOutSharp)) { -it * dir }
            },
            label = "screens",
        ) { screen ->
            when (screen) {
                Screen.Start -> StartScreen(vm, onOpen)
                Screen.Editor -> EditorScreen(vm)
                Screen.Settings -> SettingsScreen(vm, runCatching { engineVersion() }.getOrDefault(""))
            }
        }
        // a text file's encoding is chosen over whatever screen is showing
        if (vm.encodingChoice != null) TextEncodingScreen(vm)
    }
}
