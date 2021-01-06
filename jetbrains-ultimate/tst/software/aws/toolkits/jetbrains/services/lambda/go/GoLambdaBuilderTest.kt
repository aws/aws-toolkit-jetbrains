// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndGet
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addGoLambdaHandler
import software.aws.toolkits.jetbrains.utils.rules.addGoModFile
import java.nio.file.Path
import java.nio.file.Paths

class GoLambdaBuilderTest {
    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    private val sut = GoLambdaBuilder()

    @Test
    fun handlerBaseDirIsCorrect() {
        val handler = projectRule.fixture.addGoLambdaHandler(subPath = "helloworld")
        projectRule.fixture.addGoModFile("helloworld")

        val baseDir = sut.handlerBaseDirectory(projectRule.module, handler)
        val root = getContentRoot(handler)
        assertThat(baseDir.toAbsolutePath()).isEqualTo(root.resolve("helloworld"))
    }

    @Test
    fun handlerBaseDirIsCorrectInSubDir() {
        val handler = projectRule.fixture.addGoLambdaHandler(subPath = "helloworld/foobar")
        projectRule.fixture.addGoModFile("helloworld")

        val baseDir = sut.handlerBaseDirectory(projectRule.module, handler)
        val root = getContentRoot(handler)
        assertThat(baseDir).isEqualTo(root.resolve("helloworld"))
    }

    @Test
    fun missingGoModThrowsForHandlerBaseDir() {
        val handlerFile = projectRule.fixture.addGoLambdaHandler(subPath = "helloworld/foobar")

        assertThatThrownBy {
            sut.handlerBaseDirectory(projectRule.module, handlerFile)
        }.hasMessageStartingWith("Cannot locate go.mod")
    }

    @Test
    fun buildDirectoryIsCorrect() {
        val baseDir = sut.getBuildDirectory(projectRule.module)
        val root = ModuleRootManager.getInstance(projectRule.module).contentRoots.first().path
        assertThat(baseDir).isEqualTo(Paths.get(root, SamCommon.SAM_BUILD_DIR, "build"))
    }

    private fun getContentRoot(handler: PsiElement): Path = runInEdtAndGet {
        Paths.get(
            ProjectFileIndex.getInstance(projectRule.project).getContentRootForFile(handler.containingFile.virtualFile)?.path ?: ""
        )
    }
}
