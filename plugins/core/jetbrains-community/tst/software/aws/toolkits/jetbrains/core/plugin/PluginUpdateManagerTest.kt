// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.plugin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import io.mockk.every
import io.mockk.mockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.AwsToolkit.TOOLKIT_PLUGIN_ID
import software.aws.toolkits.jetbrains.settings.AwsSettings

class PluginUpdateManagerTest {
    val applicationRule = ApplicationRule()
    val disposableRule = DisposableRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(applicationRule, disposableRule)

    private lateinit var sut: PluginUpdateManager
    private val testIdeaPluginDescriptorToolkit = getPluginDescriptorForIdAndVersion(TOOLKIT_PLUGIN_ID, "1.84")
    private var isAutoUpdateEnabledDefault: Boolean = false

    @Before
    fun setup() {
        sut = PluginUpdateManager().let {
            Disposer.register(disposableRule.disposable, it)
            spy(it)
        }

        val downloaderSpy = mock<PluginDownloader>()
        downloaderSpy.stub {
            onGeneric {
                id
            } doAnswer { testIdeaPluginDescriptorToolkit.pluginId }
            onGeneric {
                pluginVersion
            } doAnswer { testIdeaPluginDescriptorToolkit.version }
            onGeneric {
                install()
            } doAnswer {}
        }

        mockkObject(PluginUpdateManager.Companion)
        every {
            PluginUpdateManager.getUpdateInfo()
        } returns listOf(downloaderSpy)

        isAutoUpdateEnabledDefault = AwsSettings.getInstance().isAutoUpdateEnabled
    }

    @After
    fun teardown() {
        tryOrNull {
            AwsSettings.getInstance().isAutoUpdateEnabled = isAutoUpdateEnabledDefault
        }
    }

    @Test
    fun `test getUpdate() should return null if aws toolkit download is not found`() {
        val testPluginDescriptor = getPluginDescriptorForIdAndVersion("test", "1.0")
        assertThat(PluginUpdateManager.getUpdate(testPluginDescriptor)).isNull()
    }

    @Test
    fun `test getUpdate() should return null if current version is same or newer`() {
        var testPluginDescriptorCurrentVersion = getPluginDescriptorForIdAndVersion(TOOLKIT_PLUGIN_ID, "1.84")
        assertThat(PluginUpdateManager.getUpdate(testPluginDescriptorCurrentVersion)).isNull()
        testPluginDescriptorCurrentVersion = getPluginDescriptorForIdAndVersion(TOOLKIT_PLUGIN_ID, "1.85")
        assertThat(PluginUpdateManager.getUpdate(testPluginDescriptorCurrentVersion)).isNull()
    }

    @Test
    fun `test getUpdate() should return toolkit if current version is older`() {
        val testPluginDescriptorCurrentVersion = getPluginDescriptorForIdAndVersion(TOOLKIT_PLUGIN_ID, "1.83")
        val update = PluginUpdateManager.getUpdate(testPluginDescriptorCurrentVersion)
        assertThat(update).isNotNull
        assertThat(update?.pluginVersion).isEqualTo("1.84")
        assertThat(update?.id.toString()).isEqualTo(TOOLKIT_PLUGIN_ID)
    }

    @Test
    fun `test auto update feature respects user setting`() {
        AwsSettings.getInstance().isAutoUpdateEnabled = false
        sut.scheduleAutoUpdate()
        runInEdt {
            verify(sut, never()).checkForUpdates(any(), any())
        }

        AwsSettings.getInstance().isAutoUpdateEnabled = true
        sut.scheduleAutoUpdate()
        runInEdt {
            verify(sut).checkForUpdates(any(), any())
        }
    }

    private fun getPluginDescriptorForIdAndVersion(id: String, version: String): IdeaPluginDescriptor {
        val mockDescriptor = mock<IdeaPluginDescriptor>()
        whenever(mockDescriptor.version).thenReturn(version)
        whenever(mockDescriptor.pluginId).thenReturn(PluginId.getId(id))
        return mockDescriptor
    }
}
