// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.MockClientManagerRule

class ExplorerTagFilterTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    val filter = ExplorerTagFilter()

    @Test
    fun `Does not filter if tag filter disabled`() {
        ResourceFilterManager.getInstance(projectRule.project).state.tagsEnabled = false

    }

    @Test
    fun `Does not filter if parent is not AWS Explorer node`() {

    }

    @Test
    fun `AWS nodes are filtered if enabled`() {

    }

    @Test
    fun `Does not filter out non AWS nodes`() {

    }
}
