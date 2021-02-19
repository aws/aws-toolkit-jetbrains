// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

interface ZipBased {
    val runtime: String?
    val handler: String?
}

interface ImageBased {
    val dockerFile: String?
    val codeLocation: String?
}

sealed class Function(
    val logicalName: String,
    val timeout: Int?,
    val memorySize: Int?
) {
    override fun toString() = logicalName
}

class ZipLambdaFunction(
    logicalName: String,
    timeout: Int?,
    memorySize: Int?,
    override val runtime: String?,
    override val handler: String?
) : Function(logicalName, timeout, memorySize), ZipBased

class ZipServerlessFunction(
    logicalName: String,
    timeout: Int?, memorySize: Int?,
    override val runtime: String?,
    override val handler: String?
) : Function(logicalName, timeout, memorySize), ZipBased

class ImageServerlessFunction(
    logicalName: String,
    timeout: Int?,
    memorySize: Int?,
    override val dockerFile: String?,
    override val codeLocation: String
) : Function(logicalName, timeout, memorySize), ImageBased
