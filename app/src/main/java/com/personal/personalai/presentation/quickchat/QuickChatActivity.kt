package com.personal.personalai.presentation.quickchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.personalai.ui.theme.PersonalAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalAITheme {
                QuickChatScreen(onDismiss = { finish() })
            }
        }
    }
}
