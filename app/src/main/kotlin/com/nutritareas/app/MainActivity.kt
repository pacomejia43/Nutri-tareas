package com.nutritareas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nutritareas.app.ui.chat.ChatScreen
import com.nutritareas.app.ui.settings.SettingsScreen
import com.nutritareas.app.ui.theme.NutriTareasTheme
import com.nutritareas.app.ui.update.UpdateHost

private sealed interface Screen {
    data object Chat : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriTareasTheme {
                NutriTareasRoot()
            }
        }
    }
}

@Composable
private fun NutriTareasRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Chat) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Chat -> ChatScreen(onOpenSettings = { screen = Screen.Settings })
            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Chat })
        }
        UpdateHost()
    }
}
