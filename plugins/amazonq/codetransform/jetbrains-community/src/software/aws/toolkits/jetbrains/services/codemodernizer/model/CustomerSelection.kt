// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

data class CustomerSelection(
    val configurationFile: VirtualFile? = null, // used to ZIP module
    val sourceJavaVersion: JavaSdkVersion, // always needed, use default of JDK_8 for SQL conversions for startJob API call
    val targetJavaVersion: JavaSdkVersion, // 17 or 21
    val sourceVendor: String = ORACLE_DB, // only one supported
    val targetVendor: String? = null,
    val sourceServerName: String? = null,
    val sqlMetadataZip: File? = null,
    // note: schema / customBuildCommand / customDependencyVersionsFile / targetJdkName / originalUploadZipPath
    // are passed in to CodeModernizerSessionContext separately,
    // *after* CodeModernizerSession is created
)
