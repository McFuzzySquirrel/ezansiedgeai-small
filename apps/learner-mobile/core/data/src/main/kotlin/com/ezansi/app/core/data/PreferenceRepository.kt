package com.ezansi.app.core.data

import com.ezansi.app.core.common.EzansiResult

/**
 * Repository for per-profile learner preferences.
 *
 * Preferences control how the explanation engine adapts its output:
 * reading level, preferred language, explanation style, etc. Each
 * profile has its own preference set so siblings sharing a device
 * get personalised experiences.
 *
 * Implementors (learner-data-engineer) must ensure:
 * - Preferences are persisted immediately on change (no "save" button)
 * - Unknown keys are ignored (forward compatibility with new preference types)
 * - Default values are returned for unset preferences
 *
 * @see LearnerPreference for the preference data structure
 */
interface PreferenceRepository {

    /**
     * Returns all preferences for the given profile.
     * Unset preferences return their default values.
     *
     * @param profileId The profile whose preferences to retrieve.
     */
    suspend fun getPreferences(profileId: String): EzansiResult<List<LearnerPreference>>

    /**
     * Updates a single preference value for the given profile.
     * Creates the preference if it doesn't exist yet.
     *
     * @param profileId The profile to update.
     * @param key The preference key (e.g. "reading_level", "explanation_style").
     * @param value The new value as a string.
     */
    suspend fun updatePreference(
        profileId: String,
        key: String,
        value: String,
    ): EzansiResult<Unit>
}

/**
 * A single learner preference entry.
 */
data class LearnerPreference(
    /** Preference key (e.g. "reading_level"). */
    val key: String,
    /** Current value (e.g. "basic"). */
    val value: String,
    /** Human-readable label for the UI (e.g. "Reading Level"). */
    val displayName: String,
    /** Human-readable description (e.g. "Controls how complex the explanations are"). */
    val description: String,
)
