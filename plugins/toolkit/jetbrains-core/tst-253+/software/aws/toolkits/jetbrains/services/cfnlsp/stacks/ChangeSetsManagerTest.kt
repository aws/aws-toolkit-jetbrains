// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import java.util.concurrent.CompletableFuture

class ChangeSetsManagerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockClientService: CfnClientService
    private lateinit var changeSetsManager: ChangeSetsManager

    @Before
    fun setUp() {
        mockClientService = mock()
        changeSetsManager = ChangeSetsManager(projectRule.project).apply {
            clientServiceProvider = { mockClientService }
        }
    }

    @Test
    fun `get returns empty list for unknown stack`() {
        assertThat(changeSetsManager.get("unknown-stack")).isEmpty()
    }

    @Test
    fun `hasMore returns false for unknown stack`() {
        assertThat(changeSetsManager.hasMore("unknown-stack")).isFalse()
    }

    @Test
    fun `fetchChangeSets calls listChangeSets on client service`() {
        whenever(mockClientService.listChangeSets(any())).thenReturn(
            CompletableFuture.completedFuture(ListChangeSetsResult(emptyList(), null))
        )

        changeSetsManager.fetchChangeSets("my-stack")

        verify(mockClientService).listChangeSets(any())
    }

    @Test
    fun `loadMoreChangeSets does nothing when no cached data`() {
        changeSetsManager.loadMoreChangeSets("unknown-stack")

        verify(mockClientService, never()).listChangeSets(any())
    }
}
