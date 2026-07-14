// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class InUseTrackerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val tracker = InUseTracker()

    @Test
    fun `writeMarker creates pid-scoped marker file in version dir`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        val pid = ProcessHandle.current().pid()

        tracker.writeMarker(versionDir, "aws-toolkit-jetbrains")

        val marker = versionDir.resolve(".inuse.$pid")
        assertThat(Files.exists(marker)).isTrue()
        assertThat(Files.readString(marker)).contains("\"pid\":$pid", "\"app\":\"aws-toolkit-jetbrains\"")
    }

    @Test
    fun `isInUse returns true when current process has written marker`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        tracker.writeMarker(versionDir, "app")

        assertThat(tracker.isInUse(versionDir)).isTrue()
    }

    @Test
    fun `isInUse returns false when only stale pid markers present`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        Files.writeString(versionDir.resolve(".inuse.$DEAD_PID"), "{}")

        assertThat(tracker.isInUse(versionDir)).isFalse()
    }

    @Test
    fun `isInUse returns false when no markers exist`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        assertThat(tracker.isInUse(versionDir)).isFalse()
    }

    @Test
    fun `removeMarker deletes only current process marker`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        val otherMarker = versionDir.resolve(".inuse.$DEAD_PID")
        Files.writeString(otherMarker, "{}")
        tracker.writeMarker(versionDir, "app")

        tracker.removeMarker(versionDir)

        val ownMarker = versionDir.resolve(".inuse.${ProcessHandle.current().pid()}")
        assertThat(Files.exists(ownMarker)).isFalse()
        assertThat(Files.exists(otherMarker)).isTrue()
    }

    @Test
    fun `cleanStaleMarkers removes markers for dead pids but keeps live ones`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        val staleMarker = versionDir.resolve(".inuse.$DEAD_PID")
        Files.writeString(staleMarker, "{}")
        tracker.writeMarker(versionDir, "app")
        val liveMarker = versionDir.resolve(".inuse.${ProcessHandle.current().pid()}")

        tracker.cleanStaleMarkers(versionDir)

        assertThat(Files.exists(staleMarker)).isFalse()
        assertThat(Files.exists(liveMarker)).isTrue()
    }

    @Test
    fun `cleanStaleMarkers ignores files without pid suffix`() {
        val versionDir = tempFolder.newFolder("1.4.0").toPath()
        val garbage = versionDir.resolve(".inuse.not-a-pid")
        Files.writeString(garbage, "{}")

        tracker.cleanStaleMarkers(versionDir)

        assertThat(Files.exists(garbage)).isTrue()
    }

    companion object {
        // Max pid on Linux is 2^22; on macOS 99999. This value is guaranteed unused.
        private const val DEAD_PID = 2_147_483_646L
    }
}
