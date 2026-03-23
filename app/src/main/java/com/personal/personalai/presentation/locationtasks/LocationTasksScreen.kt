package com.personal.personalai.presentation.locationtasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.model.GeofenceTransitionType
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.TaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTasksScreen(
    innerPadding: PaddingValues,
    viewModel: LocationTasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Tasks") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddDialog,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add location task")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            if (uiState.tasks.isEmpty() && !uiState.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No location tasks", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + to add a task that triggers when you arrive somewhere",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        LocationTaskCard(
                            task = task,
                            onEdit = { viewModel.showEditDialog(task) },
                            onDelete = { viewModel.deleteTask(task) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog || uiState.editingTask != null) {
        LocationTaskDialog(
            uiState = uiState,
            isEditing = uiState.editingTask != null,
            onDismiss = {
                if (uiState.editingTask != null) viewModel.dismissEditDialog()
                else viewModel.dismissAddDialog()
            },
            onSave = viewModel::saveTask,
            onDelete = {
                uiState.editingTask?.let {
                    viewModel.deleteTask(it)
                    viewModel.dismissEditDialog()
                }
            },
            onTitleChanged = viewModel::onTitleChanged,
            onAddressChanged = viewModel::onAddressChanged,
            onGeocodeAddress = viewModel::geocodeAddress,
            onLatChanged = viewModel::onLatChanged,
            onLngChanged = viewModel::onLngChanged,
            onRadiusChanged = viewModel::onRadiusChanged,
            onTransitionChanged = viewModel::onTransitionChanged,
            onTaskTypeChanged = viewModel::onTaskTypeChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onAiPromptChanged = viewModel::onAiPromptChanged,
            onOutputTargetChanged = viewModel::onOutputTargetChanged
        )
    }
}

@Composable
private fun LocationTaskCard(
    task: GeofenceTask,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (task.isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                val locationLine = task.locationName.ifBlank {
                    "%.5f, %.5f".format(task.latitude, task.longitude)
                }
                Text(
                    "$locationLine  •  ${task.radiusMeters.toInt()}m  •  ${task.transitionType.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.taskType == TaskType.AI_PROMPT) {
                    Text(
                        "🤖 ${task.aiPrompt?.take(60) ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LocationTaskDialog(
    uiState: LocationTasksUiState,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onGeocodeAddress: () -> Unit,
    onLatChanged: (String) -> Unit,
    onLngChanged: (String) -> Unit,
    onRadiusChanged: (Float) -> Unit,
    onTransitionChanged: (GeofenceTransitionType) -> Unit,
    onTaskTypeChanged: (TaskType) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAiPromptChanged: (String) -> Unit,
    onOutputTargetChanged: (OutputTarget) -> Unit
) {
    val isValid = uiState.formTitle.isNotBlank() &&
            uiState.formLat.toDoubleOrNull() != null &&
            uiState.formLng.toDoubleOrNull() != null &&
            (uiState.formTaskType == TaskType.REMINDER || uiState.formAiPrompt.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Location Task" else "Add Location Task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.formTitle,
                    onValueChange = onTitleChanged,
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Address search row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.formAddress,
                        onValueChange = onAddressChanged,
                        label = { Text("Address or place name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.isGeocodingAddress) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    )
                    Button(
                        onClick = onGeocodeAddress,
                        enabled = uiState.formAddress.isNotBlank() && !uiState.isGeocodingAddress
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Find", modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider()

                // Manual lat/lng (auto-filled by geocoding, or entered manually)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.formLat,
                        onValueChange = onLatChanged,
                        label = { Text("Latitude *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uiState.formLng,
                        onValueChange = onLngChanged,
                        label = { Text("Longitude *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                Text("Radius: ${uiState.formRadius.toInt()} m", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = uiState.formRadius,
                    onValueChange = onRadiusChanged,
                    valueRange = 50f..1000f,
                    steps = 18
                )

                Text("Trigger", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GeofenceTransitionType.entries.forEach { type ->
                        RadioButton(
                            selected = uiState.formTransition == type,
                            onClick = { onTransitionChanged(type) }
                        )
                        Text(type.name.lowercase(), modifier = Modifier.padding(end = 12.dp))
                    }
                }

                Text("Task Type", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskType.entries.forEach { type ->
                        RadioButton(
                            selected = uiState.formTaskType == type,
                            onClick = { onTaskTypeChanged(type) }
                        )
                        Text(type.name.lowercase().replace('_', ' '), modifier = Modifier.padding(end = 12.dp))
                    }
                }

                if (uiState.formTaskType == TaskType.REMINDER) {
                    OutlinedTextField(
                        value = uiState.formDescription,
                        onValueChange = onDescriptionChanged,
                        label = { Text("Notification message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = uiState.formAiPrompt,
                        onValueChange = onAiPromptChanged,
                        label = { Text("AI Prompt *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Text("Output", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutputTarget.entries.forEach { target ->
                            RadioButton(
                                selected = uiState.formOutputTarget == target,
                                onClick = { onOutputTargetChanged(target) }
                            )
                            Text(target.name.lowercase(), modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }

                if (isEditing) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete Task", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = isValid) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
