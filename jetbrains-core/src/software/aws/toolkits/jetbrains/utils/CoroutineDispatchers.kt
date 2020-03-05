// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

object CoroutineDispatchers {
    fun dispatcherFor(modalityState: ModalityState): CoroutineDispatcher =
        object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                ApplicationManager.getApplication().invokeLater(block, modalityState)
            }

            override fun isDispatchNeeded(context: CoroutineContext) =
                !ApplicationManager.getApplication().isDispatchThread
        }
}
