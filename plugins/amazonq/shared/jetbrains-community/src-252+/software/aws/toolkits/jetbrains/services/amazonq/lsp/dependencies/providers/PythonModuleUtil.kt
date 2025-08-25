// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.providers
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager

fun getInstalledPackages(packageManager: PythonPackageManager): List<PythonPackage> =
    packageManager.listInstalledPackagesSnapshot()
