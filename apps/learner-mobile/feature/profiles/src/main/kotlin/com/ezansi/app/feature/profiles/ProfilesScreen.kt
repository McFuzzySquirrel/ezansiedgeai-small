package com.ezansi.app.feature.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezansi.app.core.data.LearnerProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profiles screen — manage learner profiles on this device.
 *
 * Supports the shared-device scenario (PRD §4.1 Sipho): multiple learners
 * share one phone, each with their own profile, preferences, and history.
 *
 * Design constraints (PRD §8.4, §11, §12):
 * - Profile selection ≤2 taps from launch (LP-01)
 * - 48×48 dp minimum touch targets (ACC-02)
 * - Long-press to delete with confirmation dialog (only modal exception)
 * - WCAG 2.1 AA colour contrast via theme (ACC-01)
 * - LazyColumn for virtualised profile list (NF-04)
 *
 * @param viewModel The [ProfilesViewModel] providing state and actions.
 * @param onNavigateToPreferences Callback to navigate to the preferences screen.
 * @param modifier Layout modifier applied to the root container.
 */
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Title bar ───────────────────────────────────────────
            ProfilesTopBar(onNavigateToPreferences = onNavigateToPreferences)

            HorizontalDivider()

            // ── Content ─────────────────────────────────────────────
            when {
                state.isLoading -> {
                    LoadingState()
                }
                state.profiles.isEmpty() && !state.isAddingProfile -> {
                    EmptyProfilesState(onAddProfile = viewModel::toggleAddProfile)
                }
                else -> {
                    ProfilesList(
                        profiles = state.profiles,
                        activeProfileId = state.activeProfileId,
                        onSelectProfile = viewModel::setActiveProfile,
                        onDeleteProfile = viewModel::requestDeleteProfile,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Add profile input ───────────────────────────────────
            AnimatedVisibility(
                visible = state.isAddingProfile,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                AddProfileInput(
                    name = state.newProfileName,
                    onNameChanged = viewModel::onNewProfileNameChanged,
                    onSubmit = viewModel::createProfile,
                )
            }
        }

        // ── FAB to add profile ──────────────────────────────────────
        if (!state.isAddingProfile && !state.isLoading) {
            FloatingActionButton(
                onClick = viewModel::toggleAddProfile,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Add a new profile"
                    },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        }

        // ── Delete confirmation dialog ──────────────────────────────
        if (state.profileToDelete != null) {
            val profileName = state.profiles
                .firstOrNull { it.id == state.profileToDelete }?.name ?: ""

            DeleteProfileDialog(
                profileName = profileName,
                onConfirm = viewModel::confirmDeleteProfile,
                onDismiss = viewModel::cancelDeleteProfile,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Internal composables
// ═════════════════════════════════════════════════════════════════════

/**
 * Top bar showing the screen title and a settings/preferences icon.
 */
@Composable
private fun ProfilesTopBar(
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefsDescription = stringResource(R.string.profiles_preferences_button)

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
                text = stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )

            // Preferences button — navigate to per-profile settings
            IconButton(
                onClick = onNavigateToPreferences,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = prefsDescription
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Loading indicator shown while profiles are being fetched.
 */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                contentDescription = "Loading profiles"
            },
        )
    }
}

/**
 * Empty state shown when no profiles exist — prompts the user to create one.
 *
 * This is the first-launch experience for a fresh install. The message
 * is written at Grade 4 reading level (ACC-07).
 */
@Composable
private fun EmptyProfilesState(
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.profiles_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.profiles_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = onAddProfile,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.profiles_create_first),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Scrollable list of learner profiles using [LazyColumn] for virtualised rendering.
 *
 * Each profile card supports:
 * - Tap to switch active profile (≤2 taps from launch, LP-01)
 * - Long-press to trigger delete confirmation
 * - Visual indicator for the currently active profile
 */
@Composable
private fun ProfilesList(
    profiles: List<LearnerProfile>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = profiles,
            key = { it.id },
            contentType = { "profile" },
        ) { profile ->
            ProfileCard(
                profile = profile,
                isActive = profile.id == activeProfileId,
                onSelect = { onSelectProfile(profile.id) },
                onDelete = { onDeleteProfile(profile.id) },
            )
        }
    }
}

/**
 * A single profile card — shows name, last active date, and active indicator.
 *
 * Interaction (PRD §8.4):
 * - Tap: Switch to this profile (sets as active)
 * - Long-press: Triggers delete confirmation dialog
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(
    profile: LearnerProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val lastActive = dateFormat.format(Date(profile.lastActiveAt))
    val cardDescription = if (isActive) {
        stringResource(R.string.profiles_card_active_label, profile.name, lastActive)
    } else {
        stringResource(R.string.profiles_card_label, profile.name, lastActive)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shadowElevation = if (isActive) 4.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onDelete,
            )
            .semantics {
                contentDescription = cardDescription
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Profile icon
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.width(16.dp))

            // Profile info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = stringResource(R.string.profiles_last_active, lastActive),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Active indicator
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.profiles_active_indicator),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Input row for creating a new profile — text field with submit button.
 */
@Composable
private fun AddProfileInput(
    name: String,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.profiles_name_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.width(8.dp))

            TextButton(
                onClick = onSubmit,
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = name.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.profiles_add_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Delete confirmation dialog — the only modal in the app (PRD §12.1 exception).
 *
 * Deletion is irreversible, so a confirmation dialog is justified here.
 * All other onboarding and info displays use inline tooltips.
 */
@Composable
private fun DeleteProfileDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.profiles_delete_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.profiles_delete_message, profileName),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.profiles_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.profiles_delete_cancel))
            }
        },
    )
}
