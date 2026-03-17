package com.ezansi.app.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Abstraction over coroutine dispatchers for testability.
 *
 * Production code injects [DefaultDispatcherProvider]. Tests inject a
 * version backed by [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * so coroutine timing is deterministic.
 *
 * Why an interface instead of using Dispatchers directly:
 * - Hardcoded `Dispatchers.IO` in a repository makes the code untestable
 *   without Dispatchers.setMain/resetMain hacks.
 * - Constructor-injecting a DispatcherProvider keeps tests fast and reliable.
 */
interface DispatcherProvider {
    /** CPU-bound work: JSON parsing, sorting, prompt construction. */
    val default: CoroutineDispatcher

    /** Blocking I/O: SQLite queries, file reads, model loading. */
    val io: CoroutineDispatcher

    /** UI thread: Compose state updates. */
    val main: CoroutineDispatcher

    /** Immediate dispatch on main — avoids unnecessary re-dispatch when already on main. */
    val mainImmediate: CoroutineDispatcher
}

/**
 * Production dispatcher provider using the standard Android dispatchers.
 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
}
