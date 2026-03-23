package com.personal.personalai.presentation.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.personalai.domain.model.Memory
import com.personal.personalai.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject

data class FileEntry(
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val lastModified: String
)

data class LibraryUiState(
    val selectedTab: Int = 0,
    val files: List<FileEntry> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val isLoadingFiles: Boolean = true,
    val isLoadingMemories: Boolean = true,
    val openedFile: Pair<String, String>? = null,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val aiFilesDir get() = context.getExternalFilesDir("ai_files")

    init {
        loadFiles()
        viewModelScope.launch {
            memoryRepository.getMemories().collect { memories ->
                _uiState.update { it.copy(memories = memories, isLoadingMemories = false) }
            }
        }
    }

    fun loadFiles() {
        _uiState.update { it.copy(isLoadingFiles = true) }
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                try {
                    val index = File(aiFilesDir, "_index.json")
                    if (!index.exists()) return@withContext emptyList()
                    val json = JSONArray(index.readText())
                    (0 until json.length()).map { i ->
                        val obj = json.getJSONObject(i)
                        FileEntry(
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            sizeBytes = obj.optLong("size_bytes"),
                            lastModified = obj.optString("last_modified")
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _uiState.update { it.copy(files = entries, isLoadingFiles = false) }
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
        if (index == 0) loadFiles()
    }

    fun openFile(entry: FileEntry) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    File(aiFilesDir, entry.name).readText()
                } catch (e: Exception) {
                    null
                }
            }
            if (content != null) {
                _uiState.update { it.copy(openedFile = entry.name to content) }
            } else {
                _uiState.update { it.copy(error = "Could not read file: ${entry.name}") }
            }
        }
    }

    fun closeFile() = _uiState.update { it.copy(openedFile = null) }

    fun deleteMemory(memory: Memory) {
        viewModelScope.launch { memoryRepository.deleteMemory(memory) }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
