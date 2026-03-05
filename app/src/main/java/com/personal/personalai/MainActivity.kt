package com.personal.personalai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.coroutineScope
import com.llmengine.DownloadState
import com.llmengine.LlmEngine
import com.llmengine.ModelManager
import com.llmengine.Models
import com.personal.personalai.presentation.navigation.AppNavGraph
import com.personal.personalai.ui.theme.PersonalAITheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalAITheme {
                AppNavGraph()
            }
        }
    }
}