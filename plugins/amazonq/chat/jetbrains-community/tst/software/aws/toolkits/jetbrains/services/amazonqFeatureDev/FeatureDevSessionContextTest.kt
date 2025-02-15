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
        val mediaFile = mock<VirtualFile>()
        whenever(mediaFile.extension).thenReturn("mp4")
        assertFalse(featureDevSessionContext.isFileExtensionAllowed(mediaFile))
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
    fun testZipProjectWithoutAutoDev() {
        checkZipProject(
            false,
            setOf(
                "src/MyClass.java",
                "gradlew",
                "gradlew.bat",
                "README.md",
                "gradle/wrapper/gradle-wrapper.properties",
                "builder/GetTestBuilder.java",
                "settings.gradle",
                "build.gradle",
                ".gitignore",
            )
        )
    }

    @Test
    fun testZipProjectWithAutoDev() {
        checkZipProject(
            true,
            setOf(
                "src/MyClass.java",
                "icons/menu.svg",
                "assets/header.jpg",
                "gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties",
                "images/logo.png",
                "builder/GetTestBuilder.java",
                "gradlew",
                "README.md",
                ".gitignore",
                "License.md",
                "output.bin",
                "archive.zip",
                "gradlew.bat",
                "license.txt",
                "build.gradle",
                "devfile.yaml",
                "settings.gradle"
            )
        )
    }

    fun checkZipProject(autoBuildEnabled: Boolean, expectedFiles: Set<String>) {
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
            "builder/GetTestBuilder.java", // check for false positives
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
            "dist/bundle.js",
            "gradle/wrapper/gradle-wrapper.jar",
            "devfile.yaml",
        )

        projectRule.fixture.addFileToModule(module, "large-file.txt", "loblob".repeat(1024 * 1024))

        val zipResult = featureDevSessionContext.getProjectZip(autoBuildEnabled)
        val zipPath = zipResult.payload.path

        val zippedFiles = mutableSetOf<String>()
        ZipFile(zipPath).use { zipFile ->
            for (entry in zipFile.entries()) {
                if (!entry.name.endsWith("/")) {
                    zippedFiles.add(entry.name)
                }
            }
        }

        assertEquals(zippedFiles, expectedFiles)
    }

    @Test
    fun testConvertGitIgnorePatternToRegex() {
        val sampleGitIgnorePatterns = listOf(".*", "build/", "*.txt", "*.png")
        val sampleFileNames = listOf(
            ".gitignore/",
            ".env/",
            "file.txt/",
            ".git/config/",
            "src/file.txt/",
            "build/",
            "build/output.jar/",
            "builds/",
            "mybuild/",
            "build.json/",
            "log.txt/",
            "file.txt.json/",
            "file.png/",
            "src/file.png/"
        )

        val patterns = sampleGitIgnorePatterns.map { pattern -> featureDevSessionContext.convertGitIgnorePatternToRegex(pattern).toRegex() }

        val matchedFiles = sampleFileNames.filter { fileName ->
            patterns.any { pattern ->
                pattern.matches(fileName)
            }
        }

        val expectedFilesToMatch =
            listOf(
                ".gitignore/",
                ".env/",
                "file.txt/",
                ".git/config/",
                "src/file.txt/",
                "build/",
                "build/output.jar/",
                "log.txt/",
                "file.png/",
                "src/file.png/"
            )

        assertEquals(expectedFilesToMatch, matchedFiles)
    }
}
