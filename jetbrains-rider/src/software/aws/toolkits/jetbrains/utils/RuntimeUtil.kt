// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.lambda.validOrNull
import java.io.IOException

object RuntimeUtil {

    private val logger = getLogger<RuntimeUtil>()

    val DEFAULT_DOTNET_CORE_RUNTIME: Runtime = Runtime.DOTNETCORE2_1

    /**
     * Get information about current .NET runtime
     *
     * @return the [software.amazon.awssdk.services.lambda.model.Runtime] instance of current available runtime or
     *         [DEFAULT_DOTNET_CORE_RUNTIME] value if not defined.
     */
    fun getCurrentDotNetCoreRuntime(): Runtime {
        val runtimeList = try {
            java.lang.Runtime.getRuntime().exec("dotnet --list-runtimes").inputStream.bufferedReader().readLines()
        } catch (e: IOException) {
            logger.warn { "Error getting current runtime version: $e" }
            return DEFAULT_DOTNET_CORE_RUNTIME
        }

        val versionRegex = Regex("(\\d+.\\d+.\\d+)")
        val versions = runtimeList
            .filter { it.startsWith("Microsoft.NETCore.App") }
            .map { runtimeString ->
                val match = versionRegex.find(runtimeString) ?: return@map null
                match.groups[1]?.value ?: return@map null
            }
            .filterNotNull()

        val version = versions.maxBy { it } ?: return DEFAULT_DOTNET_CORE_RUNTIME

        return Runtime.fromValue("dotnetcore${version.split('.').take(2).joinToString(".")}").validOrNull
            ?: DEFAULT_DOTNET_CORE_RUNTIME
    }
}
