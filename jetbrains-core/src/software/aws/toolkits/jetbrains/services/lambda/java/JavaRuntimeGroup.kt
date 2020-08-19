// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.util.text.SemVer
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.RuntimeInfo
import software.aws.toolkits.jetbrains.services.lambda.SdkBasedRuntimeGroup

class JavaRuntimeGroup : SdkBasedRuntimeGroup() {
    override val id: String = BuiltInRuntimeGroups.Java
    override val languageIds = setOf(JavaLanguage.INSTANCE.id)

    override val supportedRuntimes: List<RuntimeInfo> = listOf(
        RuntimeInfo(Runtime.JAVA8),
        RuntimeInfo(Runtime.JAVA8_AL2, SemVer("1.1.0", 1, 1, 0)),
        RuntimeInfo(Runtime.JAVA11)
    )

    override fun runtimeForSdk(sdk: Sdk): Runtime? {
        if (sdk.sdkType is JavaSdkType) {
            val javaSdkVersion = JavaSdk.getInstance().getVersion(sdk) ?: return null
            return determineRuntimeForSdk(javaSdkVersion)
        }
        return null
    }

    private fun determineRuntimeForSdk(sdk: JavaSdkVersion) = when {
        sdk <= JavaSdkVersion.JDK_1_8 -> Runtime.JAVA8_AL2
        sdk <= JavaSdkVersion.JDK_11 -> Runtime.JAVA11
        else -> null
    }

    override fun getModuleType(): ModuleType<*> = JavaModuleType.getModuleType()

    override fun getIdeSdkType(): SdkType = JavaSdk.getInstance()

    override fun supportsSamBuild(): Boolean = true
}
