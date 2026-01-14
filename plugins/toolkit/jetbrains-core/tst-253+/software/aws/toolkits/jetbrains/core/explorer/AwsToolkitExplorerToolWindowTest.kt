// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.runInEdtAndGet
import org.assertj.core.api.Assertions.assertThat
import software.aws.toolkit.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.ToolWindowHeadlessManagerImpl

class AwsToolkitExplorerToolWindowTest : HeavyPlatformTestCase() {

    fun `test save current tab state`() {
        (ToolWindowManager.getInstance(project) as ToolWindowHeadlessManagerImpl)
            .doRegisterToolWindow(AwsToolkitExplorerFactory.TOOLWINDOW_ID)
        val sut = runInEdtAndGet { AwsToolkitExplorerToolWindow(project) }

        runInEdt {
            sut.selectTab(AwsToolkitExplorerToolWindow.EXPLORER_TAB_ID)
            assertThat(sut.state.selectedTab).isEqualTo(AwsToolkitExplorerToolWindow.EXPLORER_TAB_ID)

            sut.selectTab(AwsToolkitExplorerToolWindow.DEVTOOLS_TAB_ID)
            assertThat(sut.state.selectedTab).isEqualTo(AwsToolkitExplorerToolWindow.DEVTOOLS_TAB_ID)
        }
    }

    fun `test load tab state`() {
        (ToolWindowManager.getInstance(project) as ToolWindowHeadlessManagerImpl)
            .doRegisterToolWindow(AwsToolkitExplorerFactory.TOOLWINDOW_ID)
        val sut = runInEdtAndGet { AwsToolkitExplorerToolWindow(project) }
        runInEdt {
            sut.loadState(
                AwsToolkitExplorerToolWindowState().apply {
                    selectedTab =
                        AwsToolkitExplorerToolWindow.EXPLORER_TAB_ID
                }
            )
            assertThat(sut.state.selectedTab).isEqualTo(AwsToolkitExplorerToolWindow.Q_TAB_ID)

            sut.loadState(
                AwsToolkitExplorerToolWindowState().apply {
                    selectedTab =
                        AwsToolkitExplorerToolWindow.DEVTOOLS_TAB_ID
                }
            )
            assertThat(sut.state.selectedTab).isEqualTo(AwsToolkitExplorerToolWindow.Q_TAB_ID)
        }
    }

    fun `test handles loading invalid state`() {
        (ToolWindowManager.getInstance(project) as ToolWindowHeadlessManagerImpl)
            .doRegisterToolWindow(AwsToolkitExplorerFactory.TOOLWINDOW_ID)
        val sut = runInEdtAndGet { AwsToolkitExplorerToolWindow(project) }

        sut.loadState(
            AwsToolkitExplorerToolWindowState().apply {
                selectedTab = aString()
            }
        )
    }
}
