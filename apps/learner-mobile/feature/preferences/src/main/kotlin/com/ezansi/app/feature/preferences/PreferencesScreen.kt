package com.ezansi.app.feature.preferences

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Preferences screen — configure learning preferences for the active profile.
 *
 * All changes save immediately (no save button) — per PRD §8.6.
 * Simple toggles and radio buttons with Grade 4-readable labels (ACC-07).
 *
 * Design constraints (PRD §8.6, §11, §12):
 * - Preferences accessible ≤2 taps from any screen (PE-05)
 * - 48×48 dp minimum touch targets for radio buttons (ACC-02)
 * - WCAG 2.1 AA colour contrast (ACC-01)
 * - System font-size scaling respected (ACC-03)
 * - LazyColumn for virtualised scrolling (NF-04)
 *
 * @param viewModel The [PreferencesViewModel] providing state and actions.
 * @param modifier Layout modifier applied to the root container.
 */
@Composable
fun PreferencesScreen(
    viewModel: PreferencesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // ── Title bar ───────────────────────────────────────────────
        PreferencesTopBar(profileName = state.profileName)

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
                            contentDescription = "Loading preferences"
                        },
                    )
                }
            }
            state.profileId == null -> {
                // No active profile — prompt to create one
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.preferences_no_profile),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                PreferencesContent(
                    state = state,
                    onExplanationStyleChanged = viewModel::setExplanationStyle,
                    onReadingLevelChanged = viewModel::setReadingLevel,
                    onExampleTypeChanged = viewModel::setExampleType,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// Internal composables
// ═════════════════════════════════════════════════════════════════════

/**
 * Top bar showing the screen title and the active profile name.
 */
@Composable
private fun PreferencesTopBar(
    profileName: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.preferences_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (profileName != null) {
                Text(
                    text = stringResource(R.string.preferences_for_profile, profileName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Main preferences content — scrollable sections with radio groups.
 */
@Composable
private fun PreferencesContent(
    state: PreferencesUiState,
    onExplanationStyleChanged: (String) -> Unit,
    onReadingLevelChanged: (String) -> Unit,
    onExampleTypeChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Explanation Style (PE-01) ───────────────────────────────
        item(key = "section_explanation_style") {
            PreferenceSection(
                title = stringResource(R.string.preferences_explanation_style_title),
                description = stringResource(R.string.preferences_explanation_style_description),
            )
        }

        item(key = "explanation_step_by_step") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_style_step_by_step),
                description = stringResource(R.string.preferences_style_step_by_step_desc),
                selected = state.explanationStyle == "step_by_step",
                onClick = { onExplanationStyleChanged("step_by_step") },
            )
        }
        item(key = "explanation_visual") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_style_visual),
                description = stringResource(R.string.preferences_style_visual_desc),
                selected = state.explanationStyle == "visual",
                onClick = { onExplanationStyleChanged("visual") },
            )
        }
        item(key = "explanation_simple") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_style_simple),
                description = stringResource(R.string.preferences_style_simple_desc),
                selected = state.explanationStyle == "simple",
                onClick = { onExplanationStyleChanged("simple") },
            )
        }
        item(key = "explanation_detailed") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_style_detailed),
                description = stringResource(R.string.preferences_style_detailed_desc),
                selected = state.explanationStyle == "detailed",
                onClick = { onExplanationStyleChanged("detailed") },
            )
        }

        // ── Reading Level (PE-03) ───────────────────────────────────
        item(key = "section_reading_level") {
            Spacer(Modifier.height(8.dp))
            PreferenceSection(
                title = stringResource(R.string.preferences_reading_level_title),
                description = stringResource(R.string.preferences_reading_level_description),
            )
        }

        item(key = "reading_simple") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_level_simple),
                description = stringResource(R.string.preferences_level_simple_desc),
                selected = state.readingLevel == "simple",
                onClick = { onReadingLevelChanged("simple") },
            )
        }
        item(key = "reading_standard") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_level_standard),
                description = stringResource(R.string.preferences_level_standard_desc),
                selected = state.readingLevel == "standard",
                onClick = { onReadingLevelChanged("standard") },
            )
        }
        item(key = "reading_advanced") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_level_advanced),
                description = stringResource(R.string.preferences_level_advanced_desc),
                selected = state.readingLevel == "advanced",
                onClick = { onReadingLevelChanged("advanced") },
            )
        }

        // ── Example Type (PE-02) ────────────────────────────────────
        item(key = "section_example_type") {
            Spacer(Modifier.height(8.dp))
            PreferenceSection(
                title = stringResource(R.string.preferences_example_type_title),
                description = stringResource(R.string.preferences_example_type_description),
            )
        }

        item(key = "example_everyday") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_example_everyday),
                description = stringResource(R.string.preferences_example_everyday_desc),
                selected = state.exampleType == "everyday",
                onClick = { onExampleTypeChanged("everyday") },
            )
        }
        item(key = "example_abstract") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_example_abstract),
                description = stringResource(R.string.preferences_example_abstract_desc),
                selected = state.exampleType == "abstract",
                onClick = { onExampleTypeChanged("abstract") },
            )
        }
        item(key = "example_visual") {
            PreferenceRadioOption(
                label = stringResource(R.string.preferences_example_visual),
                description = stringResource(R.string.preferences_example_visual_desc),
                selected = state.exampleType == "visual",
                onClick = { onExampleTypeChanged("visual") },
            )
        }

        // Bottom spacer for breathing room
        item(key = "spacer_bottom") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Section header for a group of preference options.
 */
@Composable
private fun PreferenceSection(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A single radio option with label and description.
 *
 * 48×48 dp minimum touch target on the radio button (ACC-02).
 * Entire row is clickable for easier touch interaction.
 */
@Composable
private fun PreferenceRadioOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionDescription = if (selected) {
        "$label, selected. $description"
    } else {
        "$label. $description"
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = optionDescription
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
