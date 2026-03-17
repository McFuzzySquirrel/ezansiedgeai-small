package com.ezansi.app.feature.chat.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ezansi.app.feature.chat.R
import kotlinx.coroutines.delay

/** Auto-dismiss timeout in millis — tips vanish after this if not manually dismissed. */
private const val AUTO_DISMISS_DELAY_MS = 10_000L

/**
 * A dismissible onboarding tooltip — non-blocking hint overlaid on existing UI.
 *
 * Design decisions (PRD §8.2 P2-108, §12.1):
 * - NOT a modal dialog — the tooltip sits inline within the screen layout
 * - Permanently dismissible via the ✕ button or auto-dismiss after 10 seconds
 * - Semi-transparent surface for visual distinction without obscuring content
 * - Animates in with fadeIn + slideIn for a gentle entry (no decorative animation)
 * - 48×48 dp dismiss button for cracked-screen accessibility (ACC-02)
 * - Content description on dismiss button for TalkBack (ACC-04)
 *
 * @param text The hint message to display (Grade 4 reading level).
 * @param onDismiss Callback invoked when the tooltip is dismissed (manually or auto).
 * @param modifier Layout modifier applied to the tooltip container.
 */
@Composable
fun OnboardingTooltip(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(true) }
    val dismissDescription = stringResource(R.string.onboarding_dismiss_tip)

    // Auto-dismiss after 10 seconds if the user doesn't tap ✕
    LaunchedEffect(Unit) {
        delay(AUTO_DISMISS_DELAY_MS)
        isVisible = false
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(8.dp))

                // Dismiss button — 48×48 dp minimum touch target (ACC-02)
                IconButton(
                    onClick = {
                        isVisible = false
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = dismissDescription
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null, // Set on parent via semantics
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Welcome banner shown on first launch — a larger onboarding tooltip.
 *
 * Shows ONLY when no profiles exist yet AND the welcome tip hasn't been dismissed.
 * Contains a welcome message and a "Got it" dismiss button. Once dismissed,
 * the [OnboardingManager] records it and the banner never appears again.
 *
 * PRD §8.2 P2-108: Zero-step onboarding — this banner is informational only.
 * The app is fully usable without interacting with it.
 *
 * @param onDismiss Callback invoked when the learner taps "Got it".
 * @param modifier Layout modifier.
 */
@Composable
fun WelcomeBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissLabel = stringResource(R.string.onboarding_got_it)
    val bannerDescription = stringResource(R.string.onboarding_welcome_description)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = bannerDescription
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(12.dp))

            // "Got it" dismiss button — 48 dp height minimum (ACC-02)
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
