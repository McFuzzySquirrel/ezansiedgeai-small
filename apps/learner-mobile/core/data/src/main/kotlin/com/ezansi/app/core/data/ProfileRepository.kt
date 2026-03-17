package com.ezansi.app.core.data

import com.ezansi.app.core.common.EzansiResult

/**
 * Repository for managing learner profiles.
 *
 * A learner profile represents one user of the device. Multiple profiles
 * are supported because devices are often shared among siblings or
 * classmates in the South African context.
 *
 * Implementors (learner-data-engineer) must ensure:
 * - Profile data is encrypted at rest with AES-256-GCM
 * - All writes are transactional (survive abrupt kills)
 * - No learner data is logged, even in debug builds
 *
 * @see LearnerProfile for the profile data structure
 */
interface ProfileRepository {

    /**
     * Returns all learner profiles on this device.
     */
    suspend fun getProfiles(): EzansiResult<List<LearnerProfile>>

    /**
     * Creates a new learner profile with the given display name.
     *
     * @param name The learner's chosen display name.
     * @return The newly created profile with a generated ID.
     */
    suspend fun createProfile(name: String): EzansiResult<LearnerProfile>

    /**
     * Returns the currently active profile, or null if none is set.
     * On first launch, there are no profiles — the app should prompt creation.
     */
    suspend fun getActiveProfile(): EzansiResult<LearnerProfile?>

    /**
     * Sets the active profile by ID. Used when switching between profiles.
     *
     * @param id The profile ID to activate.
     */
    suspend fun setActiveProfile(id: String): EzansiResult<Unit>

    /**
     * Permanently deletes a profile and all associated data.
     * This is irreversible — the UI must confirm before calling.
     *
     * @param id The profile ID to delete.
     */
    suspend fun deleteProfile(id: String): EzansiResult<Unit>
}

/**
 * A learner profile on this device.
 */
data class LearnerProfile(
    /** Unique identifier (UUID). */
    val id: String,
    /** Learner's chosen display name. */
    val name: String,
    /** Timestamp when the profile was created (epoch millis). */
    val createdAt: Long,
    /** Timestamp of last activity (epoch millis). */
    val lastActiveAt: Long,
)
