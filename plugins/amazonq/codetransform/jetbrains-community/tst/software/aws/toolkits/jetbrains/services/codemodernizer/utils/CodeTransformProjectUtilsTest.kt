// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.LanguageLevel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.any
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeWhispererCodeModernizerTestBase
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule

class CodeTransformProjectUtilsTest : CodeWhispererCodeModernizerTestBase(HeavyJavaCodeInsightTestFixtureRule()) {
    lateinit var javaSdkMock: JavaSdk
    lateinit var sdkMock: Sdk
    lateinit var projectRootManagerMock: ProjectRootManager
    lateinit var languageLevelProjectExtensionMock: LanguageLevelProjectExtension

    @Before
    override fun setup() {
        super.setup()
        Mockito.mockStatic(ProjectRootManager::class.java)
        Mockito.mockStatic(JavaSdkImpl::class.java)
        Mockito.mockStatic(LanguageLevelProjectExtension::class.java)

        javaSdkMock = Mockito.mock(JavaSdkImpl::class.java)
        sdkMock = Mockito.spy(Sdk::class.java)
        projectRootManagerMock = Mockito.mock(ProjectRootManager::class.java)
        languageLevelProjectExtensionMock = Mockito.mock(LanguageLevelProjectExtension::class.java)

        Mockito.`when`(ProjectRootManager.getInstance(project)).thenReturn(projectRootManagerMock)
        Mockito.`when`(LanguageLevelProjectExtension.getInstance(project)).thenReturn(languageLevelProjectExtensionMock)
    }

//    @Test
    fun `CodeTransformProjectUtils tryGetJdk() function returns project SDK when module language level is not set`() {
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(null)
        Mockito.`when`(javaSdkMock.getVersion(sdkMock)).thenReturn(JavaSdkVersion.JDK_21)
        val result = project.tryGetJdk()
        assertEquals(JavaSdkVersion.JDK_21, result)
    }

//    @Test
    fun `CodeTransformProjectUtils tryGetJdkLanguageLevelJdk() function returns null when language level is null`() {
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(null)
        val result = project.tryGetJdkLanguageLevelJdk()
        assertEquals(null, result)
    }

//    @Test
    fun `CodeTransformProjectUtils tryGetJdkLanguageLevelJdk() function returns language level version`() {
        Mockito.`when`(languageLevelProjectExtensionMock.languageLevel).thenReturn(LanguageLevel.JDK_1_8)
        Mockito.`when`(JavaSdkVersion.fromLanguageLevel(any())).thenReturn(JavaSdkVersion.JDK_1_8)
        val result = project.tryGetJdkLanguageLevelJdk()
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }
}
