package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.service.NotificationAlertService
import com.example.ui.screens.MainAppContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.POMasterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationAlertService.initializeNotificationChannels(this)

        val viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[POMasterViewModel::class.java]

        setContent {
            val appSettings by viewModel.appSettingsState.collectAsState()
            MyApplicationTheme(
                themeMode = appSettings.themeMode,
                accentColorName = appSettings.accentColorName
            ) {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

