package io.github.kdroidfilter.seforimapp.core.coroutines

import kotlinx.coroutines.CancellationException

/**
 * A [runCatching] variant that is safe to use inside coroutines.
 *
 * Unlike [runCatching], this function rethrows [CancellationException] instead of
 * capturing it as a [Result.failure]. This preserves structured concurrency: when a
 * coroutine is cancelled, the cancellation signal must propagate and not be swallowed.
 *
 * **Always use this instead of [runCatching] inside suspend functions and coroutine blocks.**
 *
 * Inspired by: https://github.com/santimattius/structured-coroutines (ref-4-2)
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
