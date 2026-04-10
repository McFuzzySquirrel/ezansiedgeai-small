package com.ezansi.app.core.data.contentpack

import android.util.Log
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.common.StorageManager
import com.ezansi.app.core.data.PackMetadata
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Manages the lifecycle of content packs on-device: install, uninstall,
 * list, and open.
 *
 * ## Crash-Safe Installation (CP-03)
 *
 * Pack installation uses an atomic copy-then-rename pattern to ensure
 * the packs directory never contains a partially-written file:
 *
 * 1. Copy the source pack to a temporary file in the packs directory
 *    (e.g. `maths-grade6-caps.pack.tmp`)
 * 2. Verify the temporary copy with [PackVerifier]
 * 3. Atomically rename `.tmp` → `.pack`
 * 4. If any step fails, delete the `.tmp` file
 *
 * If the app is killed at any point during installation:
 * - Before rename: only a `.tmp` file exists, which is ignored on next scan
 * - After rename: the pack is fully installed and verified
 *
 * ## Resource Management
 *
 * Open database handles are tracked and must be released via [closePack]
 * or [closeAll]. This class implements [Closeable] for use in try-with-resources
 * or lifecycle-aware cleanup.
 *
 * @param storageManager Provides the packs directory path.
 * @param packVerifier Verifies pack integrity before installation.
 */
