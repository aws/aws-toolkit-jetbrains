// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile

data class CustomerSelection(
    val configurationFile: VirtualFile, // always needed to ZIP module
    val sourceJavaVersion: JavaSdkVersion, // always needed, use default of JDK_8 for SQL conversions for startJob API call
    val targetJavaVersion: JavaSdkVersion = JavaSdkVersion.JDK_17,
    val sourceVendor: String = "ORACLE", // only one supported
    val targetVendor: String? = null,
    val sourceServerName: String? = null,
)
