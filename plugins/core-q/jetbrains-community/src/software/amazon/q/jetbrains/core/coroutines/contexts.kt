// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
// kotlinx.coroutines.Dispatchers is banned
@file:Suppress("BannedImports")

package software.amazon.q.jetbrains.core.coroutines

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private class ModalityStateElement(val modalityState: ModalityState) : AbstractCoroutineContextElement(ModalityStateElementKey)

private object ModalityStateElementKey : CoroutineContext.Key<ModalityStateElement>

private object EdtCoroutineDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val state = context[ModalityStateElementKey]?.modalityState ?: ModalityState.any()
        ApplicationManager.getApplication().invokeLater(block, state)
    }
}

@Deprecated("Always uses ModalityState.any() by default", ReplaceWith("EDT", "software.amazon.q.jetbrains.core.coroutines.EDT"))
fun getCoroutineUiContext(): CoroutineContext = EdtCoroutineDispatcher

fun getCoroutineBgContext(): CoroutineContext = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

val EDT = Dispatchers.EDT

// parallelism should be defined https://youtrack.jetbrains.com/issue/KTOR-6462
fun ioDispatcher(limitedParallelism: Int) = Dispatchers.IO.limitedParallelism(limitedParallelism)