class PackManager(
    private val storageManager: StorageManager,
    private val packVerifier: PackVerifier,
) : Closeable {

    companion object {
        private const val TAG = "PackManager"
        private const val PACK_EXTENSION = ".pack"
        private const val TEMP_EXTENSION = ".pack.tmp"
        private const val COPY_BUFFER_SIZE = 8192
    }

    /**
     * Cache of open database handles, keyed by pack_id.
     * Access must be synchronised — multiple coroutines may open/close packs.
     */
    private val openDatabases = mutableMapOf<String, PackDatabase>()

    /**
     * Installs a content pack from a source file path.
     *
     * The pack is copied to the app's packs directory, verified for
     * integrity, and indexed. The installation is atomic — either the
     * pack is fully installed or no trace remains.
     *
     * @param sourcePath Absolute path to the .pack file to install.
     * @return [EzansiResult.Success] with pack metadata on success,
     *         [EzansiResult.Error] with a user-friendly message on failure.
     */
    fun installPack(sourcePath: String): EzansiResult<PackMetadata> {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return EzansiResult.Error("Content pack file not found")
        }
        if (!sourceFile.name.endsWith(PACK_EXTENSION)) {
            return EzansiResult.Error("Not a valid content pack file")
        }

        // Verify source pack integrity before copying
        val verifyResult = packVerifier.verify(sourceFile)
        if (verifyResult is EzansiResult.Error) {
            return verifyResult
        }

        val packsDir = storageManager.getPacksDir()
        val tempFile = File(packsDir, sourceFile.name + ".tmp")
        val targetFile = File(packsDir, sourceFile.name)

        return try {
            // Step 1: Clean up any stale temp file from a previous failed install
            if (tempFile.exists()) {
                tempFile.delete()
            }

            // Step 2: Copy to temporary file
            copyFile(sourceFile, tempFile)

            // Step 3: Verify the copy (guards against copy corruption)
            val copyVerifyResult = packVerifier.verify(tempFile)
            if (copyVerifyResult is EzansiResult.Error) {
                tempFile.delete()
                return EzansiResult.Error("Content pack was corrupted during installation")
            }

            // Step 4: Atomic rename — this is the commit point
            if (targetFile.exists()) {
                // Overwrite existing pack (upgrade scenario)
                closePackByFile(targetFile)
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.delete()
                return EzansiResult.Error(
                    "Could not install content pack — please check available storage",
                )
            }

            // Step 5: Read metadata from the installed pack
            val database = PackDatabase(targetFile, extractPackId(targetFile))
            val metadata = database.getMetadata()
            database.close()

            Log.i(TAG, "Installed pack: ${metadata.packId} (${metadata.chunkCount} chunks)")
            EzansiResult.Success(metadata)
        } catch (e: Exception) {
            // Clean up on any failure — never leave a partial install
            tempFile.delete()
            Log.e(TAG, "Pack installation failed: ${sourceFile.name}", e)
            EzansiResult.Error(
                "Something went wrong installing your content pack",
                cause = e,
            )
        }
    }

    /**
     * Removes an installed content pack and reclaims storage (CP-07).
     *
     * Closes any open database handle before deleting the file.
     *
     * @param packId The unique pack identifier to uninstall.
     * @return [EzansiResult.Success] on successful removal,
     *         [EzansiResult.Error] if the pack is not found.
     */
    fun uninstallPack(packId: String): EzansiResult<Unit> {
        closePack(packId)

        val packFile = findPackFileById(packId)
        if (packFile == null) {
            return EzansiResult.Error("Content pack not found: $packId")
        }

        return try {
            val deleted = packFile.delete()
            if (deleted) {
                Log.i(TAG, "Uninstalled pack: $packId")
                EzansiResult.Success(Unit)
            } else {
                EzansiResult.Error("Could not remove content pack — file may be in use")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall pack: $packId", e)
            EzansiResult.Error(
                "Something went wrong removing the content pack",
                cause = e,
            )
        }
    }

    /**
     * Returns metadata for all installed and verified packs.
     *
     * Scans the packs directory for `.pack` files, opens each briefly
     * to read metadata, then closes. Temp files (`.pack.tmp`) from
     * incomplete installs are cleaned up automatically.
     *
     * @return [EzansiResult.Success] with a list of pack metadata,
     *         or an empty list if no packs are installed (CP-09).
     */
    fun getInstalledPacks(): EzansiResult<List<PackMetadata>> {
        val packsDir = storageManager.getPacksDir()

        // Clean up any stale temp files from failed installs
        cleanUpTempFiles(packsDir)

        val packFiles = packsDir.listFiles { file ->
            file.isFile && file.name.endsWith(PACK_EXTENSION)
        } ?: emptyArray()

        if (packFiles.isEmpty()) {
            return EzansiResult.Success(emptyList())
        }

        val metadataList = mutableListOf<PackMetadata>()
        for (packFile in packFiles) {
            try {
                val database = PackDatabase(packFile, extractPackId(packFile))
                metadataList.add(database.getMetadata())
                database.close()
            } catch (e: Exception) {
                // Skip packs that can't be opened — don't fail the whole list
                Log.w(TAG, "Skipping unreadable pack: ${packFile.name}", e)
            }
        }

        return EzansiResult.Success(metadataList)
    }

    /**
     * Opens a pack database for querying, returning a cached handle if available.
     *
     * The returned [PackDatabase] is tracked for cleanup. Callers should
     * not close it directly — use [closePack] or [closeAll] instead.
     *
     * @param packId The unique pack identifier to open.
     * @return An open [PackDatabase], or null if the pack is not installed.
     */
    @Synchronized
    fun openPack(packId: String): PackDatabase? {
        // Return cached handle if still open
        val cached = openDatabases[packId]
        if (cached != null) {
            return cached
        }

        val packFile = findPackFileById(packId) ?: return null

        return try {
            val database = PackDatabase(packFile, packId)
            openDatabases[packId] = database
            Log.d(TAG, "Opened pack database: $packId")
            database
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open pack: $packId", e)
            null
        }
    }

    /**
     * Checks whether a pack's embeddings are compatible with the on-device model.
     *
     * Reads manifest fields (schema_version, embedding_dim, embedding_model,
     * embedding_model_version) and applies the rules from EMBEDDING_CONTRACT.md §6.2.
     * Callers should gate retrieval operations on [PackCompatibility.Compatible].
     *
     * @param packId The unique pack identifier to check.
     * @return The compatibility result, or [PackCompatibility.IncompatibleSchema]
     *         if the pack cannot be found or opened.
     */
    fun checkPackCompatibility(packId: String): PackCompatibility {
        val database = openPack(packId)
            ?: return PackCompatibility.IncompatibleSchema(
                "Content pack not found: $packId",
            )

        return try {
            val manifest = database.getManifest()
            val result = PackVersionDetector.checkCompatibility(manifest)
            if (result !is PackCompatibility.Compatible) {
                Log.w(
                    TAG,
                    "Pack '$packId' is incompatible: " +
                        when (result) {
                            is PackCompatibility.IncompatibleSchema -> result.message
                            is PackCompatibility.IncompatibleDimension -> result.message
                            is PackCompatibility.IncompatibleModel -> result.message
                            is PackCompatibility.IncompatibleVersion -> result.message
                            else -> "unknown"
                        },
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check compatibility for pack: $packId", e)
            PackCompatibility.IncompatibleSchema(
                "Could not read pack manifest: $packId",
            )
        }
    }

    /**
     * Closes the database handle for a specific pack.
     */
    @Synchronized
    fun closePack(packId: String) {
        openDatabases.remove(packId)?.close()
    }

    /**
     * Closes all open database handles — call during Application.onTrimMemory
     * or when the content pack feature is no longer active.
     */
    @Synchronized
    override fun close() {
        closeAll()
    }

    /**
     * Closes all open database handles.
     */
    @Synchronized
    fun closeAll() {
        openDatabases.values.forEach { it.close() }
        openDatabases.clear()
        Log.d(TAG, "Closed all pack databases")
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Finds the .pack file for a given pack_id.
     *
     * Searches by two strategies:
     * 1. Direct filename match (pack_id + ".pack")
     * 2. Opens each .pack file and checks manifest pack_id
     *
     * Strategy 1 handles the common case; strategy 2 handles packs
     * whose filename doesn't match their manifest pack_id.
     */
    private fun findPackFileById(packId: String): File? {
        val packsDir = storageManager.getPacksDir()

        // Strategy 1: direct filename match
        val directMatch = File(packsDir, "$packId$PACK_EXTENSION")
        if (directMatch.exists()) return directMatch

        // Strategy 2: scan all pack files and check manifest
        val packFiles = packsDir.listFiles { file ->
            file.isFile && file.name.endsWith(PACK_EXTENSION)
        } ?: return null

        for (packFile in packFiles) {
            try {
                val db = PackDatabase(packFile, extractPackId(packFile))
                val manifest = db.getManifest()
                db.close()
                if (manifest["pack_id"] == packId) {
                    return packFile
                }
            } catch (e: Exception) {
                // Skip unreadable files
                Log.w(TAG, "Could not read pack file: ${packFile.name}", e)
            }
        }

        return null
    }

    /**
     * Closes the database handle for a pack identified by its file.
     */
    @Synchronized
    private fun closePackByFile(packFile: File) {
        val packId = extractPackId(packFile)
        openDatabases.remove(packId)?.close()
    }

    /**
     * Extracts a pack_id from a filename by removing the .pack extension.
     * E.g. "maths-grade6-caps-fractions-v0.1.pack" → "maths-grade6-caps-fractions-v0.1"
     */
    private fun extractPackId(packFile: File): String {
        return packFile.name.removeSuffix(PACK_EXTENSION)
    }

    /**
     * Copies a file using buffered I/O with explicit flush and sync.
     *
     * Syncing to disk ensures the data survives a sudden power-off
     * between the copy and the atomic rename.
     */
    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                // Flush to OS buffers and sync to physical storage
                output.flush()
                output.fd.sync()
            }
        }
    }

    /**
     * Removes any `.pack.tmp` files left behind by failed installations.
     * These are safe to delete — a temp file means the install never
     * completed the atomic rename.
     */
    private fun cleanUpTempFiles(packsDir: File) {
        val tempFiles = packsDir.listFiles { file ->
            file.isFile && file.name.endsWith(TEMP_EXTENSION)
        } ?: return

        for (tempFile in tempFiles) {
            Log.i(TAG, "Cleaning up incomplete install: ${tempFile.name}")
            tempFile.delete()
        }
    }
}
