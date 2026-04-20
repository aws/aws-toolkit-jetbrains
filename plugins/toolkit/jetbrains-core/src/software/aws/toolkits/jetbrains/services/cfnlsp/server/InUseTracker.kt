// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class InUseTracker {
    fun writeMarker(versionDir: Path, app: String) {
        try {
            val pid = ProcessHandle.current().pid()
            val marker = versionDir.resolve(".inuse.$pid")
            val content = """{"pid":$pid,"app":"$app","timestamp":${System.currentTimeMillis()}}"""
            val tmp = versionDir.resolve(".inuse.$pid.tmp")
            Files.writeString(tmp, content)
            Files.move(tmp, marker, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
        }
    }

    fun removeMarker(versionDir: Path) {
        try {
            val pid = ProcessHandle.current().pid()
            Files.deleteIfExists(versionDir.resolve(".inuse.$pid"))
        } catch (_: Exception) {
        }
    }

    fun isInUse(versionDir: Path): Boolean {
        try {
            return Files.list(versionDir).use { stream ->
                stream.filter { it.fileName.toString().startsWith(".inuse.") }
                    .anyMatch { marker ->
                        val pid = marker.fileName.toString().substringAfter(".inuse.").toLongOrNull()
                        pid != null && ProcessHandle.of(pid).isPresent
                    }
            }
        } catch (_: Exception) {
            return false
        }
    }

    fun cleanStaleMarkers(versionDir: Path) {
        try {
            Files.list(versionDir).use { stream ->
                stream.filter { it.fileName.toString().startsWith(".inuse.") }
                    .forEach { marker ->
                        val pid = marker.fileName.toString().substringAfter(".inuse.").toLongOrNull() ?: return@forEach
                        if (!ProcessHandle.of(pid).isPresent) {
                            try { Files.deleteIfExists(marker) } catch (_: Exception) {}
                        }
                    }
            }
        } catch (_: Exception) {
        }
    }
}
