// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.apprunner.actions

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.services.apprunner.AppRunnerServiceNode

class OpenServiceUrlActionTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    private val url = aString()

    @Test
    fun `Open Service Url passes the correct URL to the browser launcher`() {
        val launcher = mock<BrowserLauncher>()
        val action = OpenServiceUrlAction()

        ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, launcher, disposableRule.disposable)
        action.actionPerformed(AppRunnerServiceNode(projectRule.project, aServiceSummary { serviceUrl(url) }), mock())

        verify(launcher).browse("https://$url", project = projectRule.project)
    }
}
