package com.timestampcam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timestampcam.app.ui.LibraryScreen
import com.timestampcam.app.ui.PlayerScreen
import com.timestampcam.app.ui.PresetScreen
import com.timestampcam.app.ui.RecordScreen
import com.timestampcam.app.ui.theme.TimestampCamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimestampCamTheme {
                val vm: MainViewModel = viewModel()
                TimestampCamApp(vm)
            }
        }
    }
}

private sealed interface Page {
    data object Record : Page
    data object Library : Page
    data object Presets : Page
    data class Player(val sessionId: String) : Page
}

@Composable
private fun TimestampCamApp(vm: MainViewModel) {
    val stack = remember { mutableStateListOf<Page>(Page.Record) }
    val page = stack.last()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (page) {
            Page.Record -> RecordScreen(
                vm = vm,
                onOpenLibrary = { stack.add(Page.Library) },
                onOpenPresets = { stack.add(Page.Presets) },
                onRecorded = { id -> stack.add(Page.Player(id)) },
            )
            Page.Library -> LibraryScreen(
                vm = vm,
                onBack = { stack.removeAt(stack.lastIndex) },
                onOpen = { id -> stack.add(Page.Player(id)) },
            )
            Page.Presets -> PresetScreen(
                vm = vm,
                onBack = { stack.removeAt(stack.lastIndex) },
            )
            is Page.Player -> PlayerScreen(
                vm = vm,
                sessionId = page.sessionId,
                onBack = { stack.removeAt(stack.lastIndex) },
            )
        }
    }
    androidx.activity.compose.BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }
}
