// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.extensions.PluginId
import io.mockk.called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct

class PluginResolverTest {
    private lateinit var mockPluginUtil: PluginUtil

    @Before
    fun setup() {
        mockkStatic(PluginUtil::class, PluginManagerCore::class)
        mockPluginUtil = mockk<PluginUtil> {
            every { getCallerPlugin(any()) } returns mockk()
        }
        every { PluginUtil.getInstance() } returns mockPluginUtil
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun getsProductForAmazonQPlugin() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { name } returns "amazon.q"
        }
        every { PluginManagerCore.getPlugin(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver()

        assertEquals(AWSProduct.AMAZON_Q_FOR_JET_BRAINS, pluginResolver.product)
    }

    @Test
    fun getsToolkitProductByDefault() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { name } returns "amazon.foo"
        }
        every { PluginManagerCore.getPlugin(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver()

        assertEquals(AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS, pluginResolver.product)
    }

    @Test
    fun getsResolvedVersion() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { version } returns "1.2.3"
        }
        every { PluginManagerCore.getPlugin(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver()

        assertEquals("1.2.3", pluginResolver.version)
    }

    @Test
    fun getsUnresolvedVersionAsUnknown() {
        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { version } returns null
        }
        every { PluginManagerCore.getPlugin(any()) } returns pluginDescriptor

        val pluginResolver = PluginResolver()

        assertEquals("unknown", pluginResolver.version)
    }

    @Test
    fun stackTraceResolvesExpectedToolkitClass() {
        val mockStackTrace = arrayOf(
            StackTraceElement("foo", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("software.aws.toolkits.core.foo", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("software.aws.toolkits.plugins.amazonq.bar", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("bar", "mockMethod", "mockFile.kt", 1)
        )
        val pluginId = mockk<PluginId> {
            every { idString } returns "1234"
        }

        val pluginDescriptor = mockk<IdeaPluginDescriptor> {
            every { name } returns "amazon.q"
            every { version } returns "1.2.3"
        }
        every { PluginManagerCore.getPlugin(any()) } returns pluginDescriptor
        val pluginResolver = PluginResolver(mockStackTrace)
        every { mockPluginUtil.getCallerPlugin(2) } returns pluginId

        assertEquals(AWSProduct.AMAZON_Q_FOR_JET_BRAINS, pluginResolver.product)
        assertEquals("1.2.3", pluginResolver.version)

        verify {
            PluginManagerCore.getPlugin(pluginId)
            mockPluginUtil.getCallerPlugin(2)
        }
    }

    @Test
    fun stackTraceNoToolkitClassMatches() {
        val mockStackTrace = arrayOf(
            StackTraceElement("foo", "mockMethod", "mockFile.kt", 1),
            StackTraceElement("bar", "mockMethod", "mockFile.kt", 1)
        )
        val pluginResolver = PluginResolver(mockStackTrace)

        assertEquals(AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS, pluginResolver.product)
        assertEquals("unknown", pluginResolver.version)

        verify {
            PluginManagerCore.getPlugin(any())?.wasNot(called)
        }
    }
}
