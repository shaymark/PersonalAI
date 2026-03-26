package com.personal.personalai.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.personalai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    innerPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── AI Backend card ───────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.ai_backend_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Provider toggle
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.aiProvider == AiProvider.OPENAI,
                            onClick  = { viewModel.setAiProvider(AiProvider.OPENAI) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(stringResource(R.string.ai_provider_openai)) }

                        SegmentedButton(
                            selected = uiState.aiProvider == AiProvider.OLLAMA,
                            onClick  = { viewModel.setAiProvider(AiProvider.OLLAMA) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.ai_provider_ollama)) }
                    }

                    // ── OpenAI tab content ────────────────────────────────────
                    if (uiState.aiProvider == AiProvider.OPENAI) {
                        Text(
                            stringResource(R.string.ai_backend_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value             = uiState.apiKey,
                            onValueChange     = viewModel::onApiKeyChanged,
                            label             = { Text(stringResource(R.string.api_key_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier          = Modifier.fillMaxWidth(),
                            singleLine        = true
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            if (uiState.savedSuccessfully) {
                                Text(
                                    stringResource(R.string.api_key_saved),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(
                                onClick = viewModel::saveApiKey,
                                enabled = !uiState.isSaving
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.save))
                                }
                            }
                        }
                    }

                    // ── Ollama Dev Mode tab content ───────────────────────────
                    if (uiState.aiProvider == AiProvider.OLLAMA) {
                        Text(
                            stringResource(R.string.ollama_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value             = uiState.ollamaUrl,
                            onValueChange     = viewModel::onOllamaUrlChanged,
                            label             = { Text(stringResource(R.string.ollama_url_label)) },
                            placeholder       = { Text("http://10.100.102.75:11434") },
                            modifier          = Modifier.fillMaxWidth(),
                            singleLine        = true
                        )
                        OutlinedTextField(
                            value             = uiState.ollamaModel,
                            onValueChange     = viewModel::onOllamaModelChanged,
                            label             = { Text(stringResource(R.string.ollama_model_label)) },
                            placeholder       = { Text("qwen3.5:4b") },
                            modifier          = Modifier.fillMaxWidth(),
                            singleLine        = true
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            if (uiState.ollamaSavedSuccessfully) {
                                Text(
                                    stringResource(R.string.ollama_saved),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(
                                onClick = viewModel::saveOllamaSettings,
                                enabled = !uiState.isOllamaSaving
                            ) {
                                if (uiState.isOllamaSaving) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.save))
                                }
                            }
                        }
                    }
                }
            }

            // ── Web Search card ───────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier              = Modifier.padding(16.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.web_search_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.web_search_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value             = uiState.serperApiKey,
                        onValueChange     = viewModel::onSerperApiKeyChanged,
                        label             = { Text(stringResource(R.string.serper_api_key_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier          = Modifier.fillMaxWidth(),
                        singleLine        = true
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (uiState.serperSavedSuccessfully) {
                            Text(
                                stringResource(R.string.serper_saved),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(
                            onClick = viewModel::saveSerperApiKey,
                            enabled = !uiState.isSerperSaving
                        ) {
                            if (uiState.isSerperSaving) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }

            // ── Quick Assistant card ──────────────────────────────────────────
            val context = LocalContext.current
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier              = Modifier.padding(16.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Quick Assistant",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Use the AI from any app — even when PersonalAI is closed — by setting it as your device's default assistant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "How to activate: Settings → Apps → Default Apps → Digital Assistant → select PersonalAI. " +
                        "Then long-press the Home button (or Side button on Samsung) to open the quick chat overlay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Default Apps Settings")
                    }
                }
            }

            // ── Data card ─────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier              = Modifier.padding(16.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.data_section_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = viewModel::showClearHistoryDialog,
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.clear_chat_history))
                    }
                }
            }
        }
    }

    // ── Clear history dialog ──────────────────────────────────────────────────
    if (uiState.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearHistoryDialog,
            title            = { Text(stringResource(R.string.clear_chat_history)) },
            text             = { Text(stringResource(R.string.clear_chat_confirm_message)) },
            confirmButton    = {
                TextButton(
                    onClick = viewModel::clearChatHistory,
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearHistoryDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
