package com.ezansi.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ezansi.app.di.AppContainer
import com.ezansi.app.feature.chat.ChatScreen
import com.ezansi.app.feature.chat.ChatViewModel
import com.ezansi.app.feature.chat.ChatViewModelFactory
import com.ezansi.app.feature.chat.R
import com.ezansi.app.feature.chat.onboarding.OnboardingManager
import com.ezansi.app.feature.chat.onboarding.OnboardingTooltip
import com.ezansi.app.feature.library.LibraryScreen
import com.ezansi.app.feature.library.LibraryViewModel
import com.ezansi.app.feature.library.LibraryViewModelFactory
import com.ezansi.app.feature.preferences.PreferencesScreen
import com.ezansi.app.feature.preferences.PreferencesViewModel
import com.ezansi.app.feature.preferences.PreferencesViewModelFactory
import com.ezansi.app.feature.profiles.ProfilesScreen
import com.ezansi.app.feature.profiles.ProfilesViewModel
import com.ezansi.app.feature.profiles.ProfilesViewModelFactory
import com.ezansi.app.feature.topics.TopicsScreen
import com.ezansi.app.feature.topics.TopicsViewModel
import com.ezansi.app.feature.topics.TopicsViewModelFactory

/**
 * Navigation routes for eZansiEdgeAI.
 *
 * Each route maps to a feature module screen. The route string is used
 * as the NavHost destination identifier.
 *
 * Route hierarchy:
 * - Chat (start destination) — primary interaction
 * - Topics — browse curriculum content
 * - Profiles — manage learner profiles
 * - Preferences — per-profile settings (accessed from Profiles)
 * - Library — manage content packs (accessed from Topics)
 */
sealed class EzansiRoute(val route: String) {
    /** Chat screen — ask questions, get explanations. Start destination. */
    data object Chat : EzansiRoute("chat")

    /**
     * Chat screen with a pre-filled question from the topic browser.
     *
     * The question is passed as a URL-encoded navigation argument.
     * The NavHost extracts it and calls [ChatViewModel.onInputChanged]
     * followed by [ChatViewModel.onSendMessage] to submit immediately.
     */
    data object ChatWithQuestion : EzansiRoute("chat?question={question}") {
        /** Builds the route string with the question encoded. */
        fun createRoute(question: String): String {
            return "chat?question=${java.net.URLEncoder.encode(question, "UTF-8")}"
        }
    }

    /** Topics browser — explore CAPS-aligned curriculum topics. */
    data object Topics : EzansiRoute("topics")

    /** Profiles — create, switch, manage learner profiles. */
    data object Profiles : EzansiRoute("profiles")

    /** Preferences — per-profile learning preferences. */
    data object Preferences : EzansiRoute("preferences")

    /** Content library — manage installed content packs. */
    data object Library : EzansiRoute("library")
}

/**
 * Main navigation host for eZansiEdgeAI.
 *
 * Wires each [EzansiRoute] to its feature module screen composable.
 * The [container] is passed through so screens can access dependencies
 * (via ViewModel factories).
 *
 * All screens receive their dependencies through ViewModel factories
 * that take individual interfaces (not AppContainer) to avoid circular
 * module dependencies.
 *
 * @param navController The navigation controller from the host Activity.
 * @param container The DI container for dependency access.
 * @param modifier Modifier applied to the NavHost.
 */
@Composable
fun EzansiNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = EzansiRoute.Chat.route,
        modifier = modifier,
    ) {
        composable(
            route = "chat?question={question}",
            arguments = listOf(
                navArgument("question") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModelFactory(
                    explanationEngine = container.explanationEngine,
                    chatHistoryRepository = container.chatHistoryRepository,
                    profileRepository = container.profileRepository,
                    contentPackRepository = container.contentPackRepository,
                ),
            )

            // If a question was passed from the topic browser, submit it
            val question = backStackEntry.arguments?.getString("question")
            if (!question.isNullOrBlank()) {
                val decoded = java.net.URLDecoder.decode(question, "UTF-8")
                LaunchedEffect(decoded) {
                    chatViewModel.onInputChanged(decoded)
                    chatViewModel.onSendMessage()
                }
            }

            ChatScreen(viewModel = chatViewModel)
        }

        composable(EzansiRoute.Topics.route) {
            val topicsViewModel: TopicsViewModel = viewModel(
                factory = TopicsViewModelFactory(
                    contentPackRepository = container.contentPackRepository,
                ),
            )

            // Onboarding: show a tip on first visit to Topics (P2-108)
            val context = LocalContext.current
            val onboardingManager = remember { OnboardingManager(context) }
            var showTopicsTip by remember {
                mutableStateOf(
                    onboardingManager.shouldShowTip(OnboardingManager.TIP_TOPICS_HINT),
                )
            }

            Box {
                TopicsScreen(
                    viewModel = topicsViewModel,
                    onNavigateToChat = { question ->
                        navController.navigate(
                            EzansiRoute.ChatWithQuestion.createRoute(question),
                        )
                    },
                    onNavigateToLibrary = {
                        navController.navigate(EzansiRoute.Library.route)
                    },
                )

                // Topics onboarding tip — overlaid at the top of the screen
                if (showTopicsTip) {
                    OnboardingTooltip(
                        text = stringResource(R.string.onboarding_topics_screen_hint),
                        onDismiss = {
                            onboardingManager.dismissTip(OnboardingManager.TIP_TOPICS_HINT)
                            showTopicsTip = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 72.dp),
                    )
                }
            }
        }

        composable(EzansiRoute.Profiles.route) {
            val profilesViewModel: ProfilesViewModel = viewModel(
                factory = ProfilesViewModelFactory(
                    profileRepository = container.profileRepository,
                ),
            )
            ProfilesScreen(
                viewModel = profilesViewModel,
                onNavigateToPreferences = {
                    navController.navigate(EzansiRoute.Preferences.route)
                },
            )
        }

        composable(EzansiRoute.Preferences.route) {
            val preferencesViewModel: PreferencesViewModel = viewModel(
                factory = PreferencesViewModelFactory(
                    preferenceRepository = container.preferenceRepository,
                    profileRepository = container.profileRepository,
                ),
            )
            PreferencesScreen(viewModel = preferencesViewModel)
        }

        composable(EzansiRoute.Library.route) {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = LibraryViewModelFactory(
                    contentPackRepository = container.contentPackRepository,
                ),
            )
            LibraryScreen(viewModel = libraryViewModel)
        }
    }
}
