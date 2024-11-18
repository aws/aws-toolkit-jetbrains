// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateConfig
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.FeatureDevService
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
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
    private lateinit var config: SessionStateConfig

    @Before
    fun setUp() {
        val conversationId = "test-conversation"
        featureDevService = mock()
        whenever(featureDevService.project).thenReturn(projectRule.project)
        featureDevSessionContext = FeatureDevSessionContext(featureDevService.project, 1024)
        config = SessionStateConfig(conversationId, featureDevSessionContext, featureDevService)
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
        whenever(txtFile.extension).thenReturn("mp4")
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
            ".gradle/cached.jar",
            "src/MyClass.java",
            "gradlew",
            "gradlew.bat",
            "README.md",
            "settings.gradle",
            "build.gradle",
            "gradle/wrapper/gradle-wrapper.properties",
        )

        val isAutoBuildFeatureEnabled = CodeWhispererSettings.getInstance().isAutoBuildFeatureEnabled(config.repoContext.getWorkspaceRoot())
        val zipResult = featureDevSessionContext.getProjectZip(isAutoBuildFeatureEnabled = isAutoBuildFeatureEnabled)
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
            "settings.gradle",
            "build.gradle",
            "gradle/wrapper/gradle-wrapper.properties",
        )

        assertTrue(zippedFiles == expectedFiles)
    }
}
