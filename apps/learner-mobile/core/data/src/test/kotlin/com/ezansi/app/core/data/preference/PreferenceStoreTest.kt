package com.ezansi.app.core.data.preference

import com.ezansi.app.core.data.FakeAndroidKeyStoreSpi
import com.ezansi.app.core.data.encryption.ProfileEncryption
import com.ezansi.app.core.data.installFakeAndroidKeyStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import org.robolectric.junit5.RobolectricExtension
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [PreferenceStore] — encrypted per-profile preferences.
 *
 * Validates:
 * - Default preferences are created on first access
 * - Values are persisted via encrypted files
 * - Single preference updates are merged correctly
 * - Unknown keys are silently ignored (forward compatibility)
 * - Delete removes the preference file
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
@DisplayName("PreferenceStore")
class PreferenceStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var encryption: ProfileEncryption
    private lateinit var store: PreferenceStore

    @BeforeEach
    fun setUp() {
        installFakeAndroidKeyStore()
        FakeAndroidKeyStoreSpi.reset()
        encryption = ProfileEncryption()
        store = PreferenceStore(tempDir, encryption)
    }

    // ── Defaults ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Default preferences")
    inner class DefaultTests {

        @Test
        @DisplayName("first load returns default preferences")
        fun firstLoadReturnsDefaults() {
            val prefs = store.loadPreferences("new-profile")

            assertEquals(3, prefs.size)

            val byKey = prefs.associateBy { it.key }
            assertEquals("step-by-step", byKey["explanation_style"]?.value)
            assertEquals("standard", byKey["reading_level"]?.value)
            assertEquals("everyday", byKey["example_type"]?.value)
        }

        @Test
        @DisplayName("first load creates encrypted file on disk")
        fun firstLoadCreatesFile() {
            store.loadPreferences("profile-1")

            val prefsFile = File(tempDir, "profile-1_prefs.json.enc")
            assertTrue(prefsFile.exists(), "Preferences file should be created on first load")
        }

        @Test
        @DisplayName("default preferences have display names and descriptions")
        fun defaultsHaveMetadata() {
            val prefs = store.loadPreferences("p1")

            prefs.forEach { pref ->
                assertTrue(
                    pref.displayName.isNotBlank(),
                    "Preference '${pref.key}' should have a display name",
                )
                assertTrue(
                    pref.description.isNotBlank(),
                    "Preference '${pref.key}' should have a description",
                )
            }
        }
    }

    // ── Save and load round-trip ────────────────────────────────────

    @Nested
    @DisplayName("Save and load")
    inner class SaveLoadTests {

        @Test
        @DisplayName("saved preferences persist across loads")
        fun roundTrip() {
            val customPrefs = PreferenceStore.DEFAULT_PREFERENCES.map { pref ->
                when (pref.key) {
                    "explanation_style" -> pref.copy(value = "visual")
                    "reading_level" -> pref.copy(value = "simple")
                    else -> pref
                }
            }

            store.savePreferences("profile-1", customPrefs)
            val loaded = store.loadPreferences("profile-1")

            val byKey = loaded.associateBy { it.key }
            assertEquals("visual", byKey["explanation_style"]?.value)
            assertEquals("simple", byKey["reading_level"]?.value)
            assertEquals("everyday", byKey["example_type"]?.value) // Unchanged
        }
    }

    // ── Update single preference ────────────────────────────────────

    @Nested
    @DisplayName("Update single preference")
    inner class UpdateTests {

        @Test
        @DisplayName("update one preference preserves others")
        fun updatePreservesOthers() {
            // Force defaults to be created
            store.loadPreferences("profile-1")

            store.updatePreference("profile-1", "reading_level", "advanced")

            val loaded = store.loadPreferences("profile-1")
            val byKey = loaded.associateBy { it.key }
            assertEquals("advanced", byKey["reading_level"]?.value)
            assertEquals("step-by-step", byKey["explanation_style"]?.value) // Unchanged
            assertEquals("everyday", byKey["example_type"]?.value) // Unchanged
        }

        @Test
        @DisplayName("update unknown key is silently ignored")
        fun unknownKeyIgnored() {
            store.loadPreferences("profile-1")

            store.updatePreference("profile-1", "nonexistent_key", "some_value")

            val loaded = store.loadPreferences("profile-1")
            assertEquals(3, loaded.size) // No new preference added
            assertTrue(loaded.none { it.key == "nonexistent_key" })
        }
    }

    // ── Delete ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Delete preferences")
    inner class DeleteTests {

        @Test
        @DisplayName("delete removes preference file")
        fun deleteRemovesFile() {
            store.loadPreferences("profile-1") // Creates file
            val deleted = store.deletePreferences("profile-1")

            assertTrue(deleted)
            val file = File(tempDir, "profile-1_prefs.json.enc")
            assertFalse(file.exists())
        }

        @Test
        @DisplayName("delete non-existent returns false")
        fun deleteNonExistent() {
            val deleted = store.deletePreferences("does-not-exist")
            assertFalse(deleted)
        }

        @Test
        @DisplayName("load after delete returns defaults again")
        fun loadAfterDeleteReturnsDefaults() {
            store.updatePreference("profile-1", "reading_level", "advanced")
            store.deletePreferences("profile-1")

            val loaded = store.loadPreferences("profile-1")
            val byKey = loaded.associateBy { it.key }
            assertEquals("standard", byKey["reading_level"]?.value) // Reset to default
        }
    }

    // ── Profile isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("Profile isolation")
    inner class IsolationTests {

        @Test
        @DisplayName("different profiles have independent preferences")
        fun independentPreferences() {
            store.updatePreference("alice", "reading_level", "advanced")
            store.updatePreference("bob", "reading_level", "simple")

            val alicePrefs = store.loadPreferences("alice").associateBy { it.key }
            val bobPrefs = store.loadPreferences("bob").associateBy { it.key }

            assertEquals("advanced", alicePrefs["reading_level"]?.value)
            assertEquals("simple", bobPrefs["reading_level"]?.value)
        }
    }
}
