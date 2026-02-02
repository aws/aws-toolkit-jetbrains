// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.openapi.util.SystemInfo
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Detects legacy Linux environments that require older glibc-compatible builds.
 * Matches VSCode's CLibCheck behavior.
 */
internal class LegacyLinuxDetector {
    private val glibcxxThreshold = listOf(3, 4, 29) // GLIBCXX_3.4.29

    fun useLegacyLinux(): Boolean {
        if (!SystemInfo.isLinux) return false

        // Check for Snap environment
        if (System.getenv("SNAP") != null) {
            LOG.info { "Snap environment detected" }
            return true
        }

        val maxVersion = getMaxGlibcxxVersion() ?: return false
        val isLegacy = compareVersions(maxVersion, glibcxxThreshold) < 0

        if (isLegacy) {
            LOG.info { "GLIBCXX $maxVersion < 3.4.29, using legacy Linux build" }
        }
        return isLegacy
    }

    internal fun getMaxGlibcxxVersion(): List<Int>? {
        val libPath = findLibStdCpp() ?: return null

        return try {
            val process = ProcessBuilder("strings", libPath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(10, TimeUnit.SECONDS)

            parseGlibcxxVersions(output).maxWithOrNull(::compareVersions)
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to detect GLIBCXX version" }
            null
        }
    }

    internal fun parseGlibcxxVersions(output: String): List<List<Int>> =
        Regex("""GLIBCXX_(\d+\.\d+(?:\.\d+)?)""")
            .findAll(output)
            .map { it.groupValues[1].split(".").map(String::toInt) }
            .toList()

    private fun findLibStdCpp(): String? {
        val commonPaths = listOf(
            "/usr/lib/x86_64-linux-gnu/libstdc++.so.6",
            "/usr/lib64/libstdc++.so.6",
            "/usr/lib/libstdc++.so.6",
            "/lib/x86_64-linux-gnu/libstdc++.so.6",
        )
        return commonPaths.firstOrNull { File(it).exists() }
    }

    companion object {
        private val LOG = getLogger<LegacyLinuxDetector>()

        internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val partA = a.getOrElse(i) { 0 }
                val partB = b.getOrElse(i) { 0 }
                if (partA != partB) return partA.compareTo(partB)
            }
            return 0
        }
    }
}
