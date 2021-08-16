// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Version {
    override fun displayValue(): String = "$major.$minor.$patch"

    // TODO: Support pre-release
    override fun compareTo(other: Version): Int = COMPARATOR.compare(this, other as SemanticVersion)

    companion object {
        private val COMPARATOR = compareBy<SemanticVersion> { it.major }
            .thenBy { it.minor }
            .thenBy { it.patch }

        fun parse(version: String): SemanticVersion {
            val parts = version.split(".", limit = 3)
            if (parts.size != 3) {
                throw IllegalArgumentException("$version not in the format of MAJOR.MINOR.PATH")
            }

            val patch = parts[3].substringBefore("-").toInt()
            return SemanticVersion(parts[0].toInt(), parts[1].toInt(), patch)
        }
    }
}
