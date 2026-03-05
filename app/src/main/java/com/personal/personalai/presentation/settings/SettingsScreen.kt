package com.personal.personalai.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmengine.ModelDescriptor
import com.personal.personalai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
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
                            selected = uiState.aiProvider == AiProvider.LOCAL_LLM,
                            onClick  = { viewModel.setAiProvider(AiProvider.LOCAL_LLM) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.ai_provider_local)) }
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

                    // ── Local LLM tab content ─────────────────────────────────
                    if (uiState.aiProvider == AiProvider.LOCAL_LLM) {
                        Text(
                            stringResource(R.string.local_llm_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.local_models_title),
                            style = MaterialTheme.typography.labelMedium
                        )

                        uiState.availableModels.forEachIndexed { index, model ->
                            if (index > 0) HorizontalDivider()
                            ModelRow(
                                model         = model,
                                isDownloaded  = model.id in uiState.downloadedModelIds,
                                isSelected    = model.id == uiState.selectedModelId,
                                isDownloading = model.id == uiState.downloadingModelId,
                                progress      = uiState.downloadProgress[model.id] ?: 0f,
                                onDownload    = { viewModel.downloadModel(model) },
                                onSelect      = { viewModel.selectModel(model) },
                                onDelete      = { viewModel.deleteModel(model) }
                            )
                        }
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

    // ── Download progress dialog ──────────────────────────────────────────────
    val downloadingId = uiState.downloadingModelId
    if (downloadingId != null) {
        val model    = uiState.availableModels.find { it.id == downloadingId }
        val fraction = uiState.downloadProgress[downloadingId] ?: 0f
        AlertDialog(
            onDismissRequest = { /* non-dismissible while downloading */ },
            title = {
                Text(stringResource(R.string.downloading_model, model?.displayName ?: downloadingId))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress    = { fraction },
                        modifier    = Modifier.fillMaxWidth()
                    )
                    Text(
                        text  = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }

    // ── Download error dialog ─────────────────────────────────────────────────
    val downloadError = uiState.downloadError
    if (downloadError != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDownloadError,
            title            = { Text(stringResource(R.string.download_failed_title)) },
            text             = { Text(downloadError) },
            confirmButton    = {
                TextButton(onClick = viewModel::dismissDownloadError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

// ── Model row composable ──────────────────────────────────────────────────────

@Composable
private fun ModelRow(
    model: ModelDescriptor,
    isDownloaded: Boolean,
    isSelected: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
        },
        supportingContent = if (isDownloading) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${(progress * 100).toInt()}%  •  ${stringResource(R.string.downloading)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        leadingContent = {
            if (isDownloaded) {
                RadioButton(
                    selected = isSelected,
                    onClick  = onSelect
                )
            } else {
                // Placeholder to keep alignment consistent
                Box(Modifier.size(48.dp))
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    isDownloading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    isDownloaded -> {
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.model_selected),
                                tint   = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.download)
                            )
                        }
                    }
                }
            }
        }
    )
}
