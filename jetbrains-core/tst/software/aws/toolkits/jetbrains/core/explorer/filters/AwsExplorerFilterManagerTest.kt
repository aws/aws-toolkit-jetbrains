// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule

class AwsExplorerFilterManagerTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val resourceCache = MockResourceCacheRule()

    @Test
    fun treeIsRefreshedWhenFilterIsChanged() {
        // We use the resource cache as a hack to see if the refresh explorer tree has been called
        val sut = AwsExplorerFilterManager.getInstance(projectRule.project)

        resourceCache.addEntry(projectRule.project, "foo", "bar")
        sut.setFilter(mock())
        assertThat(resourceCache.cache.entryCount()).isZero()

        resourceCache.addEntry(projectRule.project, "foo", "bar")
        sut.clearFilter()
        assertThat(resourceCache.cache.entryCount()).isZero()
    }

    @Test
    fun disposableFiltersAreDisposedOnClear() {
        val sut = AwsExplorerFilterManager.getInstance(projectRule.project)
        val filter = mock<DisposableFilter>()
        sut.setFilter(filter)

        sut.clearFilter()
        verify(filter).dispose()
    }

    interface DisposableFilter : AwsExplorerFilter, Disposable
}
