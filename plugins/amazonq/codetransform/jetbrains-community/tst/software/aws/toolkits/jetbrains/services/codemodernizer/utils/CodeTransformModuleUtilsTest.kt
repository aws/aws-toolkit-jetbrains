// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.LanguageLevel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any

class CodeTransformModuleUtilsTest {
    lateinit var module: Module
    lateinit var project: Project
    lateinit var javaSdk: JavaSdk
    lateinit var moduleRootManager: ModuleRootManager
    lateinit var projectRootManager: ProjectRootManager
    lateinit var languageLevelModuleExtension: LanguageLevelModuleExtensionImpl

    @Before
    fun setup() {
        module = Mockito.mock(Module::class.java)
        project = Mockito.mock(Project::class.java)
        javaSdk = Mockito.mock(JavaSdkImpl::class.java)
        moduleRootManager = Mockito.mock(ModuleRootManager::class.java)
        projectRootManager = Mockito.mock(ProjectRootManager::class.java)
        languageLevelModuleExtension = Mockito.mock(LanguageLevelModuleExtensionImpl::class.java)
        Mockito.`when`(ModuleRootManager.getInstance(any())).thenReturn(moduleRootManager)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdk() function returns module language level when set`() {
        Mockito.`when`(module.tryGetJdkLanguageLevelJdk()).thenReturn(JavaSdkVersion.JDK_1_8)
        Mockito.`when`(moduleRootManager.sdk).thenReturn(null)
        Mockito.`when`(JavaSdkImpl.getInstance()).thenReturn(javaSdk)
        Mockito.`when`(ProjectRootManager.getInstance(project)).thenReturn(projectRootManager)
        Mockito.`when`(projectRootManager.projectSdk).thenReturn(null)
        val result = module.tryGetJdk(project)
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdk() function returns project SDK when module language level is not set`() {
        Mockito.`when`(module.tryGetJdkLanguageLevelJdk()).thenReturn(null)
        Mockito.`when`(moduleRootManager.sdk).thenReturn(null)
        Mockito.`when`(JavaSdkImpl.getInstance()).thenReturn(javaSdk)
        Mockito.`when`(javaSdk.getVersion(any())).thenReturn(JavaSdkVersion.JDK_17)
        Mockito.`when`(ProjectRootManager.getInstance(project)).thenReturn(projectRootManager)
        Mockito.`when`(projectRootManager.projectSdk).thenReturn(null)
        val result = module.tryGetJdk(project)
        assertEquals(JavaSdkVersion.JDK_17, result)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdkLanguageLevelJdk() function returns null when language level is null`() {
        Mockito.`when`(moduleRootManager.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java))
            .thenReturn(languageLevelModuleExtension)
        Mockito.`when`(languageLevelModuleExtension.languageLevel).thenReturn(null)
        val result = module.tryGetJdkLanguageLevelJdk()
        assertEquals(null, result)
    }

    @Test
    fun `CodeTransformModuleUtils tryGetJdkLanguageLevelJdk() function returns language level version`() {
        Mockito.`when`(moduleRootManager.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java))
            .thenReturn(languageLevelModuleExtension)
        Mockito.`when`(languageLevelModuleExtension.languageLevel).thenReturn(LanguageLevel.JDK_1_8)
        val result = module.tryGetJdkLanguageLevelJdk()
        assertEquals(JavaSdkVersion.JDK_1_8, result)
    }
}
