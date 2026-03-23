package com.personal.personalai.presentation.quickchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.personalai.MainActivity
import com.personal.personalai.ui.theme.PersonalAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalAITheme {
                QuickChatScreen(
                    onDismiss = { finish() },
                    onOpenFullChat = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    }
                )
            }
        }
    }
}
