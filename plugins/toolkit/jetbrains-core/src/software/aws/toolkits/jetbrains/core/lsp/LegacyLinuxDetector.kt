// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Detects legacy Linux environments that require older glibc-compatible builds.
 */
internal object LegacyLinuxDetector {
    private val LOG = getLogger<LegacyLinuxDetector>()
    private val glibcxxThreshold = listOf(3, 4, 29) // GLIBCXX_3.4.29

    fun isLegacyLinux(): Boolean {
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

    private fun getMaxGlibcxxVersion(): List<Int>? {
        val libPath = findLibStdCpp() ?: return null

        val output = extractGlibcxxStringsUsingStringsCommand(libPath)
            ?: extractGlibcxxStringsFromBinaryFile(libPath)
        if (output == null) {
            LOG.warn { "Failed to read GLIBCXX versions from $libPath" }
            return null
        }

        return parseGlibcxxVersions(output).maxWithOrNull(::compareVersions)
    }

    private fun extractGlibcxxStringsUsingStringsCommand(libPath: String): String? = try {
        val output = runCommand(listOf("strings", libPath), 10, TimeUnit.SECONDS)
        if (output?.contains("GLIBCXX") == true) output else null
    } catch (_: Exception) {
        LOG.info { "strings command failed, trying binary read fallback" }
        null
    }

    private fun runCommand(command: List<String>, timeout: Long, unit: TimeUnit): String? {
        val outputFile = File.createTempFile("legacy-linux-detector-", ".out")
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start()

            if (!process.waitFor(timeout, unit)) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                null
            } else {
                outputFile.readText()
            }
        } finally {
            outputFile.delete()
        }
    }

    private fun extractGlibcxxStringsFromBinaryFile(libPath: String): String? = try {
        val bytes = File(libPath).readBytes()
        val content = String(bytes, Charsets.ISO_8859_1)
        val matches = Regex("""GLIBCXX_\d+\.\d+(?:\.\d+)?""").findAll(content)
        matches.joinToString("\n") { it.value }.ifEmpty { null }
    } catch (e: Exception) {
        LOG.warn(e) { "Failed to read binary at $libPath" }
        null
    }

    internal fun parseGlibcxxVersions(output: String): List<List<Int>> =
        Regex("""GLIBCXX_(\d+\.\d+(?:\.\d+)?)""")
            .findAll(output)
            .map { it.groupValues[1].split(".").map(String::toInt) }
            .toList()

    private fun findLibStdCpp(): String? {
        // Try ldconfig first (most reliable on standard Linux)
        findLibStdCppUsingLdconfig()?.let { return it }

        // Fallback to common paths
        val commonPaths = listOf(
            "/usr/lib/x86_64-linux-gnu/libstdc++.so.6",
            "/usr/lib64/libstdc++.so.6",
            "/usr/lib/libstdc++.so.6",
            "/lib/x86_64-linux-gnu/libstdc++.so.6",
        )
        return commonPaths.firstOrNull { File(it).exists() }
    }

    private fun findLibStdCppUsingLdconfig(): String? = try {
        val output = runCommand(listOf("/sbin/ldconfig", "-p"), 5, TimeUnit.SECONDS) ?: return null

        // Parse: "libstdc++.so.6 (libc6,x86-64) => /lib/x86_64-linux-gnu/libstdc++.so.6"
        output.lineSequence()
            .filter { it.contains("libstdc++.so.6") }
            .firstNotNullOfOrNull { line ->
                Regex("""=>\s+(.+)$""").find(line)?.groupValues?.get(1)?.trim()
            }
    } catch (_: Exception) {
        null
    }

    internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val partA = a.getOrElse(i) { 0 }
            val partB = b.getOrElse(i) { 0 }
            if (partA != partB) return partA.compareTo(partB)
        }
        return 0
    }
}
