package com.ezansi.app.core.data.profile

import android.util.Log
import com.ezansi.app.core.data.LearnerProfile
import com.ezansi.app.core.data.encryption.ProfileEncryption
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * File-based storage for encrypted learner profiles.
 *
 * Each profile is stored as `{profileId}.json.enc` — a JSON document
 * encrypted with AES-256-GCM. The active profile ID is stored in
 * `active_profile.txt` (unencrypted — it contains only a UUID, not PII).
 *
 * All writes use the atomic-rename pattern (write temp → fsync → rename)
 * to survive abrupt process kills or power loss (PRD §8.10 LC-01, LC-03).
 *
 * **No learner data is logged** — only structural messages (file counts,
 * operation success/failure).
 */
class ProfileStore(
    private val profilesDir: File,
    private val encryption: ProfileEncryption,
) {

    companion object {
        private const val ACTIVE_PROFILE_FILE = "active_profile.txt"
        private const val PROFILE_EXTENSION = ".json.enc"
        private const val TAG = "ProfileStore"
    }

    /**
     * Persists a [profile] to an encrypted file.
     * Overwrites any existing file for the same profile ID.
     */
    fun saveProfile(profile: LearnerProfile) {
        val json = JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("createdAt", profile.createdAt)
            put("lastActiveAt", profile.lastActiveAt)
        }
        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        val encrypted = encryption.encrypt(plaintext)
        val target = File(profilesDir, "${profile.id}$PROFILE_EXTENSION")
        writeAtomically(target, encrypted)
    }

    /**
     * Loads a single profile by ID.
     *
     * @return The profile, or null if the file is missing or corrupt.
     *         Corrupt files are logged but never cause a crash.
     */
    fun loadProfile(profileId: String): LearnerProfile? {
        val file = File(profilesDir, "$profileId$PROFILE_EXTENSION")
        if (!file.exists()) return null
        return try {
            val encrypted = file.readBytes()
            val decrypted = encryption.decrypt(encrypted)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            LearnerProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                createdAt = json.getLong("createdAt"),
                lastActiveAt = json.getLong("lastActiveAt"),
            )
        } catch (e: Exception) {
            // Log structural info only — never the profile content
            Log.e(TAG, "Failed to load profile file: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Loads all profiles from the profiles directory.
     * Skips any corrupt files without crashing.
     */
    fun loadAllProfiles(): List<LearnerProfile> {
        if (!profilesDir.exists()) return emptyList()
        val profileFiles = profilesDir.listFiles { file ->
            file.name.endsWith(PROFILE_EXTENSION) && !file.name.startsWith(".")
        } ?: return emptyList()

        Log.d(TAG, "Found ${profileFiles.size} profile file(s)")
        return profileFiles.mapNotNull { file ->
            val profileId = file.name.removeSuffix(PROFILE_EXTENSION)
            loadProfile(profileId)
        }
    }

    /**
     * Deletes the encrypted profile file for the given ID.
     *
     * @return true if the file was deleted, false if it didn't exist.
     */
    fun deleteProfile(profileId: String): Boolean {
        val file = File(profilesDir, "$profileId$PROFILE_EXTENSION")
        val deleted = file.delete()
        if (deleted) {
            Log.d(TAG, "Profile file deleted")
        }
        return deleted
    }

    /** Returns the currently active profile ID, or null if unset. */
    fun getActiveProfileId(): String? {
        val file = File(profilesDir, ACTIVE_PROFILE_FILE)
        if (!file.exists()) return null
        return try {
            file.readText(Charsets.UTF_8).trim().ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read active profile ID: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Sets the active profile ID via atomic write. */
    fun setActiveProfileId(profileId: String) {
        val target = File(profilesDir, ACTIVE_PROFILE_FILE)
        writeAtomically(target, profileId.toByteArray(Charsets.UTF_8))
    }

    /** Clears the active profile selection. */
    fun clearActiveProfile() {
        val file = File(profilesDir, ACTIVE_PROFILE_FILE)
        file.delete()
    }

    /**
     * Writes [data] to [target] using the atomic-rename pattern:
     * 1. Write to a temp file in the same directory.
     * 2. Flush and fsync to ensure data hits disk.
     * 3. Rename temp → target (atomic on same filesystem).
     *
     * If any step fails, the temp file is cleaned up and the
     * original target is left untouched.
     */
    private fun writeAtomically(target: File, data: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync()
            }
            if (!temp.renameTo(target)) {
                // Fallback for edge cases where rename fails
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
