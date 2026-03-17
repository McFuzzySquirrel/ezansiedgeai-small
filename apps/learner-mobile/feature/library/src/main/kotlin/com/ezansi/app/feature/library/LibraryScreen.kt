package com.ezansi.app.feature.library

import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezansi.app.core.data.PackMetadata

/**
 * Content library screen — manage installed content packs.
 *
 * Displays installed packs with metadata (name, version, subject, grade, size)
 * and supports pack deletion to reclaim storage (PRD §8.5 CP-06, CP-07).
 *
 * Design constraints (PRD §11, §12):
 * - 48×48 dp minimum touch targets (ACC-02)
 * - WCAG 2.1 AA colour contrast (ACC-01)
 * - Zero-pack state with friendly message (CP-09)
 * - LazyColumn for virtualised pack list (NF-04)
 * - Storage info shown for device awareness
 *
 * @param viewModel The [LibraryViewModel] providing state and actions.
 * @param modifier Layout modifier applied to the root container.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // ── Title bar ───────────────────────────────────────────────
        LibraryTopBar()

        HorizontalDivider()

        // ── Content ─────────────────────────────────────────────────
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Loading content library"
                        },
                    )
                }
            }
            state.packs.isEmpty() -> {
                EmptyLibraryState()
            }
            else -> {
                PacksList(
                    packs = state.packs,
                    onDeletePack = viewModel::requestDeletePack,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Storage info footer ─────────────────────────────────────
        StorageInfoFooter()

        // ── Delete confirmation dialog ──────────────────────────────
        if (state.packToDelete != null) {
            val packName = state.packs
                .firstOrNull { it.packId == state.packToDelete }?.displayName ?: ""

            DeletePackDialog(
                packName = packName,
                onConfirm = viewModel::confirmDeletePack,
                onDismiss = viewModel::cancelDeletePack,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Internal composables
// ═════════════════════════════════════════════════════════════════════

/**
 * Top bar showing the library title.
 */
@Composable
private fun LibraryTopBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/**
 * Empty state — shown when no content packs are installed (CP-09).
 */
@Composable
private fun EmptyLibraryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.library_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Scrollable list of installed content packs.
 */
@Composable
private fun PacksList(
    packs: List<PackMetadata>,
    onDeletePack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = packs,
            key = { it.packId },
            contentType = { "pack" },
        ) { pack ->
            PackCard(
                pack = pack,
                onDelete = { onDeletePack(pack.packId) },
            )
        }
    }
}

/**
 * A single content pack card showing metadata and a delete action.
 */
@Composable
private fun PackCard(
    pack: PackMetadata,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeText = formatBytes(pack.sizeBytes)
    val deleteDescription = stringResource(R.string.library_delete_pack, pack.displayName)
    val cardDescription = stringResource(
        R.string.library_pack_label,
        pack.displayName,
        pack.version,
        pack.subject,
        pack.grade,
    )

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = cardDescription
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row: name + delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.library_pack_subtitle,
                            pack.subject,
                            pack.grade,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Delete button — 48×48 dp touch target (ACC-02)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = deleteDescription
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Detail chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailChip(
                    label = stringResource(R.string.library_version_label),
                    value = pack.version,
                )
                DetailChip(
                    label = stringResource(R.string.library_chunks_label),
                    value = pack.chunkCount.toString(),
                )
                DetailChip(
                    label = stringResource(R.string.library_size_label),
                    value = sizeText,
                )
            }
        }
    }
}

/**
 * Small label/value chip for pack metadata.
 */
@Composable
private fun DetailChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Footer showing available storage space on the device.
 */
@Composable
private fun StorageInfoFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val availableStorage = remember {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            formatBytes(stat.availableBytes)
        } catch (_: Exception) {
            "Unknown"
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.library_storage_available, availableStorage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Delete pack confirmation dialog.
 */
@Composable
private fun DeletePackDialog(
    packName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.library_delete_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.library_delete_message, packName),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.library_delete_cancel))
            }
        },
    )
}

/**
 * Formats bytes into a human-readable string (KB, MB, GB).
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
