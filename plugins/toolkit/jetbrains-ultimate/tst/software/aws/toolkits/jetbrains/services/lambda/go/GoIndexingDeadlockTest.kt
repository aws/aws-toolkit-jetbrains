// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.StartupActivityTestUtil
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addGoLambdaHandler
import software.aws.toolkits.jetbrains.utils.rules.addGoModFile

class GoIndexingDeadlockTest {

    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    @Test
    fun `waitForProjectActivitiesToComplete prevents indexing deadlock with Go fixtures`() {
        projectRule.fixture.addGoLambdaHandler(subPath = "hello")
        projectRule.fixture.addGoModFile(subPath = "hello")

        // Drain all async ProjectActivity jobs (Go SDK detection, go.mod parsing)
        @Suppress("DEPRECATION")
        StartupActivityTestUtil.waitForProjectActivitiesToComplete(projectRule.project)

        // After activities complete, project should not be stuck in dumb mode
        assertFalse(
            "Project should not be in dumb mode after activities complete",
            DumbService.isDumb(projectRule.project)
        )

        // waitUntilIndexesAreReady should complete quickly (no deadlock)
        IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
    }
}
