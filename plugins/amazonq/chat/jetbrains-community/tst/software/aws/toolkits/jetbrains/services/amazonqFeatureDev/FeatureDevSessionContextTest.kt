// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RuleChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.FeatureDevService
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addFileToModule
import java.util.zip.ZipFile

class FeatureDevSessionContextTest : FeatureDevTestBase(HeavyJavaCodeInsightTestFixtureRule()) {

    private fun addFilesToProjectModule(vararg path: String) {
        val module = projectRule.module
        path.forEach { projectRule.fixture.addFileToModule(module, it, it) }
    }

    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, disposableRule)
    private lateinit var featureDevSessionContext: FeatureDevSessionContext
    private lateinit var featureDevService: FeatureDevService

    @Before
    fun setUp() {
        featureDevService = mock()
        whenever(featureDevService.project).thenReturn(projectRule.project)
        featureDevSessionContext = FeatureDevSessionContext(featureDevService.project, 1024)
    }

    @Test
    fun testWithDirectory() {
        val directory = mock<VirtualFile>()
        whenever(directory.extension).thenReturn(null)
        whenever(directory.isDirectory).thenReturn(true)
        assertTrue(featureDevSessionContext.isFileExtensionAllowed(directory))
    }

    @Test
    fun testWithValidFile() {
        val ktFile = mock<VirtualFile>()
        whenever(ktFile.extension).thenReturn("kt")
        whenever(ktFile.path).thenReturn("code.kt")
        assertTrue(featureDevSessionContext.isFileExtensionAllowed(ktFile))
    }

    @Test
    fun testWithInvalidFile() {
        val txtFile = mock<VirtualFile>()
        whenever(txtFile.extension).thenReturn("txt")
        whenever(txtFile.path).thenReturn("file.txt")
        assertFalse(featureDevSessionContext.isFileExtensionAllowed(txtFile))
    }

    @Test
    fun testAllowedFilePath() {
        val allowedPaths = listOf("build.gradle", "gradle.properties", ".mvn/wrapper/maven-wrapper.properties")
        allowedPaths.forEach({
            val txtFile = mock<VirtualFile>()
            whenever(txtFile.path).thenReturn(it)
            whenever(txtFile.extension).thenReturn(it.split(".").last())
            assertTrue(featureDevSessionContext.isFileExtensionAllowed(txtFile))
        })
    }

    @Test
    fun testZipProject() {
        addFilesToProjectModule(
            ".gitignore",
            ".gradle/cached.jar",
            "src/MyClass.java",
            "gradlew",
            "gradlew.bat",
            "README.md",
            "settings.gradle",
            "build.gradle",
            "gradle/wrapper/gradle-wrapper.properties",
            "builder/GetTestBuilder.java", //check for false positives
            ".aws-sam/build/function1",
            ".gem/specs.rb",
            "archive.zip",
            "output.bin",
            "images/logo.png",
            "assets/header.jpg",
            "icons/menu.svg",
            "license.txt",
            "License.md",
            "node_modules/express",
            "build/outputs",
            "dist/bundle.js"
        )

        val zipResult = featureDevSessionContext.getProjectZip()
        val zipPath = zipResult.payload.path

        val zippedFiles = mutableSetOf<String>()
        ZipFile(zipPath).use { zipFile ->
            for (entry in zipFile.entries()) {
                if (!entry.name.endsWith("/")) {
                    zippedFiles.add(entry.name)
                }
            }
        }

        val expectedFiles = setOf(
            "src/MyClass.java",
            "gradlew",
            "gradlew.bat",
            "README.md",
            "gradle/wrapper/gradle-wrapper.properties",
            "builder/GetTestBuilder.java"
        )

        assertTrue(zippedFiles == expectedFiles)
    }

    @Test
    fun `test basic pattern conversion`() {
        val input = "*.txt"
        val expected = "(?:^|.*/?)[^/]*[^/]\\.txt(?:/.*)?\$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test pattern with special characters`() {
        val input = "test[abc].txt"
        val expected = "(?:^|.*/?)test\\[abc\\]\\.txt(?:/.*)?$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test pattern with double asterisk`() {
        val input = "**/build"
        val expected = "(?:^|.*/?).[^/]*[^/][^/]/build(?:/.*)?\$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test pattern starting with slash`() {
        val input = "/root/file.txt"
        val expected = "^root/file\\.txt(?:/.*)?$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test pattern ending with slash`() {
        val input = "build/"
        val expected = "(?:^|.*/?)build/.*"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test pattern with question mark`() {
        val input = "file?.txt"
        val expected = "(?:^|.*/?)file[^/]\\.txt(?:/.*)?$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test complex pattern with multiple special characters`() {
        val input = "**/test-[0-9]*.{java,kt}"
        val expected = "(?:^|.*/?).[^/]*[^/][^/]/test-\\[0-9\\][^/]*[^/]\\.\\{java\\,kt\\}(?:/.*)?\$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test empty pattern`() {
        val input = ""
        val expected = "(?:^|.*/?)(?:/.*)?$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }

    @Test
    fun `test pattern with all special regex characters`() {
        val input = ".$+()[]{}^|"
        val expected = "(?:^|.*/?)\\.\\\$\\+\\(\\)\\[\\]\\{\\}\\^\\|(?:/.*)?$"
        assertEquals(expected, featureDevSessionContext.convertGitIgnorePatternToRegex(input))
    }
}
