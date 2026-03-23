package com.personal.personalai.presentation.locationtasks

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.data.geofence.GeofenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import com.personal.personalai.domain.model.GeofenceTask
import com.personal.personalai.domain.model.GeofenceTransitionType
import com.personal.personalai.domain.model.OutputTarget
import com.personal.personalai.domain.model.TaskType
import com.personal.personalai.domain.repository.GeofenceTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationTasksUiState(
    val tasks: List<GeofenceTask> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingTask: GeofenceTask? = null,
    // Form fields
    val formTitle: String = "",
    val formAddress: String = "",
    val isGeocodingAddress: Boolean = false,
    val formLat: String = "",
    val formLng: String = "",
    val formRadius: Float = 100f,
    val formTransition: GeofenceTransitionType = GeofenceTransitionType.ENTER,
    val formTaskType: TaskType = TaskType.REMINDER,
    val formDescription: String = "",
    val formAiPrompt: String = "",
    val formOutputTarget: OutputTarget = OutputTarget.NOTIFICATION,
    val error: String? = null
)

@HiltViewModel
class LocationTasksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GeofenceTaskRepository,
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationTasksUiState(isLoading = true))
    val uiState: StateFlow<LocationTasksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks, isLoading = false) }
            }
        }
    }

    fun showAddDialog() = _uiState.update { it.copy(showAddDialog = true) + resetForm() }
    fun dismissAddDialog() = _uiState.update { it.copy(showAddDialog = false) + resetForm() }

    fun showEditDialog(task: GeofenceTask) = _uiState.update {
        it.copy(
            editingTask       = task,
            formTitle         = task.title,
            formAddress       = task.locationName,
            formLat           = task.latitude.toString(),
            formLng           = task.longitude.toString(),
            formRadius        = task.radiusMeters,
            formTransition    = task.transitionType,
            formTaskType      = task.taskType,
            formDescription   = task.description,
            formAiPrompt      = task.aiPrompt ?: "",
            formOutputTarget  = task.outputTarget
        )
    }

    fun dismissEditDialog() = _uiState.update { it.copy(editingTask = null) + resetForm() }

    fun onTitleChanged(v: String) = _uiState.update { it.copy(formTitle = v) }
    fun onAddressChanged(v: String) = _uiState.update { it.copy(formAddress = v) }
    fun onLatChanged(v: String) = _uiState.update { it.copy(formLat = v) }
    fun onLngChanged(v: String) = _uiState.update { it.copy(formLng = v) }
    fun onRadiusChanged(v: Float) = _uiState.update { it.copy(formRadius = v) }
    fun onTransitionChanged(v: GeofenceTransitionType) = _uiState.update { it.copy(formTransition = v) }
    fun onTaskTypeChanged(v: TaskType) = _uiState.update { it.copy(formTaskType = v) }
    fun onDescriptionChanged(v: String) = _uiState.update { it.copy(formDescription = v) }
    fun onAiPromptChanged(v: String) = _uiState.update { it.copy(formAiPrompt = v) }
    fun onOutputTargetChanged(v: OutputTarget) = _uiState.update { it.copy(formOutputTarget = v) }

    fun geocodeAddress() {
        val address = _uiState.value.formAddress.trim()
        if (address.isBlank()) return
        _uiState.update { it.copy(isGeocodingAddress = true, error = null) }
        viewModelScope.launch {
            val result = resolveAddress(address)
            if (result != null) {
                _uiState.update {
                    it.copy(
                        formLat = result.first.toString(),
                        formLng = result.second.toString(),
                        formAddress = result.third,
                        isGeocodingAddress = false
                    )
                }
            } else {
                _uiState.update { it.copy(isGeocodingAddress = false, error = "Address not found") }
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveAddress(address: String): Triple<Double, Double, String>? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(address, 1) { results ->
                            val loc = results.firstOrNull()
                            cont.resume(
                                if (loc != null) Triple(loc.latitude, loc.longitude, loc.getAddressLine(0) ?: address)
                                else null
                            )
                        }
                    }
                } else {
                    val results = geocoder.getFromLocationName(address, 1)
                    val loc = results?.firstOrNull() ?: return@withContext null
                    Triple(loc.latitude, loc.longitude, loc.getAddressLine(0) ?: address)
                }
            } catch (e: Exception) {
                null
            }
        }

    fun saveTask() {
        val s = _uiState.value
        val lat = s.formLat.toDoubleOrNull() ?: run {
            _uiState.update { it.copy(error = "Invalid latitude") }; return
        }
        val lng = s.formLng.toDoubleOrNull() ?: run {
            _uiState.update { it.copy(error = "Invalid longitude") }; return
        }

        viewModelScope.launch {
            val existing = s.editingTask
            val task = GeofenceTask(
                id             = existing?.id ?: 0,
                title          = s.formTitle.trim(),
                locationName   = s.formAddress.trim(),
                latitude       = lat,
                longitude      = lng,
                radiusMeters   = s.formRadius,
                transitionType = s.formTransition,
                taskType       = s.formTaskType,
                description    = s.formDescription.trim(),
                aiPrompt       = s.formAiPrompt.trim().takeIf { it.isNotBlank() },
                outputTarget   = s.formOutputTarget
            )
            if (existing != null) {
                geofenceManager.remove(existing.id)
                repository.updateTask(task)
                geofenceManager.register(task)
            } else {
                val id = repository.insertTask(task)
                geofenceManager.register(task.copy(id = id))
            }
            _uiState.update { it.copy(showAddDialog = false, editingTask = null) + resetForm() }
        }
    }

    fun deleteTask(task: GeofenceTask) {
        viewModelScope.launch {
            geofenceManager.remove(task.id)
            repository.deleteTask(task)
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun resetForm(): LocationTasksUiState.() -> LocationTasksUiState = {
        copy(
            formTitle            = "",
            formAddress          = "",
            isGeocodingAddress   = false,
            formLat              = "",
            formLng              = "",
            formRadius       = 100f,
            formTransition   = GeofenceTransitionType.ENTER,
            formTaskType     = TaskType.REMINDER,
            formDescription  = "",
            formAiPrompt     = "",
            formOutputTarget = OutputTarget.NOTIFICATION,
            error            = null
        )
    }

    // Extension to allow combining state updates with reset
    private operator fun LocationTasksUiState.plus(fn: LocationTasksUiState.() -> LocationTasksUiState) = fn()
}
