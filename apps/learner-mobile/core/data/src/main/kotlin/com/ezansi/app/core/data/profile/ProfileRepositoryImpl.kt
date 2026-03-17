package com.ezansi.app.core.data.profile

import com.ezansi.app.core.common.DispatcherProvider
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.LearnerProfile
import com.ezansi.app.core.data.ProfileRepository
import com.ezansi.app.core.data.chat.ChatHistoryStore
import com.ezansi.app.core.data.preference.PreferenceStore
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Production implementation of [ProfileRepository].
 *
 * Manages learner profiles with:
 * - AES-256-GCM encryption at rest (via [ProfileStore])
 * - Atomic file writes for crash safety (PRD §8.10 LC-01, LC-03)
 * - Multi-profile isolation (each profile in a separate encrypted file)
 * - Cascading delete (profile + preferences + chat history)
 *
 * All file operations run on [DispatcherProvider.io] to avoid
 * blocking the UI thread.
 *
 * **No learner data is logged** — only structural operation messages.
 */
class ProfileRepositoryImpl(
    private val profileStore: ProfileStore,
    private val preferenceStore: PreferenceStore,
    private val chatHistoryStore: ChatHistoryStore,
    private val dispatcherProvider: DispatcherProvider,
) : ProfileRepository {

    override suspend fun getProfiles(): EzansiResult<List<LearnerProfile>> =
        withContext(dispatcherProvider.io) {
            try {
                val profiles = profileStore.loadAllProfiles()
                    .sortedByDescending { it.lastActiveAt }
                EzansiResult.Success(profiles)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to load profiles", e)
            }
        }

    override suspend fun createProfile(name: String): EzansiResult<LearnerProfile> =
        withContext(dispatcherProvider.io) {
            try {
                val now = System.currentTimeMillis()
                val profile = LearnerProfile(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    createdAt = now,
                    lastActiveAt = now,
                )
                profileStore.saveProfile(profile)
                EzansiResult.Success(profile)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to create profile", e)
            }
        }

    override suspend fun getActiveProfile(): EzansiResult<LearnerProfile?> =
        withContext(dispatcherProvider.io) {
            try {
                val activeId = profileStore.getActiveProfileId()
                    ?: return@withContext EzansiResult.Success(null)
                val profile = profileStore.loadProfile(activeId)
                EzansiResult.Success(profile)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to get active profile", e)
            }
        }

    override suspend fun setActiveProfile(id: String): EzansiResult<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                val profile = profileStore.loadProfile(id)
                    ?: return@withContext EzansiResult.Error("Profile not found")

                // Update lastActiveAt timestamp
                val updated = profile.copy(lastActiveAt = System.currentTimeMillis())
                profileStore.saveProfile(updated)
                profileStore.setActiveProfileId(id)
                EzansiResult.Success(Unit)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to set active profile", e)
            }
        }

    override suspend fun deleteProfile(id: String): EzansiResult<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                // Cascade: delete all associated data first
                chatHistoryStore.clearHistory(id)
                preferenceStore.deletePreferences(id)
                profileStore.deleteProfile(id)

                // Clear active profile if this was the active one
                if (profileStore.getActiveProfileId() == id) {
                    profileStore.clearActiveProfile()
                }
                EzansiResult.Success(Unit)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to delete profile", e)
            }
        }
}
