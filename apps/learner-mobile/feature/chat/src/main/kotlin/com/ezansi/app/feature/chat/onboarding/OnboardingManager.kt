package com.ezansi.app.feature.chat.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages onboarding tooltip state — tracks which tips have been shown and dismissed.
 *
 * Uses unencrypted SharedPreferences because tooltip state is non-sensitive UI
 * configuration (not learner data). This keeps the onboarding system lightweight
 * and avoids pulling in the encryption dependency for cosmetic state.
 *
 * Zero-step onboarding (PRD §8.2 P2-108):
 * - The app is fully usable without completing any onboarding steps
 * - Tips are optional and permanently dismissible
 * - No modals, no permission dialogs, no sign-up walls
 *
 * @param context Application context for SharedPreferences access.
 */
class OnboardingManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * Returns true if the tip should be shown to the user.
     *
     * A tip is shown only if it has not been previously dismissed. Once a
     * learner taps the dismiss button (or the tip auto-dismisses after timeout),
     * [dismissTip] records the dismissal and this returns false permanently.
     *
     * @param tipId One of the known tip identifiers (e.g. "welcome", "first_question").
     */
    fun shouldShowTip(tipId: String): Boolean {
        return !prefs.getBoolean(keyForTip(tipId), false)
    }

    /**
     * Permanently dismisses a tip so it never shows again.
     *
     * Uses apply() (async write) since this is non-critical UI state —
     * if the write is lost due to a crash, the tip simply shows again.
     *
     * @param tipId The tip identifier to dismiss.
     */
    fun dismissTip(tipId: String) {
        prefs.edit().putBoolean(keyForTip(tipId), true).apply()
    }

    /**
     * Resets all tips so they show again on next visit.
     *
     * Useful for testing or if the user wants to re-see the onboarding hints.
     * Clears all keys in the onboarding preferences file.
     */
    fun resetAllTips() {
        prefs.edit().clear().apply()
    }

    private fun keyForTip(tipId: String): String = "${KEY_PREFIX}$tipId"

    companion object {
        /** SharedPreferences file name — separate from encrypted learner data prefs. */
        private const val PREFS_NAME = "ezansi_onboarding"

        /** Key prefix for dismissed tip state. */
        private const val KEY_PREFIX = "onboarding_dismissed_"

        // ── Known tip identifiers ──────────────────────────────────
        /** Welcome banner shown on first launch when no profiles exist. */
        const val TIP_WELCOME = "welcome"

        /** Tip shown after the first AI answer is received. */
        const val TIP_FIRST_QUESTION = "first_question"

        /** Tip shown on first visit to the Topics screen. */
        const val TIP_TOPICS_HINT = "topics_hint"

        /** Tip nudging profile creation from the chat screen. */
        const val TIP_PROFILE_HINT = "profile_hint"
    }
}
