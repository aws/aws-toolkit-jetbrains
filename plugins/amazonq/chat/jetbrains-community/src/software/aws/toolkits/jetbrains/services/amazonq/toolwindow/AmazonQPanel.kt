// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageTypeRegistry
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessageConnector
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.util.highlightCommand
import software.aws.toolkits.jetbrains.services.amazonq.webview.Browser
import software.aws.toolkits.jetbrains.services.amazonq.webview.BrowserConnector
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.EditorThemeAdapter
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.auth.isCodeScanAvailable
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.auth.isCodeTestAvailable
import software.aws.toolkits.jetbrains.services.amazonqDoc.auth.isDocAvailable
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.auth.isFeatureDevAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable
import java.util.concurrent.CompletableFuture
import javax.swing.JButton

class AmazonQPanel(val project: Project, private val scope: CoroutineScope) : Disposable {
    private val browser = CompletableFuture<Browser>()
    private val webviewContainer = Wrapper()
    private val appSource = AppSource()
    private val browserConnector = BrowserConnector(project = project)
    private val editorThemeAdapter = EditorThemeAdapter()
    private val appConnections = mutableListOf<AppConnection>()

    init {
        project.messageBus.connect().subscribe(
            AsyncChatUiListener.TOPIC,
            object : AsyncChatUiListener {
                override fun onChange(command: String) {
                    browser.get()?.postChat(command)
                }

                override fun onChange(command: FlareUiMessage) {
                    browser.get()?.postChat(command)
                }
            }
        )
    }

    val component = panel {
        row {
            cell(webviewContainer)
                .align(Align.FILL)
        }.resizableRow()

        // Button to show the web debugger for debugging the UI:
        if (isDeveloperMode()) {
            row {
                cell(
                    JButton("Show Web Debugger").apply {
                        addActionListener {
                            // Code to be executed when the button is clicked
                            // Add your logic here

                            browser.get().jcefBrowser.openDevtools()
                        }
                    },
                )
                    .align(AlignX.CENTER)
                    .align(AlignY.BOTTOM)
            }
        }
    }

    init {
        if (!JBCefApp.isSupported()) {
            // Fallback to an alternative browser-less solution
            if (AppMode.isRemoteDevHost()) {
                webviewContainer.add(JBTextArea("Amazon Q chat is not supported in remote dev environment."))
            } else {
                webviewContainer.add(JBTextArea("JCEF not supported"))
            }
            browser.complete(null)
        } else {
            val loadingPanel = JBLoadingPanel(null, this)
            val wrapper = Wrapper()
            loadingPanel.startLoading()

            webviewContainer.add(wrapper)
            wrapper.setContent(loadingPanel)

            ApplicationManager.getApplication().executeOnPooledThread {
                val webUri = runBlocking { service<ArtifactManager>().fetchArtifact(project).resolve("amazonq-ui.js").toUri() }
                loadingPanel.stopLoading()
                runInEdt {
                    browser.complete(
                        Browser(this, webUri, project).also {
                            wrapper.setContent(it.component())

                            initConnections()
                            connectUi(it)
                            connectApps(it)
                        }
                    )
                }
            }
        }
    }

    fun sendMessage(message: AmazonQMessage, tabType: String) {
        appConnections.filter { it.app.tabTypes.contains(tabType) }.forEach {
            scope.launch {
                it.messagesFromUiToApp.publish(message)
            }
        }
    }

    fun sendMessageAppToUi(message: AmazonQMessage, tabType: String) {
        appConnections.filter { it.app.tabTypes.contains(tabType) }.forEach {
            scope.launch {
                it.messagesFromAppToUi.publish(message)
            }
        }
    }

    private fun initConnections() {
        val apps = appSource.getApps(project)
        apps.forEach { app ->
            appConnections += AppConnection(
                app = app,
                messagesFromAppToUi = MessageConnector(),
                messagesFromUiToApp = MessageConnector(),
                messageTypeRegistry = MessageTypeRegistry(),
            )
        }
    }

    private fun connectApps(browser: Browser) {
        val fqnWebviewAdapter = FqnWebviewAdapter(browser.jcefBrowser, browserConnector)

        appConnections.forEach { connection ->
            val initContext = AmazonQAppInitContext(
                project = project,
                messagesFromAppToUi = connection.messagesFromAppToUi,
                messagesFromUiToApp = connection.messagesFromUiToApp,
                messageTypeRegistry = connection.messageTypeRegistry,
                fqnWebviewAdapter = fqnWebviewAdapter,
            )
            // Connect the app to the UI
            connection.app.init(initContext)
            // Dispose of the app when the tool window is disposed.
            Disposer.register(this, connection.app)
        }
    }

    private fun connectUi(browser: Browser) {
        browser.init(
            isCodeTransformAvailable = isCodeTransformAvailable(project),
            isFeatureDevAvailable = isFeatureDevAvailable(project),
            isCodeScanAvailable = isCodeScanAvailable(project),
            isCodeTestAvailable = isCodeTestAvailable(project),
            isDocAvailable = isDocAvailable(project),
            highlightCommand = highlightCommand(),
            activeProfile = QRegionProfileManager.getInstance().takeIf { it.shouldDisplayProfileInfo(project) }?.activeProfile(project)
        )

        scope.launch {
            // Pipe messages from the UI to the relevant apps and vice versa
            browserConnector.connect(
                browser = browser,
                connections = appConnections,
            )
        }

        scope.launch {
            // Update the theme in the UI when the IDE theme changes
            browserConnector.connectTheme(
                chatBrowser = browser.jcefBrowser.cefBrowser,
                themeSource = editorThemeAdapter.onThemeChange(),
            )
        }
    }

    override fun dispose() {
    }
}
