// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

import com.intellij.util.text.SemVer

data class SemanticVersion(private val version: SemVer) : Version {
    override fun displayValue(): String = version.toString()

    override fun compareTo(other: Version): Int = version.compareTo((other as SemanticVersion).version)
}
