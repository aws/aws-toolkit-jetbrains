// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkit.core.lambda.LambdaRuntime
import software.aws.toolkit.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.core.IdBasedExtensionPoint
import software.aws.toolkits.jetbrains.services.lambda.completion.HandlerCompletion
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup

class HandlerPanelTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private val runtimeWithCompletion = LambdaRuntime.JAVA21

    @Before
    fun setUp() {
        val runtimeGroup = checkNotNull(runtimeWithCompletion.runtimeGroup)
        val extension = IdBasedExtensionPoint<HandlerCompletion>().apply {
            id = runtimeGroup.id
            implementationClass = FakeHandlerCompletion::class.java.name
            pluginDescriptor =
                DefaultPluginDescriptor(PluginId.getId("aws.toolkit.test"), HandlerPanelTest::class.java.classLoader)
        }
        ExtensionTestUtil.maskExtensions(HandlerCompletion.extensionPointName, listOf(extension), disposableRule.disposable)
    }

    @Test
    fun `handler set after switching to the completion field is kept once pending UI events run`() {
        runInEdtAndWait {
            val panel = HandlerPanel(projectRule.project)
            panel.setRuntime(runtimeWithCompletion.toSdkRuntime())
            panel.handler.text = "Assembly::Namespace.Function::Handler"

            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            assertThat(panel.handler.text).isEqualTo("Assembly::Namespace.Function::Handler")
        }
    }

    @Test
    fun `handler is carried over when the field switches between simple and completion`() {
        runInEdtAndWait {
            val panel = HandlerPanel(projectRule.project)
            panel.handler.text = "com.example.Handler::handleRequest"

            panel.setRuntime(runtimeWithCompletion.toSdkRuntime())
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertThat(panel.handler.text).isEqualTo("com.example.Handler::handleRequest")

            panel.setRuntime(null)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertThat(panel.handler.text).isEqualTo("com.example.Handler::handleRequest")
        }
    }
}

class FakeHandlerCompletion : HandlerCompletion {
    override fun getLookupElements(project: Project): Collection<LookupElement> = emptyList()

    override fun getPrefixMatcher(prefix: String): PrefixMatcher = PlainPrefixMatcher(prefix)
}
