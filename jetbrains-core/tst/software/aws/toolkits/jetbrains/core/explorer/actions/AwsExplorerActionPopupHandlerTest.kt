// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.actions

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import software.aws.toolkits.core.utils.test.notNull
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.utils.isInstanceOfSatisfying

class AwsExplorerActionPopupHandlerTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Test
    fun `action contributors add actions to the popup`() {
        val dummyNode = mock<AwsExplorerNode<String>>()
        val sut = AwsExplorerActionPopupHandler { dummyNode }
        val mockExtension = object : AwsExplorerActionContributor {
            override fun process(group: DefaultActionGroup, node: AwsExplorerNode<*>) {
                group.add(mock())
            }
        }

        ExtensionTestUtil.maskExtensions(AwsExplorerActionContributor.EP_NAME, listOf(mockExtension), disposableRule.disposable)

        assertThat(sut.buildPopup()?.actionGroup).notNull.isInstanceOfSatisfying<DefaultActionGroup> {
            assertThat(it.childrenCount).isEqualTo(1)
        }
    }

    @Test
    fun `popup is null shown when no actions`() {
        val dummyNode = mock<AwsExplorerNode<String>>()
        val sut = AwsExplorerActionPopupHandler { dummyNode }

        ExtensionTestUtil.maskExtensions(AwsExplorerActionContributor.EP_NAME, emptyList(), disposableRule.disposable)
        assertThat(sut.buildPopup()).isNull()
    }
}
