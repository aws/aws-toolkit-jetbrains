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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import software.amazon.q.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeWhispererCodeModernizerTestBase

class CodeTransformModuleUtilsTest : CodeWhispererCodeModernizerTestBase(HeavyJavaCodeInsightTestFixtureRule()) {
    lateinit var javaSdkMock: JavaSdk
    lateinit var sdkMock: Sdk
    lateinit var moduleRootManagerMock: ModuleRootManager
    lateinit var projectRootManagerMock: ProjectRootManager
    lateinit var languageLevelModuleExtensionMock: LanguageLevelModuleExtensionImpl

    @Before
    override fun setup() {
        super.setup()
        mockStatic(ModuleRootManager::class.java)
        mockStatic(ProjectRootManager::class.java)
        mockStatic(JavaSdkImpl::class.java)

        javaSdkMock = mock<JavaSdkImpl>()
        sdkMock = spy<Sdk>()
        moduleRootManagerMock = mock<ModuleRootManager>()
        projectRootManagerMock = mock<ProjectRootManager>()
        languageLevelModuleExtensionMock = mock<LanguageLevelModuleExtensionImpl>()

        whenever(ModuleRootManager.getInstance(module)).doReturn(moduleRootManagerMock)
        whenever(ProjectRootManager.getInstance(project)).doReturn(projectRootManagerMock)
        whenever(moduleRootManagerMock.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java)).doReturn(languageLevelModuleExtensionMock)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdk() function returns module language level when set`() {
        whenever(moduleRootManagerMock.sdk).doReturn(sdkMock)
        whenever(projectRootManagerMock.projectSdk).doReturn(null)
        whenever(languageLevelModuleExtensionMock.languageLevel).doReturn(LanguageLevel.JDK_1_8)
        whenever(javaSdkMock.getVersion(any())).doReturn(JavaSdkVersion.JDK_1_8)
        val result = module.tryGetJdk(project)
        assertThat(result).isEqualTo(JavaSdkVersion.JDK_1_8)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdk() function returns null when project and module sdk and language level is not set`() {
        whenever(languageLevelModuleExtensionMock.languageLevel).doReturn(null)
        whenever(javaSdkMock.getVersion(sdkMock)).doReturn(null)
        val result = module.tryGetJdk(project)
        assertThat(result).isNull()
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdkLanguageLevelJdk() function returns null when language level is null`() {
        whenever(languageLevelModuleExtensionMock.languageLevel).doReturn(null)
        whenever(javaSdkMock.getVersion(any())).doReturn(JavaSdkVersion.JDK_17)
        val result = module.tryGetJdkLanguageLevelJdk()
        assertThat(result).isNull()
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdkLanguageLevelJdk() function returns language level version`() {
        whenever(languageLevelModuleExtensionMock.languageLevel).doReturn(LanguageLevel.JDK_1_8)
        whenever(javaSdkMock.getVersion(any())).doReturn(JavaSdkVersion.JDK_1_8)
        val result = module.tryGetJdkLanguageLevelJdk()
        assertThat(result).isEqualTo(JavaSdkVersion.JDK_1_8)
    }
}
