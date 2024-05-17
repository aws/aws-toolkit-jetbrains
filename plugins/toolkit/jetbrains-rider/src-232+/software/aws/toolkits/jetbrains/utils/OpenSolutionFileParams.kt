// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import java.io.File

typealias OpenSolutionFileParams = com.jetbrains.rider.test.OpenSolutionParams
fun openSolutionFile(solutionDirName: String): File {
    val solutionFileName = when (solutionDirName) {
        "testData/solutions/EchoLambda6X" -> "EchoLambda.sln"
        "testData/solutions/ImageLambda6X" -> "ImageLambda.sln"
        else -> throw IllegalArgumentException("Unsupported solution directory: $solutionDirName")
    }
    return File("$solutionDirName/$solutionFileName")
}

const val OPEN_SOLUTION_DIR_NAME: String = "testData/solutions/SamHelloWorldApp/SamHelloWorldApp.sln"
