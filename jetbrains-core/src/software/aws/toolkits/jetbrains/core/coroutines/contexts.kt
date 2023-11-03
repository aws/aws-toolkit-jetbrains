// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.core.coroutines

import com.intellij.openapi.application.EDT
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

// this is technically not part of [kotlinx.coroutines.Dispatchers], but we have to suppress the linter here because it's defined as an extension of Dispatchers
fun getCoroutineUiContext(): CoroutineContext = Dispatchers.EDT

fun getCoroutineBgContext(): CoroutineContext = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()
