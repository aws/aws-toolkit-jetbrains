// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

/**
 * Top level interface for different versioning schemes such as semantic version
 */
interface Version : Comparable<Version> {
    /**
     * @return Human-readable representation of the version
     */
    fun displayValue(): String
}

/**
 * @return true if the specified version is compatible with any of the specified version ranges. Always returns true if no ranges are specified.
 */
fun <T : Version> T.isValid(ranges: List<VersionRange<T>>): Validity {
    if (ranges.isEmpty()) {
        return Validity.Valid(this)
    }

    val minVersions = ranges.map { it.minVersion }.sortedDescending()
    if (minVersions.none { minVersion -> this >= minVersion }) {
        return Validity.VersionTooOld(minVersions.first()) // Sorted already so take the first which should be the greatest min version
    }

    val maxVersions = ranges.map { it.maxVersion }.sortedDescending()
    if (maxVersions.none { maxVersion -> this < maxVersion }) {
        return Validity.VersionTooNew(maxVersions.first()) // Sorted already so take the first which should be the greatest max version
    }

    return Validity.Valid(this)
}

/**
 * Represents a range of versions.
 *
 * @property minVersion The minimum version supported, inclusive.
 * @property maxVersion The maximum version supported, exclusive.
 */
data class VersionRange<T : Version>(val minVersion: T, val maxVersion: T)
infix fun <T : Version> T.until(that: T): VersionRange<T> = VersionRange(this, that)
