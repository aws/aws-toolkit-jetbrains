// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.util.DefaultCodeWhispererFileContextProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.satisfiesKt

class NodeJsCodeWhispererFileContextProviderTest {
    @JvmField
    @Rule
    val projectRule = HeavyJavaCodeInsightTestFixtureRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    lateinit var sut: DefaultCodeWhispererFileContextProvider

    lateinit var fixture: JavaCodeInsightTestFixture
    lateinit var project: Project

    @Before
    fun setup() {
        fixture = projectRule.fixture
        project = projectRule.project

        sut = FileContextProvider.getInstance(project) as DefaultCodeWhispererFileContextProvider
    }

    @Test
    fun `extractSupplementalFileContext on background should not cause read lock error`() = runTest {
        // regression test for https://github.com/aws/aws-toolkit-jetbrains/issues/4888
        assertThat(ApplicationManager.getApplication()).satisfiesKt {
            assertThat(it.isDispatchThread).isFalse()
            assertThat(it.isReadAccessAllowed).isFalse()
        }

        val psiFile = fixture.configureByText("test.d.ts", "")

        val fileContext = aFileContextInfo(CodeWhispererTypeScript.INSTANCE)
        sut.extractSupplementalFileContext(psiFile, fileContext, 50)
    }
}
