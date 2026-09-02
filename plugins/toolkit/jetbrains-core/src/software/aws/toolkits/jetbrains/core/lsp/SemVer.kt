// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

internal object SemVerParser {
    fun parse(version: String): SemVerValue? {
        val cleaned = version.removePrefix("v").substringBefore('+')
        val hyphenIndex = cleaned.indexOf('-')
        val core = if (hyphenIndex >= 0) cleaned.substring(0, hyphenIndex) else cleaned
        val prereleasePart = if (hyphenIndex >= 0) cleaned.substring(hyphenIndex + 1) else null

        val parts = core.split('.')
        if (parts.size != 3) return null

        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        val prerelease = prereleasePart?.split('.')?.takeIf { identifiers ->
            identifiers.all { identifier -> identifier.isNotEmpty() }
        } ?: if (prereleasePart == null) emptyList() else return null

        return SemVerValue(major, minor, patch, prerelease)
    }

    fun parseRange(range: String): SemVerRange {
        val constraints = CONSTRAINT_PATTERN.findAll(range).map { match ->
            val op = match.groupValues[1]
            val ver = parse(match.groupValues[2])
                ?: error("Invalid version in range: ${match.groupValues[2]}")
            SemVerRange.Constraint(op, ver)
        }.toList()

        require(constraints.isNotEmpty()) { "Invalid version range: $range" }
        return SemVerRange(constraints)
    }

    private val CONSTRAINT_PATTERN = Regex("""(<=|>=|==|=|<|>)\s*(\S+)""")
}

data class SemVerValue(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<SemVerValue> {

    override fun compareTo(other: SemVerValue): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }

        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1

        for (i in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val thisId = prerelease.getOrNull(i)
            val otherId = other.prerelease.getOrNull(i)
            if (thisId == null) return -1
            if (otherId == null) return 1
            val thisNum = thisId.toIntOrNull()
            val otherNum = otherId.toIntOrNull()
            val result = when {
                thisNum != null && otherNum != null -> thisNum.compareTo(otherNum)
                thisNum != null -> -1
                otherNum != null -> 1
                else -> thisId.compareTo(otherId)
            }
            if (result != 0) return result
        }
        return 0
    }
}

class SemVerRange(val constraints: List<Constraint>) {
    data class Constraint(val op: String, val version: SemVerValue)

    fun satisfiedBy(version: SemVerValue): Boolean = constraints.all { constraint ->
        val coreVersion = SemVerValue(version.major, version.minor, version.patch)
        when (constraint.op) {
            "<" -> coreVersion < constraint.version
            "<=" -> coreVersion <= constraint.version
            ">" -> coreVersion > constraint.version
            ">=" -> coreVersion >= constraint.version
            "=", "==" -> version == constraint.version
            else -> throw IllegalArgumentException("Unknown operation ${constraint.op}")
        }
    }
}
