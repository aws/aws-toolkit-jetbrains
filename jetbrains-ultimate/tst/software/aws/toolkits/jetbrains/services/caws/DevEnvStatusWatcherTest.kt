// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.caws

import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class DevEnvStatusWatcherTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Test
    fun `test watcher`() {
        val watcher = DevEnvStatusWatcher.getInstance(projectRule.project)
        watcher.notifyBackendOfActivity()


    }

}
