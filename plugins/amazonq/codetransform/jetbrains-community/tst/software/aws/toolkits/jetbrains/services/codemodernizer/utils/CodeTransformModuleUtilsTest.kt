// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.LanguageLevel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeWhispererCodeModernizerTestBase
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule

class CodeTransformModuleUtilsTest : CodeWhispererCodeModernizerTestBase(HeavyJavaCodeInsightTestFixtureRule()) {
    lateinit var javaSdkMock: JavaSdk
    lateinit var sdkMock: Sdk
    lateinit var moduleRootManagerMock: ModuleRootManager
    lateinit var projectRootManagerMock: ProjectRootManager
    lateinit var languageLevelModuleExtensionMock: LanguageLevelModuleExtensionImpl

    @Before
    override fun setup() {
        super.setup()
        Mockito.mockStatic(ModuleRootManager::class.java)
        Mockito.mockStatic(ProjectRootManager::class.java)
        Mockito.mockStatic(JavaSdkImpl::class.java)

        javaSdkMock = Mockito.mock(JavaSdkImpl::class.java)
        sdkMock = Mockito.mock(Sdk::class.java)
        moduleRootManagerMock = Mockito.mock(ModuleRootManager::class.java)
        projectRootManagerMock = Mockito.mock(ProjectRootManager::class.java)
        languageLevelModuleExtensionMock = Mockito.mock(LanguageLevelModuleExtensionImpl::class.java)

        Mockito.`when`(ModuleRootManager.getInstance(module)).thenReturn(moduleRootManagerMock)
        Mockito.`when`(ProjectRootManager.getInstance(project)).thenReturn(projectRootManagerMock)
        Mockito.`when`(moduleRootManagerMock.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java))
            .thenReturn(languageLevelModuleExtensionMock)
        Mockito.`when`(moduleRootManagerMock.sdk).thenReturn(sdkMock)
        Mockito.`when`(projectRootManagerMock.projectSdk).thenReturn(sdkMock)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdk() function returns module language level when set`() {
        Mockito.`when`(languageLevelModuleExtensionMock.languageLevel).thenReturn(LanguageLevel.JDK_1_8)
        Mockito.`when`(javaSdkMock.getVersion(any())).thenReturn(JavaSdkVersion.JDK_1_8)
        val result = module.tryGetJdk(project)
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdk() function returns project SDK when module language level is not set`() {
        Mockito.`when`(languageLevelModuleExtensionMock.languageLevel).thenReturn(null)
        Mockito.`when`(javaSdkMock.getVersion(any())).thenReturn(JavaSdkVersion.JDK_17)
        val result = module.tryGetJdk(project)
        assertEquals(JavaSdkVersion.JDK_17, result)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdkLanguageLevelJdk() function returns null when language level is null`() {
        Mockito.`when`(languageLevelModuleExtensionMock.languageLevel).thenReturn(null)
        Mockito.`when`(javaSdkMock.getVersion(any())).thenReturn(JavaSdkVersion.JDK_17)
        val result = module.tryGetJdkLanguageLevelJdk()
        assertEquals(null, result)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdkLanguageLevelJdk() function returns language level version`() {
        Mockito.`when`(languageLevelModuleExtensionMock.languageLevel).thenReturn(LanguageLevel.JDK_1_8)
        Mockito.`when`(javaSdkMock.getVersion(any())).thenReturn(JavaSdkVersion.JDK_1_8)
        val result = module.tryGetJdkLanguageLevelJdk()
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }
}
