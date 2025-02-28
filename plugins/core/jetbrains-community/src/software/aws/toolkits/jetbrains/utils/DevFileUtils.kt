// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.vfs.VirtualFile

fun isDevFile(file: VirtualFile): Boolean = file.name.matches(Regex("devfile\\.ya?ml", RegexOption.IGNORE_CASE))
