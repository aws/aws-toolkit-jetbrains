// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugProcessStarter
import org.jetbrains.concurrency.Promise
import software.amazon.awssdk.services.lambda.model.PackageType
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.ImageDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamRunningState

abstract class DotnetImageDebugSupport : ImageDebugSupport {
    override fun supportsPathMappings(): Boolean = false
    override fun runtimeGroup(): RuntimeGroup = RuntimeGroup.getById(BuiltInRuntimeGroups.Dotnet)

    override suspend fun createDebugProcess(
        environment: ExecutionEnvironment,
        state: SamRunningState,
        debugHost: String,
        debugPorts: List<Int>
    ): XDebugProcessStarter? {
        throw UnsupportedOperationException("Use 'createDebugProcessAsync' instead")
    }

    override fun createDebugProcessAsync(
        environment: ExecutionEnvironment,
        state: SamRunningState,
        debugHost: String,
        debugPorts: List<Int>
    ): Promise<XDebugProcessStarter?> = DotnetDebugUtils.createDebugProcessAsync(environment, state, debugHost, debugPorts)

    override fun containerEnvVars(debugPorts: List<Int>): Map<String, String> = mapOf(
            "_AWS_LAMBDA_DOTNET_DEBUGGING" to "1"
        )
}

class Dotnet21ImageDebug : DotnetImageDebugSupport() {
    override val id: String = Runtime.DOTNETCORE2_1.toString()
    override fun displayName() = Runtime.DOTNETCORE2_1.toString().capitalize()
}

class Dotnet31ImageDebug : DotnetImageDebugSupport() {
    override val id: String = Runtime.DOTNETCORE3_1.toString()
    override fun displayName() = Runtime.DOTNETCORE3_1.toString().capitalize()
}
