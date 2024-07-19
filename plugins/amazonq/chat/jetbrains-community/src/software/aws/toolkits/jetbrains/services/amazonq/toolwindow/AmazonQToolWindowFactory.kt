// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.services.amazonq.QWebviewPanel
import software.aws.toolkits.jetbrains.services.amazonq.RefreshQChatPanelButtonPressedListener
import software.aws.toolkits.jetbrains.services.amazonq.gettingstarted.openMeetQPage
import software.aws.toolkits.jetbrains.services.amazonq.isQSupportedInThisVersion
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.FeatureId
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class AmazonQToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow is ToolWindowEx) {
            val actionManager = ActionManager.getInstance()
            toolWindow.setTitleActions(listOf(actionManager.getAction("aws.q.toolwindow.titleBar")))
        }
        val contentManager = toolWindow.contentManager

        project.messageBus.connect().subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    onConnectionChanged(project, newConnection, toolWindow)
                }
            }
        )

        project.messageBus.connect().subscribe(
            RefreshQChatPanelButtonPressedListener.TOPIC,
            object : RefreshQChatPanelButtonPressedListener {
                override fun onRefresh() {
                    runInEdt {
                        contentManager.removeAllContents(true)
                        prepareChatContent(project, contentManager)
                    }
                }
            }
        )

        project.messageBus.connect().subscribe(
            BearerTokenProviderListener.TOPIC,
            object : BearerTokenProviderListener {
                override fun onChange(providerId: String, newScopes: List<String>?) {
                    if (ToolkitConnectionManager.getInstance(project).connectionStateForFeature(QConnection.getInstance()) == BearerTokenAuthState.AUTHORIZED) {
                        contentManager.removeAllContents(true)
                        val content = contentManager.factory.createContent(AmazonQToolWindow.getInstance(project).component, null, false).also {
                            it.isCloseable = true
                            it.isPinnable = true
                        }

                        runInEdt {
                            contentManager.removeAllContents(true)
                            contentManager.addContent(content)
                        }
                    }
                }
            }
        )

        val content = prepareChatContent(project, contentManager)

        toolWindow.activate(null)
        contentManager.setSelectedContent(content)
    }

    private fun prepareChatContent(
        project: Project,
        contentManager: ContentManager
    ): Content {
        val component = if (isQConnected(project) && !isQExpired(project)) {
            AmazonQToolWindow.getInstance(project).component
        } else {
            QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.Q))
            QWebviewPanel.getInstance(project).component
        }

        val content = contentManager.factory.createContent(component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }
        contentManager.addContent(content)
        return content
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = message("q.window.title")
        toolWindow.component.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    val newWidth = e.component.width
                    if (newWidth >= MINIMUM_TOOLWINDOW_WIDTH) return
                    LOG.debug {
                        "Amazon Q Tool window stretched to a width less than the minimum allowed width, resizing to the minimum allowed width"
                    }
                    (toolWindow as ToolWindowEx).stretchWidth(MINIMUM_TOOLWINDOW_WIDTH - newWidth)
                }
            }
        )
    }

    override fun shouldBeAvailable(project: Project): Boolean = !isRunningOnRemoteBackend() && isQSupportedInThisVersion()

    private fun onConnectionChanged(project: Project, newConnection: ToolkitConnection?, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val isNewConnectionForQ = newConnection?.let {
            (it as? AwsBearerTokenConnection)?.let { conn ->
                val scopeShouldHave = Q_SCOPES

                LOG.debug { "newConnection: ${conn.id}; scope: ${conn.scopes}; scope must-have: $scopeShouldHave" }

                scopeShouldHave.all { s -> s in conn.scopes }
            } ?: false
        } ?: false

        if (isNewConnectionForQ) {
            openMeetQPage(project)
        }

        QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.Q))

        // isQConnected alone is not robust and there is race condition (read/update connection states)
        val component = if (isNewConnectionForQ || (isQConnected(project) && !isQExpired(project))) {
            LOG.debug { "returning Q-chat window; isQConnection=$isNewConnectionForQ; hasPinnedConnection=$isNewConnectionForQ" }
            AmazonQToolWindow.getInstance(project).component
        } else {
            LOG.debug { "returning login window; no Q connection found" }
            QWebviewPanel.getInstance(project).component
        }

        val content = contentManager.factory.createContent(component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }

        runInEdt {
            contentManager.removeAllContents(true)
            contentManager.addContent(content)
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQToolWindowFactory>()
        const val WINDOW_ID = AMAZON_Q_WINDOW_ID
        private const val MINIMUM_TOOLWINDOW_WIDTH = 325
    }
}
