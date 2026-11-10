package com.omstudy.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omstudy.ai.data.AuthRepository
import com.omstudy.ai.ui.AuthFlow
import com.omstudy.ai.ui.MainApp
import com.omstudy.ai.ui.MainViewModel
import com.omstudy.ai.ui.OmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.themeMode.collectAsState()
            val uid by AuthRepository.uid.collectAsState()
            val dark = when (mode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            OmTheme(dark) {
                if (uid == null) AuthFlow() else MainApp(vm)
            }
        }
    }
}
