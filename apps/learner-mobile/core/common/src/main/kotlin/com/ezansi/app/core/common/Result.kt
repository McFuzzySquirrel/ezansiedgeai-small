package com.ezansi.app.core.common

/**
 * Unified result wrapper for all async operations in eZansiEdgeAI.
 *
 * Every repository and engine method returns [EzansiResult] so callers
 * handle success, error, and loading states consistently without
 * exceptions leaking across module boundaries.
 *
 * Usage:
 * ```kotlin
 * when (val result = repository.loadPack(path)) {
 *     is EzansiResult.Success -> showPack(result.data)
 *     is EzansiResult.Error   -> showError(result.message)
 *     is EzansiResult.Loading -> showSpinner()
 * }
 * ```
 */
sealed class EzansiResult<out T> {

    /** Operation completed successfully with [data]. */
    data class Success<out T>(val data: T) : EzansiResult<T>()

    /**
     * Operation failed with a human-readable [message].
     * [cause] is retained for logging but never shown to the learner.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : EzansiResult<Nothing>()

    /** Operation is in progress. Used for UI state, not returned by suspending functions. */
    data object Loading : EzansiResult<Nothing>()
}

/** Maps the success value, passing errors and loading through unchanged. */
inline fun <T, R> EzansiResult<T>.map(transform: (T) -> R): EzansiResult<R> = when (this) {
    is EzansiResult.Success -> EzansiResult.Success(transform(data))
    is EzansiResult.Error -> this
    is EzansiResult.Loading -> this
}

/** Returns the success value or null. */
fun <T> EzansiResult<T>.getOrNull(): T? = when (this) {
    is EzansiResult.Success -> data
    else -> null
}

/** Returns the success value or the result of [fallback]. */
inline fun <T> EzansiResult<T>.getOrElse(fallback: (EzansiResult<T>) -> T): T = when (this) {
    is EzansiResult.Success -> data
    else -> fallback(this)
}
