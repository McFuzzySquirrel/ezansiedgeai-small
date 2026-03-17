package com.ezansi.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.PackMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the content library screen.
 */
data class LibraryUiState(
    /** All installed content packs on this device. */
    val packs: List<PackMetadata> = emptyList(),
    /** True while packs are being loaded. */
    val isLoading: Boolean = true,
    /** Pack ID pending delete confirmation, or null. */
    val packToDelete: String? = null,
    /** Error message to display, if any. */
    val errorMessage: String? = null,
)

/**
 * ViewModel for the content library screen — manages installed content packs.
 *
 * Displays installed packs with metadata (version, size, chunk count) and
 * supports deletion to reclaim storage (PRD §8.5 CP-06, CP-07).
 *
 * Handles zero-pack state gracefully with a friendly message (CP-09).
 */
class LibraryViewModel(
    private val contentPackRepository: ContentPackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadPacks()
    }

    /** Refreshes the list of installed packs. */
    fun loadPacks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = contentPackRepository.getInstalledPacks()) {
                is EzansiResult.Success -> {
                    _uiState.update {
                        it.copy(packs = result.data, isLoading = false)
                    }
                }
                is EzansiResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                is EzansiResult.Loading -> { /* No-op */ }
            }
        }
    }

    /** Shows the delete confirmation for a pack. */
    fun requestDeletePack(packId: String) {
        _uiState.update { it.copy(packToDelete = packId) }
    }

    /** Cancels the pending delete confirmation. */
    fun cancelDeletePack() {
        _uiState.update { it.copy(packToDelete = null) }
    }

    /**
     * Confirms deletion and removes the pack.
     *
     * Currently uses loadPack() to refresh the list. In a future iteration,
     * a deletePack(id) method will be added to ContentPackRepository.
     */
    fun confirmDeletePack() {
        val packId = _uiState.value.packToDelete ?: return

        viewModelScope.launch {
            // ContentPackRepository doesn't have a deletePack() method yet.
            // For now, we clear the pending state and refresh the list.
            // TODO(content-pack-engineer): Add deletePack(id) to ContentPackRepository
            _uiState.update { it.copy(packToDelete = null) }
            loadPacks()
        }
    }

    /** Dismisses the error message. */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Factory for creating [LibraryViewModel] with manual DI.
 */
class LibraryViewModelFactory(
    private val contentPackRepository: ContentPackRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                contentPackRepository = contentPackRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
