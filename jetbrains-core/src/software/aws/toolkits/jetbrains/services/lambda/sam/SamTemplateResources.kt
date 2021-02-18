// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import software.amazon.awssdk.services.lambda.model.PackageType

sealed class Function(
    val codeLocation: String,
    val packageType: PackageType,
    val timeout: Int?,
    val memorySize: Int?
)

class LambdaFunction(
    fun runtime(): String = getScalarProperty("Runtime"), fun handler(): String = getScalarProperty("Handler")) : Function()

class ServerlessFunction(
    codeLocation: String,
    packageType: PackageType,
    timeout: Int?,
    memorySize: Int?,
    val dockerFile: String?
) : Function(codeLocation, packageType, timeout, memorySize)
