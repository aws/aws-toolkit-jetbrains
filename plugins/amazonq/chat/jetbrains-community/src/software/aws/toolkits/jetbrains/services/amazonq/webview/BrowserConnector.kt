// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.RunOnceUtil
import com.intellij.ui.jcef.JBCefJSQuery.Response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import software.aws.toolkits.jetbrains.services.amazonq.LoadModuleCompletion
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageSerializer
import software.aws.toolkits.jetbrains.services.amazonq.util.command
import software.aws.toolkits.jetbrains.services.amazonq.util.tabType
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.AmazonQTheme
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.ThemeBrowserAdapter
import software.aws.toolkits.jetbrains.settings.MeetQSettings
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.util.function.Function

class BrowserConnector(
    private val serializer: MessageSerializer = MessageSerializer.getInstance(),
    private val themeBrowserAdapter: ThemeBrowserAdapter = ThemeBrowserAdapter(),
) {
    var uiReady = CompletableDeferred<Boolean>()

    suspend fun connect(
        browser: Browser,
        connections: List<AppConnection>,
    ) = coroutineScope {
        // Send browser messages to the outbound publisher
        addMessageHook(browser)
            .onEach { json ->
                val node = serializer.toNode(json)
                when (node.command) {
                    "ui-is-ready" -> {
                        uiReady.complete(true)
                        RunOnceUtil.runOnceForApp("AmazonQ-UI-Ready") {
                            MeetQSettings.getInstance().reinvent2024OnboardingCount += 1
                        }

                        Telemetry.toolkit.didLoadModule.use {
                            // the Duration is usually 0 because it takes a few nanoseconds to load the module
                            // so when it's translated to millis it is returned as 0
                            it.module("Chat")
                            it.result(MetricResult.Succeeded)
                        }
                        LoadModuleCompletion.getInstance(null)?.resetTimer()
                    }

                    "disclaimer-acknowledged" -> {
                        MeetQSettings.getInstance().disclaimerAcknowledged = true
                    }

                    // some weird issue preventing deserialization from working
                    "open-user-guide" -> {
                        BrowserUtil.browse(node.get("userGuideLink").asText())
                    }
                    "send-telemetry" -> {
                        val source = node.get("source")
                        val module = node.get("module")
                        val trigger = node.get("trigger")

                        if (source != null) {
                            Telemetry.ui.click.use {
                                it.elementId(source.asText())
                            }
                        } else if (module != null && trigger != null) {
                            Telemetry.toolkit.willOpenModule.use {
                                it.module(module.asText())
                                it.source(trigger.asText())
                                it.result(MetricResult.Succeeded)
                            }
                        }
                    }
                }

                val tabType = node.tabType ?: return@onEach
                connections.filter { connection -> connection.app.tabTypes.contains(tabType) }.forEach { connection ->
                    launch {
                        val message = serializer.deserialize(node, connection.messageTypeRegistry)
                        connection.messagesFromUiToApp.publish(message)
                    }
                }
            }
            .launchIn(this)

        // Wait for UI ready before starting to send messages to the UI.
        uiReady.await()

        // Send inbound messages to the browser
        val inboundMessages = connections.map { it.messagesFromAppToUi.flow }.merge()
        inboundMessages
            .onEach { browser.post(serializer.serialize(it)) }
            .launchIn(this)
    }

    suspend fun connectTheme(
        chatBrowser: CefBrowser,
        loginBrowser: CefBrowser,
        themeSource: Flow<AmazonQTheme>,
    ) = coroutineScope {
        themeSource
            .distinctUntilChanged()
            .onEach {
                themeBrowserAdapter.updateLoginThemeInBrowser(loginBrowser, it)
                themeBrowserAdapter.updateThemeInBrowser(chatBrowser, it, uiReady)
            }
            .launchIn(this)
    }

    private fun addMessageHook(browser: Browser) = callbackFlow {
        val handler = Function<String, Response> {
            trySend(it)
            Response(null)
        }

        browser.receiveMessageQuery.addHandler(handler)

        awaitClose {
            browser.receiveMessageQuery.removeHandler(handler)
        }
    }
}
