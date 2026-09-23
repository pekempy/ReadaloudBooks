package com.pekempy.ReadAloudbooks.util

import kotlinx.coroutines.*

class Debouncer(private val delayMs: Long) {
    private var debounceJob: Job? = null

    fun debounce(coroutineScope: CoroutineScope, action: suspend () -> Unit) {
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(delayMs)
            action()
        }
    }

    fun cancel() {
        debounceJob?.cancel()
    }
}
