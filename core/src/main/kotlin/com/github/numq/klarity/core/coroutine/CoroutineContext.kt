package com.github.numq.klarity.core.coroutine

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

suspend fun CoroutineContext.cancelChildrenAndJoin() {
    this[Job]?.children.orEmpty().toList().takeIf(List<*>::isNotEmpty)?.let { children ->
        coroutineScope {
            children.map { job ->
                async {
                    job.cancelAndJoin()
                }
            }.awaitAll()
        }
    }
}