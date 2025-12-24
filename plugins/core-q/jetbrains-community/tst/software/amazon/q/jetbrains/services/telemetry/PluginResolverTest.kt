// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.services.telemetry

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.amazon.q.jetbrains.services.telemetry.PluginResolver

@ExtendWith(MockKExtension::class)
class PluginResolverTest {
    @BeforeEach
    fun setup() {
        PluginResolver.setThreadLocal(null)
        mockkStatic(PluginManagerCore::class)
    }

    @Test
    fun getsProductForAmazonQPlugin() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { pluginId.idString } returns "amazon.q"
        }
        every { PluginManagerCore.getPluginDescriptorOrPlatformByClassName(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver.fromCurrentThread()

        assertThat(pluginResolver.product).isEqualTo(AWSProduct.AMAZON_Q_FOR_JET_BRAINS)
    }

    @Test
    fun getsToolkitProductByDefault() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { pluginId.idString } returns "amazon.foo"
        }
        every { PluginManagerCore.getPluginDescriptorOrPlatformByClassName(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver.fromCurrentThread()

        assertThat(pluginResolver.product).isEqualTo(AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS)
    }

    @Test
    fun getsResolvedVersion() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { version } returns "1.2.3"
        }
        every { PluginManagerCore.getPluginDescriptorOrPlatformByClassName(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver.fromCurrentThread()

        assertThat(pluginResolver.version).isEqualTo("1.2.3")
    }

    @Test
    fun getsUnresolvedVersionAsUnknown() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { version } returns null
        }
        every { PluginManagerCore.getPluginDescriptorOrPlatformByClassName(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver.fromCurrentThread()

        assertThat(pluginResolver.version).isEqualTo("unknown")
    }

    @Test
    fun stackTraceResolvesExpectedToolkitClass() {
        val mockStackTrace = arrayOf(
            StackTraceElement("foo", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("software.aws.toolkits.core.foo", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("software.aws.toolkits.plugins.amazonq.bar", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("bar", "mockMethod", "mockFile.kt", 1)
        )

        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { pluginId.idString } returns "amazon.q"
            every { version } returns "1.2.3"
        }
        val pluginResolver = PluginResolver.fromStackTrace(mockStackTrace)
        every { PluginManagerCore.getPluginDescriptorOrPlatformByClassName(any()) } returns pluginDescriptor

        assertThat(pluginResolver.product).isEqualTo(AWSProduct.AMAZON_Q_FOR_JET_BRAINS)
        assertThat(pluginResolver.version).isEqualTo("1.2.3")

        verify {
            PluginManagerCore.getPluginDescriptorOrPlatformByClassName("software.aws.toolkits.plugins.amazonq.bar")
        }
    }

    @Test
    fun stackTraceNoToolkitClassMatches() {
        val mockStackTrace = arrayOf(
            StackTraceElement("foo", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("bar", "mockMethod", "mockFile.kt", 1)
        )
        val pluginResolver = PluginResolver.fromStackTrace(mockStackTrace)

        assertThat(pluginResolver.product).isEqualTo(AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS)
        assertThat(pluginResolver.version).isEqualTo("unknown")

        verify(exactly = 0) {
            PluginManagerCore.getPlugin(any())
        }
    }
}
