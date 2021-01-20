// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.rider.ideaInterop.fileTypes.csharp.CSharpLanguage
import com.jetbrains.rider.ideaInterop.fileTypes.vb.VbLanguage
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.lambda.LambdaRuntime
import software.aws.toolkits.core.lambda.validOrNull
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.SdkBasedRuntimeGroup
import software.aws.toolkits.jetbrains.utils.DotNetRuntimeUtils

class DotNetRuntimeGroup : SdkBasedRuntimeGroup() {
    override val id: String = BuiltInRuntimeGroups.Dotnet
    override val supportsPathMappings: Boolean = false

    override val languageIds: Set<String> = setOf(
        CSharpLanguage.id,
        VbLanguage.id
    )
    override val supportedRuntimes = listOf(
        LambdaRuntime.DOTNETCORE2_1,
        LambdaRuntime.DOTNETCORE3_1,
        LambdaRuntime.DOTNET5_0
    )

    override fun runtimeForSdk(sdk: Sdk): Runtime? = null

    override fun determineRuntime(project: Project): Runtime? = DotNetRuntimeUtils.getCurrentDotNetCoreRuntime().validOrNull
}
