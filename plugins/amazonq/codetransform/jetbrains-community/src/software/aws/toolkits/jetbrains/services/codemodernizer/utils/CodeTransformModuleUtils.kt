// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager

/**
 * @description Try to get the module SDK version and fallback to the project SDK version from the "project structure" settings.
 */
fun Module.tryGetJdk(project: Project): JavaSdkVersion? {
    val javaSdk = JavaSdkImpl.getInstance()
    val moduleRootManager = ModuleRootManager.getInstance(this)
    val moduleLanguageLevel = this.tryGetJdkLanguageLevelJdk() ?: javaSdk.getVersion(moduleRootManager.sdk!!)
    val sdk = moduleLanguageLevel ?: javaSdk.getVersion(ProjectRootManager.getInstance(project).projectSdk!!) ?: return null
    return sdk
}

/**
 * @description Try to get the project SDK "language level" version from the module "source" settings.
 * The default value should be set to the project settings SDK and language level, so if the parent SDK is set to
 * Java 17 and the language level is set to default. The value spit out will be JDK_17. If the parent language
 * level is set to JDK_1_8 then the default will be JDK_1_8 for the module. You can override all this at the
 * module "source" setting
 */
fun Module.tryGetJdkLanguageLevelJdk(): JavaSdkVersion? {
    val moduleRootManager = ModuleRootManager.getInstance(this)
    val languageLevelModuleExtension = moduleRootManager.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java)
    val languageLevel = languageLevelModuleExtension.languageLevel
    return JavaSdkVersion.fromLanguageLevel(languageLevel!!)
}
