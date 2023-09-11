// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.caws

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class DevEnvStatusWatcherTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    // lastActivityTime tracks the inactivity time of the last editor activity recorded by JetBrains
    private var lastActivityTime: Long = 600

    // secondsRecordedSinceLastPrompt tracks the time since the last prompt for the user to continue their work
    private var secondsRecordedSinceLastPrompt: Long = 0

    @Test
    fun `If there has been no activity for a few seconds, it returns the number of seconds since last activity`() {
        val watcher = DevEnvStatusWatcher()
        val actualInactivityDuration = watcher.getActualInactivityDuration(lastActivityTime, secondsRecordedSinceLastPrompt)
        assertThat(actualInactivityDuration).isEqualTo(600)
    }

    @Test
    fun `If there is no editor activity after prompt, it returns the number of seconds since last activity`() {
        val watcher = DevEnvStatusWatcher()
        secondsRecordedSinceLastPrompt = lastActivityTime
        lastActivityTime = 640
        val actualInactivityDuration = watcher.getActualInactivityDuration(lastActivityTime, secondsRecordedSinceLastPrompt)
        assertThat(actualInactivityDuration).isEqualTo(40)
    }

    @Test
    fun `If there is editor activity after prompt, it returns the number of seconds since last activity`() {
        val watcher = DevEnvStatusWatcher()
        secondsRecordedSinceLastPrompt = 300
        lastActivityTime = 20
        val actualInactivityDuration = watcher.getActualInactivityDuration(lastActivityTime, secondsRecordedSinceLastPrompt)
        assertThat(actualInactivityDuration).isEqualTo(20)
    }
}
