package com.ezansi.app.core.data.preference

import android.util.Log
import com.ezansi.app.core.data.LearnerPreference
import com.ezansi.app.core.data.encryption.ProfileEncryption
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * File-based storage for encrypted per-profile preferences.
 *
 * Each profile's preferences are stored as `{profileId}_prefs.json.enc` —
 * a JSON document encrypted with AES-256-GCM. Default preferences are
 * created automatically on first access (PRD §8.6 PE-01 through PE-03).
 *
 * All writes use the atomic-rename pattern for crash safety (PRD §8.10 LC-03).
 *
 * **No learner data is logged** — only structural messages.
 */
class PreferenceStore(
    private val profilesDir: File,
    private val encryption: ProfileEncryption,
) {

    companion object {
        private const val PREFS_SUFFIX = "_prefs.json.enc"
        private const val TAG = "PreferenceStore"

        /**
         * Default preferences for new profiles.
         *
         * - explanation_style: "step-by-step" | "visual" | "simple" | "detailed"
         * - reading_level: "simple" | "standard" | "advanced"
         * - example_type: "everyday" | "abstract" | "visual"
         */
        val DEFAULT_PREFERENCES = listOf(
            LearnerPreference(
                key = "explanation_style",
                value = "step-by-step",
                displayName = "Explanation Style",
                description = "How explanations are structured: step-by-step, visual, simple, or detailed",
            ),
            LearnerPreference(
                key = "reading_level",
                value = "standard",
                displayName = "Reading Level",
                description = "Controls how complex the language in explanations is: simple, standard, or advanced",
            ),
            LearnerPreference(
                key = "example_type",
                value = "everyday",
                displayName = "Example Type",
                description = "The type of examples used in explanations: everyday, abstract, or visual",
            ),
        )
    }

    /**
     * Loads preferences for the given profile.
     *
     * If no preferences file exists yet, default preferences are created
     * and persisted. Stored values are merged with the default schema so
     * new preferences added in future versions get their defaults.
     *
     * On read failure (corrupt file), defaults are returned and the error
     * is logged — never crashes.
     */
    fun loadPreferences(profileId: String): List<LearnerPreference> {
        val file = File(profilesDir, "$profileId$PREFS_SUFFIX")
        if (!file.exists()) {
            savePreferences(profileId, DEFAULT_PREFERENCES)
            return DEFAULT_PREFERENCES
        }
        return try {
            val encrypted = file.readBytes()
            val decrypted = encryption.decrypt(encrypted)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            // Merge stored values with defaults — defaults define the schema
            DEFAULT_PREFERENCES.map { default ->
                if (json.has(default.key)) {
                    default.copy(value = json.getString(default.key))
                } else {
                    default
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preferences, returning defaults: ${e.javaClass.simpleName}")
            DEFAULT_PREFERENCES
        }
    }

    /**
     * Saves a full set of preferences for the given profile.
     */
    fun savePreferences(profileId: String, preferences: List<LearnerPreference>) {
        val json = JSONObject()
        preferences.forEach { pref ->
            json.put(pref.key, pref.value)
        }
        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        val encrypted = encryption.encrypt(plaintext)
        val target = File(profilesDir, "$profileId$PREFS_SUFFIX")
        writeAtomically(target, encrypted)
    }

    /**
     * Updates a single preference for the given profile.
     *
     * Unknown keys are silently ignored for forward compatibility — if a
     * future version adds new preference types, older code won't crash
     * when encountering them (PRD §8.6).
     */
    fun updatePreference(profileId: String, key: String, value: String) {
        val current = loadPreferences(profileId).toMutableList()
        val index = current.indexOfFirst { it.key == key }
        if (index >= 0) {
            current[index] = current[index].copy(value = value)
            savePreferences(profileId, current)
        }
        // Unknown keys are ignored — forward compatibility
    }

    /**
     * Deletes all preferences for the given profile.
     *
     * @return true if the file was deleted, false if it didn't exist.
     */
    fun deletePreferences(profileId: String): Boolean {
        val file = File(profilesDir, "$profileId$PREFS_SUFFIX")
        return file.delete()
    }

    /**
     * Atomic write: temp file → fsync → rename.
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
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
