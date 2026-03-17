package com.ezansi.app

import android.app.Application
import com.ezansi.app.di.AppContainer

/**
 * eZansiEdgeAI Application entry point.
 *
 * Responsibilities:
 * - Initialises the manual DI [AppContainer]
 * - No analytics, no crash reporting, no network initialisation
 * - AI models are NOT loaded here — they lazy-load on first query
 *   (coding-principles §2: inference discipline)
 */
class EzansiApplication : Application() {

    /** Manual DI container — access from any Activity or Composable. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
