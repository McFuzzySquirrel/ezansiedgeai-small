package com.ezansi.app.core.common

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Resolves storage paths for models, content packs, and profile data.
 *
 * Storage strategy (PRD §7.3 Hardware Layer):
 * 1. Primary: internal app storage (`Context.getFilesDir()`)
 * 2. Fallback: adoptable external storage (SD card formatted as internal)
 *
 * The app uses `android:installLocation="auto"` in the manifest so the
 * OS can move the APK to adoptable storage. This class handles the data
 * directories that hold models (~800 MB) and content packs (~200 MB each).
 *
 * Why not scoped storage / MediaStore:
 * - Models and packs are app-private data, not user documents.
 * - No MANAGE_EXTERNAL_STORAGE permission needed.
 * - `getExternalFilesDirs()` returns adoptable storage without permissions on API 29+.
 */
class StorageManager(private val context: Context) {

    companion object {
        private const val DIR_MODELS = "models"
        private const val DIR_PACKS = "content-packs"
        private const val DIR_PROFILES = "profiles"
    }

    /**
     * Directory for LLM and embedding model files.
     * Uses adoptable storage if available and has more free space than internal.
     */
    fun getModelsDir(): File = resolveDir(DIR_MODELS)

    /**
     * Directory for installed content packs (.pack SQLite bundles).
     * Uses adoptable storage if available and has more free space than internal.
     */
    fun getPacksDir(): File = resolveDir(DIR_PACKS)

    /**
     * Directory for learner profile data (encrypted SQLite databases).
     * Always uses internal storage — profile data must not be on removable media.
     */
    fun getProfilesDir(): File {
        val dir = File(context.filesDir, DIR_PROFILES)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns available storage bytes on the best storage volume.
     * Checks both internal and adoptable external storage.
     */
    fun getAvailableStorageBytes(): Long {
        val internal = getAvailableBytes(context.filesDir)
        val external = getBestExternalDir()?.let { getAvailableBytes(it) } ?: 0L
        return maxOf(internal, external)
    }

    /**
     * Returns available storage bytes on the volume containing [dir].
     */
    fun getAvailableBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: IllegalArgumentException) {
            // StatFs throws if the path doesn't exist or is inaccessible
            0L
        }
    }

    /**
     * Resolves the best directory for large data files (models, packs).
     * Prefers adoptable external storage when it has more free space.
     */
    private fun resolveDir(subdirectory: String): File {
        val internalDir = File(context.filesDir, subdirectory)
        val externalDir = getBestExternalDir()?.let { File(it, subdirectory) }

        // Use external if it exists and has meaningfully more space (>100 MB more)
        val dir = if (externalDir != null &&
            getAvailableBytes(externalDir.parentFile ?: externalDir) >
            getAvailableBytes(internalDir.parentFile ?: internalDir) + 100 * 1024 * 1024
        ) {
            externalDir
        } else {
            internalDir
        }

        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns the best external files directory, or null if none is available.
     * On devices with adoptable storage, this returns the SD card path.
     * Filters out emulated storage (which is the same volume as internal).
     */
    private fun getBestExternalDir(): File? {
        val externalDirs = context.getExternalFilesDirs(null)
        return externalDirs
            .filterNotNull()
            .filter { isExternalStorageMounted(it) }
            .filter { !isEmulatedStorage(it) }
            .maxByOrNull { getAvailableBytes(it) }
    }

    private fun isExternalStorageMounted(dir: File): Boolean {
        return try {
            Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
        } catch (_: Exception) {
            false
        }
    }

    private fun isEmulatedStorage(dir: File): Boolean {
        return try {
            Environment.isExternalStorageEmulated(dir)
        } catch (_: Exception) {
            true // Assume emulated if we can't determine
        }
    }
}
