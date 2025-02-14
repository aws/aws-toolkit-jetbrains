// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class GitIgnoreFilteringUtilTest {

    @Mock
    private lateinit var mockModuleDir: VirtualFile

    private lateinit var gitIgnoreFilteringUtil: GitIgnoreFilteringUtil

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        gitIgnoreFilteringUtil = GitIgnoreFilteringUtil(mockModuleDir)
    }

    @Test
    fun `test default gitignore patterns are initialized`() {
        val defaultPatterns = listOf(
            ".aws-sam",
            ".gem",
            ".git",
            ".gitignore",
            ".gradle",
            ".hg",
            ".idea",
            ".project",
            ".rvm",
            ".svn",
            "*.zip",
            "*.bin",
            "*.png",
            "*.jpg",
            "*.svg",
            "*.pyc",
            "license.txt",
            "License.txt",
            "LICENSE.txt",
            "license.md",
            "License.md",
            "LICENSE.md",
            "node_modules",
            "build",
            "dist",
            "annotation-generated-src",
            "annotation-generated-tst"
        )

        // Access the private field using reflection for testing
        val field = GitIgnoreFilteringUtil::class.java.getDeclaredField("additionalGitIgnoreRules")
        field.isAccessible = true
        val actualPatterns = field.get(gitIgnoreFilteringUtil) as Set<String>

        assertThat(defaultPatterns.toSet()).isEqualTo(actualPatterns)
    }

    @Test
    fun `test initialization with different useCase`() {
        val utilWithUseCase = GitIgnoreFilteringUtil(
            mockModuleDir,
            CodeWhispererConstants.FeatureName.CODE_REVIEW
        )
        assertThat(utilWithUseCase).isNotNull
    }

    @Test
    fun `test initialization with null useCase`() {
        val utilWithNullUseCase = GitIgnoreFilteringUtil(mockModuleDir, null)
        assertThat(utilWithNullUseCase).isNotNull
    }

    @Test
    fun `test module directory is properly set`() {
        whenever(mockModuleDir.path).thenReturn("/test/path")

        val field = GitIgnoreFilteringUtil::class.java.getDeclaredField("moduleDir")
        field.isAccessible = true
        val actualModuleDir = field.get(gitIgnoreFilteringUtil) as VirtualFile

        assertThat(mockModuleDir).isEqualTo(actualModuleDir)
        assertThat("/test/path").isEqualTo(actualModuleDir.path)
    }
}
