package com.ezansi.app.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.LearnerProfile
import com.ezansi.app.core.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the profiles screen.
 *
 * Immutable snapshot updated atomically via [MutableStateFlow.update].
 */
data class ProfilesUiState(
    /** All profiles on this device. */
    val profiles: List<LearnerProfile> = emptyList(),
    /** The currently active profile ID, or null if none set. */
    val activeProfileId: String? = null,
    /** True while profiles are being loaded. */
    val isLoading: Boolean = true,
    /** Text in the "add profile" name field. */
    val newProfileName: String = "",
    /** True if the "add profile" input section is expanded. */
    val isAddingProfile: Boolean = false,
    /** Profile ID pending delete confirmation, or null. */
    val profileToDelete: String? = null,
    /** Error message to display, if any. */
    val errorMessage: String? = null,
)

/**
 * ViewModel for the profiles screen — manages learner profiles on this device.
 *
 * Supports the shared-device scenario (PRD §4.1 Sipho): multiple learners
 * share one phone, each with their own profile, preferences, and chat history.
 *
 * Profile selection is ≤2 taps from launch (PRD §8.4 LP-01):
 * 1. Tap "Profiles" in bottom bar
 * 2. Tap a profile name to switch
 */
class ProfilesViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    /** Refreshes the profile list and active profile from the repository. */
    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = profileRepository.getProfiles()) {
                is EzansiResult.Success -> {
                    val activeResult = profileRepository.getActiveProfile()
                    val activeId = (activeResult as? EzansiResult.Success)?.data?.id

                    _uiState.update {
                        it.copy(
                            profiles = result.data,
                            activeProfileId = activeId,
                            isLoading = false,
                        )
                    }
                }
                is EzansiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
                is EzansiResult.Loading -> { /* No-op for suspend */ }
            }
        }
    }

    /** Creates a new profile with the entered name and sets it as active. */
    fun createProfile() {
        val name = _uiState.value.newProfileName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            when (val result = profileRepository.createProfile(name)) {
                is EzansiResult.Success -> {
                    // Auto-activate the newly created profile
                    profileRepository.setActiveProfile(result.data.id)
                    _uiState.update {
                        it.copy(
                            newProfileName = "",
                            isAddingProfile = false,
                        )
                    }
                    loadProfiles()
                }
                is EzansiResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                is EzansiResult.Loading -> { /* No-op */ }
            }
        }
    }

    /** Switches the active profile to the given ID. */
    fun setActiveProfile(id: String) {
        viewModelScope.launch {
            when (val result = profileRepository.setActiveProfile(id)) {
                is EzansiResult.Success -> {
                    _uiState.update { it.copy(activeProfileId = id) }
                }
                is EzansiResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                is EzansiResult.Loading -> { /* No-op */ }
            }
        }
    }

    /** Shows the delete confirmation for a profile. */
    fun requestDeleteProfile(id: String) {
        _uiState.update { it.copy(profileToDelete = id) }
    }

    /** Cancels the pending delete confirmation. */
    fun cancelDeleteProfile() {
        _uiState.update { it.copy(profileToDelete = null) }
    }

    /** Permanently deletes the profile after confirmation. */
    fun confirmDeleteProfile() {
        val profileId = _uiState.value.profileToDelete ?: return

        viewModelScope.launch {
            when (val result = profileRepository.deleteProfile(profileId)) {
                is EzansiResult.Success -> {
                    _uiState.update { it.copy(profileToDelete = null) }
                    loadProfiles()
                }
                is EzansiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            profileToDelete = null,
                            errorMessage = result.message,
                        )
                    }
                }
                is EzansiResult.Loading -> { /* No-op */ }
            }
        }
    }

    /** Updates the text in the new profile name field. */
    fun onNewProfileNameChanged(name: String) {
        _uiState.update { it.copy(newProfileName = name) }
    }

    /** Toggles the add-profile input section open/closed. */
    fun toggleAddProfile() {
        _uiState.update {
            it.copy(
                isAddingProfile = !it.isAddingProfile,
                newProfileName = "",
            )
        }
    }

    /** Dismisses the error message. */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Factory for creating [ProfilesViewModel] with manual DI.
 *
 * Takes individual interfaces to avoid circular module dependencies.
 */
class ProfilesViewModelFactory(
    private val profileRepository: ProfileRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfilesViewModel::class.java)) {
            return ProfilesViewModel(
                profileRepository = profileRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
