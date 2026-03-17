package com.ezansi.app.core.data.profile

import com.ezansi.app.core.data.FakeAndroidKeyStoreSpi
import com.ezansi.app.core.data.LearnerProfile
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ProfileStore] — encrypted file-based profile storage.
 *
 * Uses Robolectric for Android framework classes + a fake AndroidKeyStore
 * provider so [ProfileEncryption] can create AES-256-GCM keys in-memory.
 *
 * Tests verify:
 * - Round-trip: save → load returns identical profile
 * - Atomic writes don't leave corrupt files on failure
 * - Active profile selection persists correctly
 * - Delete removes the encrypted file
 * - Missing/corrupt files are handled gracefully
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
@DisplayName("ProfileStore")
class ProfileStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var encryption: ProfileEncryption
    private lateinit var store: ProfileStore

    @BeforeEach
    fun setUp() {
        installFakeAndroidKeyStore()
        FakeAndroidKeyStoreSpi.reset()
        encryption = ProfileEncryption()
        store = ProfileStore(tempDir, encryption)
    }

    private fun testProfile(
        id: String = "profile-001",
        name: String = "Thandi",
        createdAt: Long = 1_700_000_000_000L,
        lastActiveAt: Long = 1_700_000_100_000L,
    ) = LearnerProfile(id, name, createdAt, lastActiveAt)

    // ── Save and load ───────────────────────────────────────────────

    @Nested
    @DisplayName("Save and load")
    inner class SaveLoadTests {

        @Test
        @DisplayName("save then load returns identical profile")
        fun roundTrip() {
            val original = testProfile()
            store.saveProfile(original)
            val loaded = store.loadProfile("profile-001")

            assertNotNull(loaded)
            assertEquals(original.id, loaded.id)
            assertEquals(original.name, loaded.name)
            assertEquals(original.createdAt, loaded.createdAt)
            assertEquals(original.lastActiveAt, loaded.lastActiveAt)
        }

        @Test
        @DisplayName("encrypted file is not plaintext")
        fun fileIsEncrypted() {
            store.saveProfile(testProfile())

            val encFile = File(tempDir, "profile-001.json.enc")
            assertTrue(encFile.exists())

            // Encrypted content should NOT contain the profile name as plaintext
            val rawContent = encFile.readText(Charsets.UTF_8)
            assertFalse(
                rawContent.contains("Thandi"),
                "Encrypted file should not contain plaintext name",
            )
        }

        @Test
        @DisplayName("overwrite existing profile with updated data")
        fun overwriteExisting() {
            store.saveProfile(testProfile())
            store.saveProfile(testProfile(name = "Sipho", lastActiveAt = 2_000_000_000_000L))

            val loaded = store.loadProfile("profile-001")
            assertNotNull(loaded)
            assertEquals("Sipho", loaded.name)
            assertEquals(2_000_000_000_000L, loaded.lastActiveAt)
        }

        @Test
        @DisplayName("load non-existent profile returns null")
        fun loadMissing() {
            val result = store.loadProfile("does-not-exist")
            assertNull(result)
        }
    }

    // ── Load all ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Load all profiles")
    inner class LoadAllTests {

        @Test
        @DisplayName("loadAllProfiles returns all saved profiles")
        fun loadAll() {
            store.saveProfile(testProfile(id = "p1", name = "Thandi"))
            store.saveProfile(testProfile(id = "p2", name = "Sipho"))
            store.saveProfile(testProfile(id = "p3", name = "Lerato"))

            val profiles = store.loadAllProfiles()
            assertEquals(3, profiles.size)

            val names = profiles.map { it.name }.toSet()
            assertTrue("Thandi" in names)
            assertTrue("Sipho" in names)
            assertTrue("Lerato" in names)
        }

        @Test
        @DisplayName("loadAllProfiles returns empty list when no profiles exist")
        fun loadAllEmpty() {
            val profiles = store.loadAllProfiles()
            assertTrue(profiles.isEmpty())
        }

        @Test
        @DisplayName("skips corrupt files without crashing")
        fun skipsCorruptFiles() {
            store.saveProfile(testProfile(id = "good", name = "Good"))

            // Write a corrupt file
            val corruptFile = File(tempDir, "bad.json.enc")
            corruptFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))

            val profiles = store.loadAllProfiles()
            // Should get the good profile, skip the corrupt one
            assertEquals(1, profiles.size)
            assertEquals("Good", profiles[0].name)
        }
    }

    // ── Delete ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Delete profile")
    inner class DeleteTests {

        @Test
        @DisplayName("delete existing profile returns true")
        fun deleteExisting() {
            store.saveProfile(testProfile())
            val deleted = store.deleteProfile("profile-001")

            assertTrue(deleted)
            assertNull(store.loadProfile("profile-001"))
        }

        @Test
        @DisplayName("delete non-existent profile returns false")
        fun deleteMissing() {
            val deleted = store.deleteProfile("does-not-exist")
            assertFalse(deleted)
        }
    }

    // ── Active profile ──────────────────────────────────────────────

    @Nested
    @DisplayName("Active profile selection")
    inner class ActiveProfileTests {

        @Test
        @DisplayName("set and get active profile ID round-trip")
        fun setGetRoundTrip() {
            store.setActiveProfileId("profile-001")
            assertEquals("profile-001", store.getActiveProfileId())
        }

        @Test
        @DisplayName("no active profile initially")
        fun noActiveInitially() {
            assertNull(store.getActiveProfileId())
        }

        @Test
        @DisplayName("clear active profile removes selection")
        fun clearActive() {
            store.setActiveProfileId("profile-001")
            store.clearActiveProfile()
            assertNull(store.getActiveProfileId())
        }

        @Test
        @DisplayName("changing active profile overwrites previous")
        fun changeActive() {
            store.setActiveProfileId("p1")
            store.setActiveProfileId("p2")
            assertEquals("p2", store.getActiveProfileId())
        }
    }

    // ── Atomic writes ───────────────────────────────────────────────

    @Nested
    @DisplayName("Atomic write safety")
    inner class AtomicWriteTests {

        @Test
        @DisplayName("no .tmp files remain after successful write")
        fun noTmpFilesRemain() {
            store.saveProfile(testProfile())

            val tmpFiles = tempDir.listFiles { file -> file.name.endsWith(".tmp") }
            assertTrue(
                tmpFiles.isNullOrEmpty(),
                "No .tmp files should remain after successful write",
            )
        }
    }
}
