// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.util.text.SemVer

interface Version : Comparable<Version> {
    fun displayValue(): String
}

data class SemanticVersion(private val version: SemVer) : Version {
    override fun displayValue(): String = version.toString()

    override fun compareTo(other: Version): Int = version.compareTo((other as SemanticVersion).version)
}

data class VersionRange<T>(val minVersion: Version, val maxVersion: Version) {
    operator fun contains(value: Version): Boolean = value >= minVersion && value < maxVersion
}

fun <T : Version> isVersionValid(version: T, ranges: List<VersionRange<T>>): Compatability {
    if (ranges.isEmpty()) {
        return Compatability.Valid
    }

    val minVersions = ranges.map { it.minVersion }.sortedDescending()
    if (minVersions.none { minVersion -> version >= minVersion }) {
        return Compatability.VersionTooOld(minVersions.first()) // Sorted already so take the first which should be the greatest min version
    }

    val maxVersions = ranges.map { it.maxVersion }.sortedDescending()
    if (maxVersions.none { maxVersion -> version < maxVersion }) {
        return Compatability.VersionTooNew(maxVersions.first()) // Sorted already so take the first which should be the greatest max version
    }

    return Compatability.Valid
}
