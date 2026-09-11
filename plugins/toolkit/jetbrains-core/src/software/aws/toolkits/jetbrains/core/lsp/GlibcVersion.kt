// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

/**
 * A glibc runtime version expressed as its `major.minor` components.
 *
 * This model is used to compare the configured Node.js runtime with the minimum version required
 * by native language-server dependencies.
 */
internal data class GlibcVersion(val major: Int, val minor: Int) : Comparable<GlibcVersion> {
    override fun compareTo(other: GlibcVersion): Int =
        compareValuesBy(this, other, GlibcVersion::major, GlibcVersion::minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        /** Minimum glibc required by the CloudFormation language server's native dependencies (for example LMDB). */
        val REQUIRED: GlibcVersion = GlibcVersion(2, 28)

        private val MAJOR_MINOR = Regex("""(\d+)\.(\d+)""")

        /**
         * Extracts the first `major.minor` version from strings such as `"2.28"`, `"glibc 2.26"`, or
         * `"ldd (GNU libc) 2.31"`. Returns `null` for blank input or text that carries no version (for
         * example a musl banner), so callers can treat an unknown libc safely.
         */
        fun parse(raw: String?): GlibcVersion? {
            if (raw.isNullOrBlank()) return null
            val match = MAJOR_MINOR.find(raw) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            return GlibcVersion(major, minor)
        }
    }
}
