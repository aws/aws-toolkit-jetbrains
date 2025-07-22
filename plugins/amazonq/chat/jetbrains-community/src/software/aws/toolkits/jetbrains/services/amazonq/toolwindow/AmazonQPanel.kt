// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageTypeRegistry
import software.aws.toolkits.jetbrains.services.amazonq.isQSupportedInThisVersion
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
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
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.resources.message
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO.read
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
            if (isRunningOnRemoteBackend()) {
                webviewContainer.add(JBTextArea("Amazon Q chat is not supported in this remote dev environment because it lacks JCEF webview support."))
            } else {
                webviewContainer.add(JBTextArea("JCEF not supported"))
            }
            browser.complete(null)
        } else if (!isQSupportedInThisVersion()) {
            webviewContainer.add(JBTextArea("${message("q.unavailable")}\n ${message("q.unavailable.node")}"))
            browser.complete(null)
        } else {
            val loadingPanel = if (isRunningOnRemoteBackend()) {
                JBLoadingPanel(null) {
                    ProgressBarLoadingDecorator(it, this, -1)
                }
            } else {
                JBLoadingPanel(null, this)
            }

            val wrapper = Wrapper()
            loadingPanel.startLoading()

            webviewContainer.add(wrapper)
            wrapper.setContent(loadingPanel)

            scope.launch {
                val mynahAsset = service<ArtifactManager>().fetchArtifact(project).resolve("amazonq-ui.js")
                // wait for server to be running
                AmazonQLspService.getInstance(project).instanceFlow.first()

                withContext(EDT) {
                    browser.complete(
                        Browser(this@AmazonQPanel, mynahAsset, project).also { browserInstance ->
                            wrapper.setContent(browserInstance.component())

                            // Add DropTarget to the browser component
                            // JCEF does not propagate OS-level dragenter, dragOver and drop into DOM.
                            // As an alternative, enabling the native drag in JCEF,
                            // and let the native handling the drop event, and update the UI through JS bridge.
                            val dropTarget = object : DropTarget() {
                                override fun drop(dtde: DropTargetDropEvent) {
                                    try {
                                        dtde.acceptDrop(dtde.dropAction)
                                        val transferable = dtde.transferable
                                        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                            val fileList = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>

                                            val errorMessages = mutableListOf<String>()
                                            val validImages = mutableListOf<File>()
                                            val allowedTypes = setOf("jpg", "jpeg", "png", "gif", "webp")
                                            val maxFileSize = 3.75 * 1024 * 1024 // 3.75MB in bytes
                                            val maxDimension = 8000

                                            for (file in fileList as List<File>) {
                                                val validationResult = validateImageFile(file, allowedTypes, maxFileSize, maxDimension)
                                                if (validationResult != null) {
                                                    errorMessages.add(validationResult)
                                                } else {
                                                    validImages.add(file)
                                                }
                                            }

                                            // File count restriction
                                            if (validImages.size > 20) {
                                                errorMessages.add("A maximum of 20 images can be added to a single message.")
                                                validImages.subList(20, validImages.size).clear()
                                            }

                                            val json = OBJECT_MAPPER.writeValueAsString(validImages)
                                            browserInstance.jcefBrowser.cefBrowser.executeJavaScript(
                                                "window.handleNativeDrop('$json')",
                                                browserInstance.jcefBrowser.cefBrowser.url,
                                                0
                                            )

                                            val errorJson = OBJECT_MAPPER.writeValueAsString(errorMessages)
                                            browserInstance.jcefBrowser.cefBrowser.executeJavaScript(
                                                "window.handleNativeNotify('$errorJson')",
                                                browserInstance.jcefBrowser.cefBrowser.url,
                                                0
                                            )
                                            dtde.dropComplete(true)
                                        } else {
                                            dtde.dropComplete(false)
                                        }
                                    } catch (e: Exception) {
                                        LOG.error { "Failed to handle file drop operation: ${e.message}" }
                                        dtde.dropComplete(false)
                                    }
                                }
                            }

                            // Set DropTarget on the browser component and its children
                            browserInstance.component()?.let { component ->
                                component.dropTarget = dropTarget
                                // Also try setting on parent if needed
                                component.parent?.dropTarget = dropTarget
                            }

                            initConnections()
                            connectUi(browserInstance)
                            connectApps(browserInstance)

                            loadingPanel.stopLoading()
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

    private fun validateImageFile(file: File, allowedTypes: Set<String>, maxFileSize: Double, maxDimension: Int): String? {
        val fileName = file.name
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (ext !in allowedTypes) {
            return "$fileName: File must be an image in JPEG, PNG, GIF, or WebP format."
        }

        if (file.length() > maxFileSize) {
            return "$fileName: Image must be no more than 3.75MB in size."
        }

        return try {
            val img = read(file)
            when {
                img == null -> "$fileName: File could not be read as an image."
                img.width > maxDimension -> "$fileName: Image must be no more than 8,000px in width."
                img.height > maxDimension -> "$fileName: Image must be no more than 8,000px in height."
                else -> null
            }
        } catch (e: Exception) {
            "$fileName: File could not be read as an image."
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQPanel>()
        private val OBJECT_MAPPER = jacksonObjectMapper()
    }

    override fun dispose() {
    }
}
