package com.ezansi.app.feature.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.LearnerPreference
import com.ezansi.app.core.data.PreferenceRepository
import com.ezansi.app.core.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the preferences screen.
 */
data class PreferencesUiState(
    /** The active profile ID whose preferences are being edited. */
    val profileId: String? = null,
    /** Active profile display name. */
    val profileName: String? = null,
    /** Current explanation style selection. */
    val explanationStyle: String = "step_by_step",
    /** Current reading level selection. */
    val readingLevel: String = "simple",
    /** Current example type selection. */
    val exampleType: String = "everyday",
    /** True while preferences are being loaded. */
    val isLoading: Boolean = true,
    /** Error message to display, if any. */
    val errorMessage: String? = null,
)

/**
 * Known preference keys — must match the keys used by [PreferenceRepository].
 */
object PreferenceKeys {
    const val EXPLANATION_STYLE = "explanation_style"
    const val READING_LEVEL = "reading_level"
    const val EXAMPLE_TYPE = "example_type"
}

/**
 * ViewModel for the preferences screen — edits per-profile learning preferences.
 *
 * Changes are saved immediately on selection (no "save" button) per PRD §8.6.
 * The explanation engine reads these preferences to adapt its output.
 *
 * Preferences accessible ≤2 taps from any screen (PE-05):
 * 1. Tap Profiles in bottom bar → tap settings icon
 * 2. Or navigate directly from profile card
 */
class PreferencesViewModel(
    private val preferenceRepository: PreferenceRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    init {
        loadActiveProfileAndPreferences()
    }

    /** Updates the explanation style and persists immediately. */
    fun setExplanationStyle(style: String) {
        _uiState.update { it.copy(explanationStyle = style) }
        savePreference(PreferenceKeys.EXPLANATION_STYLE, style)
    }

    /** Updates the reading level and persists immediately. */
    fun setReadingLevel(level: String) {
        _uiState.update { it.copy(readingLevel = level) }
        savePreference(PreferenceKeys.READING_LEVEL, level)
    }

    /** Updates the example type and persists immediately. */
    fun setExampleType(type: String) {
        _uiState.update { it.copy(exampleType = type) }
        savePreference(PreferenceKeys.EXAMPLE_TYPE, type)
    }

    /** Dismisses the error message. */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun loadActiveProfileAndPreferences() {
        viewModelScope.launch {
            when (val result = profileRepository.getActiveProfile()) {
                is EzansiResult.Success -> {
                    val profile = result.data
                    if (profile != null) {
                        _uiState.update {
                            it.copy(
                                profileId = profile.id,
                                profileName = profile.name,
                            )
                        }
                        loadPreferences(profile.id)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Create a profile first to set your preferences.",
                            )
                        }
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

    private fun loadPreferences(profileId: String) {
        viewModelScope.launch {
            when (val result = preferenceRepository.getPreferences(profileId)) {
                is EzansiResult.Success -> {
                    val prefs = result.data
                    _uiState.update { state ->
                        state.copy(
                            explanationStyle = prefs.findValue(
                                PreferenceKeys.EXPLANATION_STYLE,
                                "step_by_step",
                            ),
                            readingLevel = prefs.findValue(
                                PreferenceKeys.READING_LEVEL,
                                "simple",
                            ),
                            exampleType = prefs.findValue(
                                PreferenceKeys.EXAMPLE_TYPE,
                                "everyday",
                            ),
                            isLoading = false,
                        )
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

    private fun savePreference(key: String, value: String) {
        val profileId = _uiState.value.profileId ?: return
        viewModelScope.launch {
            when (val result = preferenceRepository.updatePreference(profileId, key, value)) {
                is EzansiResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                else -> { /* Success or Loading — no UI change needed */ }
            }
        }
    }

    /** Finds a preference value by key, returning [default] if not found. */
    private fun List<LearnerPreference>.findValue(key: String, default: String): String {
        return firstOrNull { it.key == key }?.value ?: default
    }
}

/**
 * Factory for creating [PreferencesViewModel] with manual DI.
 */
class PreferencesViewModelFactory(
    private val preferenceRepository: PreferenceRepository,
    private val profileRepository: ProfileRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
            return PreferencesViewModel(
                preferenceRepository = preferenceRepository,
                profileRepository = profileRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
