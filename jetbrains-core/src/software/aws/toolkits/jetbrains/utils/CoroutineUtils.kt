// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import kotlin.coroutines.CoroutineContext

fun getCoroutineUiContext(modalityState: ModalityState? = null, disposable: Disposable? = null): CoroutineContext {
    val uiThread = if (modalityState == null) AppUIExecutor.onUiThread() else AppUIExecutor.onUiThread(modalityState)
    return if (disposable == null) {
        uiThread
    } else {
        uiThread.expireWith(disposable)
    }.coroutineDispatchingContext()
}
