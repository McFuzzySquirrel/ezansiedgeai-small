package com.ezansi.app.core.data.preference

import com.ezansi.app.core.common.DispatcherProvider
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.LearnerPreference
import com.ezansi.app.core.data.PreferenceRepository
import kotlinx.coroutines.withContext

/**
 * Production implementation of [PreferenceRepository].
 *
 * Persists per-profile preferences as encrypted JSON files. All file
 * operations run on [DispatcherProvider.io] to avoid blocking the UI thread.
 *
 * Preferences are persisted immediately on change — no "save" button needed.
 * Unknown keys are silently ignored for forward compatibility.
 */
class PreferenceRepositoryImpl(
    private val preferenceStore: PreferenceStore,
    private val dispatcherProvider: DispatcherProvider,
) : PreferenceRepository {

    override suspend fun getPreferences(
        profileId: String,
    ): EzansiResult<List<LearnerPreference>> =
        withContext(dispatcherProvider.io) {
            try {
                val preferences = preferenceStore.loadPreferences(profileId)
                EzansiResult.Success(preferences)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to load preferences", e)
            }
        }

    override suspend fun updatePreference(
        profileId: String,
        key: String,
        value: String,
    ): EzansiResult<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                preferenceStore.updatePreference(profileId, key, value)
                EzansiResult.Success(Unit)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to update preference", e)
            }
        }
}
