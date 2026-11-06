package com.zhiwei.shiyin.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhiwei.shiyin.AppContainer
import com.zhiwei.shiyin.ui.library.LibraryScreen
import com.zhiwei.shiyin.ui.playback.PlaybackScreen
import com.zhiwei.shiyin.ui.presets.PresetScreen
import com.zhiwei.shiyin.ui.record.RecordScreen

object Routes {
    const val Library = "library"
    const val Record = "record"
    const val Presets = "presets"
    const val Playback = "playback/{id}"

    fun playback(id: String) = "playback/$id"
}

@Composable
fun ShiyinNav(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.Library) {
        composable(Routes.Library) {
            LibraryScreen(
                recordings = container.recordings,
                onRecord = { nav.navigate(Routes.Record) },
                onPresets = { nav.navigate(Routes.Presets) },
                onOpen = { id -> nav.navigate(Routes.playback(id)) },
            )
        }
        composable(Routes.Record) {
            RecordScreen(
                recordings = container.recordings,
                presets = container.presets,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.navigate(Routes.playback(id)) {
                        popUpTo(Routes.Record) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Presets) {
            PresetScreen(repository = container.presets, onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.Playback,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            PlaybackScreen(
                recordings = container.recordings,
                presets = container.presets,
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
