// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RuleChain
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

class FeatureDevSessionContextTest : FeatureDevTestBase() {
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
        val allowedPaths = listOf("gradlew", "build.gradle", "gradle.properties", ".mvn/wrapper/maven-wrapper.properties")
        allowedPaths.forEach({
            val txtFile = mock<VirtualFile>()
            whenever(txtFile.path).thenReturn(it)
            assertTrue(featureDevSessionContext.isFileExtensionAllowed(txtFile))
        })
    }
}
