package com.arflix.tv.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** A slow provider must not erase sources already found by another provider. */
internal suspend fun <T> withPartialVodResults(
    timeoutMs: Long,
    resolve: suspend (publish: (List<T>) -> Unit) -> List<T>,
): List<T> {
    var completed = emptyList<T>()
    return try {
        withTimeoutOrNull(timeoutMs) {
            resolve { completed = it.toList() }
        } ?: completed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        completed
    }
}
