// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule

class ResourceFilterManagerTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockResourceCache = MockResourceCacheRule(projectRule)

    val filter = ExplorerTagFilter()

    @Test
    fun `tagFiltersEnabled return true if tags specified and enabled`() {

    }

    @Test
    fun `tagFiltersEnabled returns false when no tags specified`() {

    }

    @Test
    fun `tagFiltersEnabled returns no resources if resource doesn't have all tags`() {

    }

    @Test
    fun `tagFiltersEnabled returns no resources if resource doesn't have the correct tag value`() {

    }

    @Test
    fun `tagFiltersEnabled returns no resources if resource doesn't have the tag specified`() {

    }

    @Test
    fun `tagFiltersEnabled returns resources if resource has matching tag and value`() {

    }

    @Test
    fun `tagFiltersEnabled returns resources if resource has matching tag and no values specified`() {

    }
}
