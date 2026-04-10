package com.ezansi.app.feature.topics.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezansi.app.core.ai.search.SearchResult
import com.ezansi.app.feature.topics.R

/**
 * Displays a single search result as a Material 3 Card.
 *
 * Shows the chunk title, CAPS topic path, a content snippet, relevance
 * score, and an "Ask AI" action button. Designed for 720p 5-inch screens
 * with ≥48 dp touch targets and TalkBack support (ACC-02, ACC-04).
 *
 * @param result The search result to display.
 * @param onAskAiClick Callback when the learner taps "Ask AI" on this result.
 * @param modifier Modifier for the card container.
 */
@Composable
fun SearchResultCard(
    result: SearchResult,
    onAskAiClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val relevancePercent = (result.score * 100).toInt()
    val cardDescription = stringResource(
        R.string.search_result_label,
        result.title,
        formatTopicPath(result.topicPath),
        relevancePercent,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Title row with relevance badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Title — bold, primary text
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Relevance score badge
                Text(
                    text = stringResource(R.string.search_relevance, relevancePercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Topic path — secondary text
            Text(
                text = formatTopicPath(result.topicPath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Snippet — content preview
            Text(
                text = result.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "Ask AI" button — 48dp min touch target (ACC-02)
            val askAiLabel = stringResource(R.string.search_ask_ai_label, result.title)
            Button(
                onClick = { onAskAiClick(result) },
                modifier = Modifier
                    .align(Alignment.End)
                    .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
                    .semantics { contentDescription = askAiLabel },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.search_ask_ai),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Formats a dot-separated topic path for display.
 *
 * "term1.fractions.addition" → "Term 1 › Fractions › Addition"
 */
internal fun formatTopicPath(topicPath: String): String {
    return topicPath.split(".")
        .joinToString(" › ") { segment ->
            // Handle "termN" patterns → "Term N"
            val termMatch = Regex("^term(\\d+)$").find(segment.lowercase())
            if (termMatch != null) {
                "Term ${termMatch.groupValues[1]}"
            } else {
                segment
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
            }
        }
}
