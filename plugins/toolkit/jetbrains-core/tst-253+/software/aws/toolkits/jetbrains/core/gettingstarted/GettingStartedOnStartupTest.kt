// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.gettingstarted

import com.intellij.configurationStore.getPersistentStateComponentStorageLocation
import com.intellij.testFramework.HeavyPlatformTestCase
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import software.aws.toolkit.core.utils.deleteIfExists
import software.aws.toolkit.core.utils.touch
import software.aws.toolkit.jetbrains.core.credentials.MockCredentialManagerExtension
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.GettingStartedPanel
import software.aws.toolkits.jetbrains.settings.GettingStartedSettings

@ExperimentalCoroutinesApi
class GettingStartedOnStartupTest : HeavyPlatformTestCase() {
    private val credManagerExtension = MockCredentialManagerExtension()
    private val sut = GettingStartedOnStartup()

    override fun tearDown() {
        try {
            GettingStartedSettings.getInstance().shouldDisplayPage = true
            getPersistentStateComponentStorageLocation(GettingStartedSettings::class.java)?.deleteIfExists()
        } finally {
            super.tearDown()
        }
    }

    fun `test does not show screen if aws settings exist and has credentials`() {
        mockkObject(GettingStartedPanel.Companion)
        every { GettingStartedPanel.openPanel(any()) } returns Unit
        val fp = getPersistentStateComponentStorageLocation(GettingStartedSettings::class.java) ?: error(
            "could not determine persistent storage for GettingStartedSettings"
        )
        try {
            fp.touch()
            sut.runActivity(project)
        } finally {
            fp.deleteIfExists()
        }

        verify(exactly = 0) {
            GettingStartedPanel.openPanel(project)
        }
    }

    fun `test does not show screen if has previously shown screen`() {
        mockkObject(GettingStartedPanel.Companion)
        every { GettingStartedPanel.openPanel(any()) } returns Unit
        GettingStartedSettings.getInstance().shouldDisplayPage = false
        sut.runActivity(project)

        verify(exactly = 0) {
            GettingStartedPanel.openPanel(project)
        }
    }

    fun `test shows screen if aws settings exist and no credentials`() {
        mockkObject(GettingStartedPanel.Companion)
        every { GettingStartedPanel.openPanel(any()) } returns Unit
        credManagerExtension.clear()
        val fp = getPersistentStateComponentStorageLocation(GettingStartedSettings::class.java) ?: error(
            "could not determine persistent storage for GettingStartedSettings"
        )
        try {
            fp.touch()
            sut.runActivity(project)
        } finally {
            fp.deleteIfExists()
        }

        verify {
            GettingStartedPanel.openPanel(project, any(), any())
        }
    }

    fun `test shows screen on first install`() {
        mockkObject(GettingStartedPanel.Companion)
        every { GettingStartedPanel.openPanel(any()) } returns Unit
        sut.runActivity(project)

        verify {
            GettingStartedPanel.openPanel(project, any(), any())
        }
    }
}
