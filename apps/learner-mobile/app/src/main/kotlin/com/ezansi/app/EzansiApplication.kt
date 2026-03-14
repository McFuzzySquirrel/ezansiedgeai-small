package com.ezansi.app

import android.app.Application

/**
 * eZansiEdgeAI Application entry point.
 *
 * Responsibilities:
 * - DI container initialisation (when DI framework is wired)
 * - No analytics, no crash reporting, no network initialisation
 */
class EzansiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // TODO: Initialise DI container (manual DI or Koin/Hilt)
        // TODO: Lazy-load AI models on first query, not here (coding-principles §2)
    }
}
