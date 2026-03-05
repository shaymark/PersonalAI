package com.personal.personalai

import android.content.Context
import android.os.Bundle
import android.util.Log
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
        lifecycle.coroutineScope.launch {
//            testllmEngine(this@MainActivity)
        }
    }
}

suspend fun testllmEngine(context: Context) {
    val manager = ModelManager(context)
    manager.download(Models.GEMMA3_1B_INT4).collect { state ->
        when (state) {
            is DownloadState.Progress -> {  Log.d("MainActivity", state.toString())}
            is DownloadState.Done -> { Log.d("MainActivity", state.toString()) }
            is DownloadState.Failed -> { Log.d("MainActivity", state.toString()) }
        }
    }
    val file = manager.getModelFile(Models.GEMMA3_1B_INT4) ?: error("Not downloaded")
    val session = withContext(Dispatchers.IO) { LlmEngine.load(context,file) }
    val prompt = "you are helpful assistant every response need to be max 10 words, please answer this question: what is the capital of france?"
    session.generate(prompt).collect {
        Log.d("MainActivity", it)
    }

}