package com.personal.personalai.presentation.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.personalai.R
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.RecurrenceType
import com.personal.personalai.domain.model.ScheduledTask
import com.personal.personalai.domain.model.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    innerPadding: PaddingValues,
    viewModel: ScheduledTasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_tasks_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddDialog,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task_description))
            }
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            if (uiState.tasks.isEmpty() && !uiState.isLoading) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onEdit = { viewModel.showEditDialog(task) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AddTaskDialog(
            uiState = uiState,
            onTitleChanged = viewModel::onTitleChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onScheduledAtChanged = viewModel::onScheduledAtChanged,
            onTaskTypeChanged = viewModel::onTaskTypeChanged,
            onAiPromptChanged = viewModel::onAiPromptChanged,
            onOutputTargetChanged = viewModel::onOutputTargetChanged,
            onRecurrenceTypeChanged = viewModel::onRecurrenceTypeChanged,
            onConfirm = viewModel::createTask,
            onDismiss = viewModel::dismissAddDialog
        )
    }

    if (uiState.editingTask != null) {
        EditTaskDialog(
            uiState = uiState,
            onTitleChanged = viewModel::onTitleChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onScheduledAtChanged = viewModel::onScheduledAtChanged,
            onTaskTypeChanged = viewModel::onTaskTypeChanged,
            onAiPromptChanged = viewModel::onAiPromptChanged,
            onOutputTargetChanged = viewModel::onOutputTargetChanged,
            onRecurrenceTypeChanged = viewModel::onRecurrenceTypeChanged,
            onConfirm = viewModel::saveEditedTask,
            onDismiss = viewModel::dismissEditDialog,
            onDelete = viewModel::deleteEditingTask
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            stringResource(R.string.no_scheduled_tasks),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            stringResource(R.string.no_tasks_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TaskCard(task: ScheduledTask, onEdit: () -> Unit) {
    val isOverdue = task.scheduledAt < System.currentTimeMillis() && !task.isCompleted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (task.taskType == TaskType.AI_PROMPT) {
                Text(
                    text = "🤖",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium
                )
                val subtitle = if (task.taskType == TaskType.AI_PROMPT) {
                    task.aiPrompt
                } else {
                    task.description.ifBlank { null }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val recurrenceLabel = when (task.recurrenceType) {
                    RecurrenceType.DAILY  -> stringResource(R.string.repeats_daily)
                    RecurrenceType.WEEKLY -> stringResource(R.string.repeats_weekly)
                    RecurrenceType.NONE   -> ""
                }
                Text(
                    text = formatScheduledTime(task.scheduledAt) + if (recurrenceLabel.isNotEmpty()) " · $recurrenceLabel" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun TaskDialogContent(
    uiState: ScheduledTasksUiState,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onScheduledAtChanged: (Long) -> Unit,
    onTaskTypeChanged: (TaskType) -> Unit,
    onAiPromptChanged: (String) -> Unit,
    onOutputTargetChanged: (OutputTarget) -> Unit,
    onRecurrenceTypeChanged: (RecurrenceType) -> Unit
) {
    val context = LocalContext.current
    val isFuture = uiState.newTaskScheduledAt > System.currentTimeMillis()
    val scheduledTimeText = stringResource(R.string.scheduled_time, formatScheduledTime(uiState.newTaskScheduledAt))
    val futureWarning = if (!isFuture) " " + stringResource(R.string.must_be_future) else ""

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        OutlinedTextField(
            value = uiState.newTaskTitle,
            onValueChange = onTitleChanged,
            label = { Text(stringResource(R.string.task_title_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Task type selector
        Text(stringResource(R.string.task_type_label), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.newTaskType == TaskType.REMINDER,
                onClick = { onTaskTypeChanged(TaskType.REMINDER) },
                label = { Text(stringResource(R.string.reminder)) }
            )
            FilterChip(
                selected = uiState.newTaskType == TaskType.AI_PROMPT,
                onClick = { onTaskTypeChanged(TaskType.AI_PROMPT) },
                label = { Text(stringResource(R.string.ai_task)) }
            )
        }

        // Conditional: description for Reminder, prompt + output for AI Task
        if (uiState.newTaskType == TaskType.REMINDER) {
            OutlinedTextField(
                value = uiState.newTaskDescription,
                onValueChange = onDescriptionChanged,
                label = { Text(stringResource(R.string.task_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
        } else {
            OutlinedTextField(
                value = uiState.newAiPrompt,
                onValueChange = onAiPromptChanged,
                label = { Text(stringResource(R.string.prompt_label)) },
                placeholder = { Text(stringResource(R.string.prompt_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            Text(stringResource(R.string.deliver_to_label), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = uiState.newOutputTarget == OutputTarget.NOTIFICATION,
                    onClick = { onOutputTargetChanged(OutputTarget.NOTIFICATION) },
                    label = { Text(stringResource(R.string.notification)) }
                )
                FilterChip(
                    selected = uiState.newOutputTarget == OutputTarget.CHAT,
                    onClick = { onOutputTargetChanged(OutputTarget.CHAT) },
                    label = { Text(stringResource(R.string.chat)) }
                )
                FilterChip(
                    selected = uiState.newOutputTarget == OutputTarget.BOTH,
                    onClick = { onOutputTargetChanged(OutputTarget.BOTH) },
                    label = { Text(stringResource(R.string.both)) }
                )
            }
        }

        // Recurrence section
        Text(stringResource(R.string.repeat_label), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = uiState.newRecurrenceType == RecurrenceType.NONE,
                onClick = { onRecurrenceTypeChanged(RecurrenceType.NONE) },
                label = { Text(stringResource(R.string.none)) }
            )
            FilterChip(
                selected = uiState.newRecurrenceType == RecurrenceType.DAILY,
                onClick = { onRecurrenceTypeChanged(RecurrenceType.DAILY) },
                label = { Text(stringResource(R.string.daily)) }
            )
            FilterChip(
                selected = uiState.newRecurrenceType == RecurrenceType.WEEKLY,
                onClick = { onRecurrenceTypeChanged(RecurrenceType.WEEKLY) },
                label = { Text(stringResource(R.string.weekly)) }
            )
        }

        // Scheduled time display
        Text(
            text = scheduledTimeText + futureWarning,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
        )

        // Date + time picker button
        Button(
            onClick = {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = uiState.newTaskScheduledAt
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val newCal = Calendar.getInstance()
                                newCal.set(year, month, day, hour, minute, 0)
                                newCal.set(Calendar.MILLISECOND, 0)
                                onScheduledAtChanged(newCal.timeInMillis)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text(stringResource(R.string.pick_date_time))
        }

        // Quick-time chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = false,
                onClick = { onScheduledAtChanged(System.currentTimeMillis() + 30 * 60 * 1000L) },
                label = { Text(stringResource(R.string.add_30_min)) }
            )
            FilterChip(
                selected = false,
                onClick = { onScheduledAtChanged(System.currentTimeMillis() + 60 * 60 * 1000L) },
                label = { Text(stringResource(R.string.add_1_hour)) }
            )
            FilterChip(
                selected = false,
                onClick = { onScheduledAtChanged(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) },
                label = { Text(stringResource(R.string.add_1_day)) }
            )
        }
    }
}

@Composable
private fun AddTaskDialog(
    uiState: ScheduledTasksUiState,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onScheduledAtChanged: (Long) -> Unit,
    onTaskTypeChanged: (TaskType) -> Unit,
    onAiPromptChanged: (String) -> Unit,
    onOutputTargetChanged: (OutputTarget) -> Unit,
    onRecurrenceTypeChanged: (RecurrenceType) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isFuture = uiState.newTaskScheduledAt > System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_task_dialog_title)) },
        text = {
            TaskDialogContent(
                uiState = uiState,
                onTitleChanged = onTitleChanged,
                onDescriptionChanged = onDescriptionChanged,
                onScheduledAtChanged = onScheduledAtChanged,
                onTaskTypeChanged = onTaskTypeChanged,
                onAiPromptChanged = onAiPromptChanged,
                onOutputTargetChanged = onOutputTargetChanged,
                onRecurrenceTypeChanged = onRecurrenceTypeChanged
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = uiState.newTaskTitle.isNotBlank() &&
                    (uiState.newTaskType == TaskType.REMINDER || uiState.newAiPrompt.isNotBlank()) &&
                    isFuture
            ) { Text(stringResource(R.string.schedule)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun EditTaskDialog(
    uiState: ScheduledTasksUiState,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onScheduledAtChanged: (Long) -> Unit,
    onTaskTypeChanged: (TaskType) -> Unit,
    onAiPromptChanged: (String) -> Unit,
    onOutputTargetChanged: (OutputTarget) -> Unit,
    onRecurrenceTypeChanged: (RecurrenceType) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val isFuture = uiState.newTaskScheduledAt > System.currentTimeMillis()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = {
                Text(stringResource(R.string.delete_task_message, uiState.newTaskTitle))
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_task_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskDialogContent(
                    uiState = uiState,
                    onTitleChanged = onTitleChanged,
                    onDescriptionChanged = onDescriptionChanged,
                    onScheduledAtChanged = onScheduledAtChanged,
                    onTaskTypeChanged = onTaskTypeChanged,
                    onAiPromptChanged = onAiPromptChanged,
                    onOutputTargetChanged = onOutputTargetChanged,
                    onRecurrenceTypeChanged = onRecurrenceTypeChanged
                )

                // Delete task button
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.delete_task))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = uiState.newTaskTitle.isNotBlank() &&
                    (uiState.newTaskType == TaskType.REMINDER || uiState.newAiPrompt.isNotBlank()) &&
                    isFuture
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Preview
@Composable
private fun AddTaskDialogPreview() {
    MaterialTheme {
        AddTaskDialog(
            uiState = ScheduledTasksUiState(),
            onTitleChanged = {},
            onDescriptionChanged = {},
            onScheduledAtChanged = {},
            onTaskTypeChanged = {},
            onAiPromptChanged = {},
            onOutputTargetChanged = {},
            onRecurrenceTypeChanged = {},
            onConfirm = {},
            onDismiss = {}
        )
    }
}

private fun formatScheduledTime(epochMillis: Long): String =
    SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()).format(Date(epochMillis))
