// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.java

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

internal inline fun runInDumbMode(projectRule: JavaCodeInsightTestFixtureRule, crossinline block: () -> Unit) {
    val dumbServiceImpl = DumbService.getInstance(projectRule.project) as DumbServiceImpl
    runBlocking {
        // automatically on correct thread in 233+
        withContext(EDT) {
            dumbServiceImpl.runInDumbMode {
                block()
            }
        }
    }
}
