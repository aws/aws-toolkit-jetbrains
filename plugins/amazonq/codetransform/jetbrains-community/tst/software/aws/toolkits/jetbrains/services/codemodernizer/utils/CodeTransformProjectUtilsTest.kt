// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil.getMockJdk21
import org.junit.Assert.assertEquals
import org.junit.Before
import org.mockito.Mockito
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeWhispererCodeModernizerTestBase
import kotlin.test.Test

class CodeTransformProjectUtilsTest : CodeWhispererCodeModernizerTestBase() {
    lateinit var projectRootManagerMock: ProjectRootManager
    lateinit var languageLevelProjectExtensionMock: LanguageLevelProjectExtension
    lateinit var sdkMock: Sdk

    @Before
    override fun setup() {
        super.setup()
        Mockito.mockStatic(LanguageLevelProjectExtension::class.java)

        sdkMock = getMockJdk21()
        languageLevelProjectExtensionMock = Mockito.mock(LanguageLevelProjectExtension::class.java)

        Mockito.`when`(LanguageLevelProjectExtension.getInstance(project)).thenReturn(languageLevelProjectExtensionMock)
    }

    @Test
    fun `CodeTransformProjectUtils tryGetJdk() function returns project SDK when module language level is not set`() {
        Mockito.mockStatic(ProjectRootManager::class.java)
        projectRootManagerMock = Mockito.mock(ProjectRootManager::class.java)
        Mockito.`when`(ProjectRootManager.getInstance(project)).thenReturn(projectRootManagerMock)
        Mockito.`when`(projectRootManagerMock.projectSdk).thenReturn(sdkMock)
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(null)
        val result = project.tryGetJdk()
        assertEquals(JavaSdkVersion.JDK_21, result)
    }

    @Test
    fun `CodeTransformProjectUtils tryGetJdk() function returns project SDK when module language level is set`() {
        Mockito.mockStatic(ProjectRootManager::class.java)
        projectRootManagerMock = Mockito.mock(ProjectRootManager::class.java)
        Mockito.`when`(ProjectRootManager.getInstance(project)).thenReturn(projectRootManagerMock)
        Mockito.`when`(projectRootManagerMock.projectSdk).thenReturn(sdkMock)
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(LanguageLevel.JDK_1_8)
        val result = project.tryGetJdk()
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }

    @Test
    fun `CodeTransformProjectUtils tryGetJdkLanguageLevelJdk() function returns null when language level is null`() {
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(null)
        val result = project.tryGetJdkLanguageLevelJdk()
        assertEquals(null, result)
    }

    @Test
    fun `CodeTransformProjectUtils tryGetJdkLanguageLevelJdk() function returns language level version`() {
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(LanguageLevel.JDK_1_8)
        val result = project.tryGetJdkLanguageLevelJdk()
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }
}
