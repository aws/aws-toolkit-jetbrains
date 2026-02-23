// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

/**
 * Minimal semver implementation for comparing language server versions.
 * Handles formats: "1.4.0", "v1.4.0", "1.4.0-beta", "1.4.0-2030-alpha"
 */
internal data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }

        // No prerelease > has prerelease (1.0.0 > 1.0.0-beta)
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1

        // Compare prerelease identifiers left to right
        for (i in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease.getOrNull(i)
            val b = other.prerelease.getOrNull(i)
            if (a == null) return -1 // fewer fields = lower precedence
            if (b == null) return 1
            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()
            val cmp = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1 // numeric < string
                bNum != null -> 1
                else -> a.compareTo(b)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    companion object {
        fun parse(version: String): SemVer? {
            val cleaned = version.removePrefix("v")
            // Split "1.4.0-beta" into core="1.4.0" and pre="beta"
            val hyphenIdx = cleaned.indexOf('-')
            val core = if (hyphenIdx >= 0) cleaned.substring(0, hyphenIdx) else cleaned
            val prePart = if (hyphenIdx >= 0) cleaned.substring(hyphenIdx + 1) else null

            val parts = core.split('.')
            if (parts.size != 3) return null

            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null
            val prerelease = prePart?.split('.')?.flatMap { it.split('-') } ?: emptyList()

            return SemVer(major, minor, patch, prerelease)
        }
    }
}

/**
 * Simple version range supporting constraints like "<2.0.0".
 * Supports: <X.Y.Z, <=X.Y.Z, >=X.Y.Z, >X.Y.Z
 */
internal class SemVerRange private constructor(
    private val constraints: List<Constraint>,
) {
    private data class Constraint(val op: String, val version: SemVer)

    fun satisfiedBy(version: SemVer): Boolean =
        // For range checks, compare only major.minor.patch (ignore prerelease on the constraint bound)
        constraints.all { c ->
            val coreVersion = SemVer(version.major, version.minor, version.patch)
            when (c.op) {
                "<" -> coreVersion < c.version
                "<=" -> coreVersion <= c.version
                ">" -> coreVersion > c.version
                ">=" -> coreVersion >= c.version
                else -> false
            }
        }

    companion object {
        private val CONSTRAINT_PATTERN = Regex("""(<=?|>=?)\s*(\S+)""")

        fun parse(range: String): SemVerRange {
            val constraints = CONSTRAINT_PATTERN.findAll(range).map { match ->
                val op = match.groupValues[1]
                val ver = SemVer.parse(match.groupValues[2])
                    ?: error("Invalid version in range: ${match.groupValues[2]}")
                Constraint(op, ver)
            }.toList()

            require(constraints.isNotEmpty()) { "Invalid version range: $range" }
            return SemVerRange(constraints)
        }
    }
}
