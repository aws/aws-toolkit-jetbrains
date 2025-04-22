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
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.core.notifications.NotificationPanel
import software.aws.toolkits.jetbrains.core.notifications.ProcessNotificationsBase
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.services.amazonq.QWebviewPanel
import software.aws.toolkits.jetbrains.services.amazonq.RefreshQChatPanelButtonPressedListener
import software.aws.toolkits.jetbrains.services.amazonq.gettingstarted.openMeetQPage
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.jetbrains.utils.isQWebviewsAvailable
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.FeatureId
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class AmazonQToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = BorderLayoutPanel()
        val qPanel = Wrapper()
        val notificationPanel = NotificationPanel()

        mainPanel.addToTop(notificationPanel)
        mainPanel.add(qPanel)
        val notifListener = ProcessNotificationsBase.getInstance(project)
        notifListener.addListenerForNotification { bannerContent ->
            runInEdt {
                notificationPanel.updateNotificationPanel(bannerContent)
            }
        }

        if (toolWindow is ToolWindowEx) {
            val actionManager = ActionManager.getInstance()
            toolWindow.setTitleActions(listOf(actionManager.getAction("aws.q.toolwindow.titleBar")))
        }
        val contentManager = toolWindow.contentManager

        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())?.let { qConn ->
                        openMeetQPage(project)
                    }
                    prepareChatContent(project, qPanel)
                }
            }
        )

        project.messageBus.connect(toolWindow.disposable).subscribe(
            RefreshQChatPanelButtonPressedListener.TOPIC,
            object : RefreshQChatPanelButtonPressedListener {
                override fun onRefresh() {
                    prepareChatContent(project, qPanel)
                }
            }
        )

        project.messageBus.connect(toolWindow.disposable).subscribe(
            BearerTokenProviderListener.TOPIC,
            object : BearerTokenProviderListener {
                override fun onChange(providerId: String, newScopes: List<String>?) {
                    if (ToolkitConnectionManager.getInstance(project).connectionStateForFeature(QConnection.getInstance()) == BearerTokenAuthState.AUTHORIZED) {
                        AmazonQToolWindow.getInstance(project).disposeAndRecreate()
                        prepareChatContent(project, qPanel)
                    }
                }
            }
        )

        project.messageBus.connect(toolWindow.disposable).subscribe(
            QRegionProfileSelectedListener.TOPIC,
            object : QRegionProfileSelectedListener {
                // note we name myProject intentionally ow it will shadow the "project" provided by the IDE
                override fun onProfileSelected(myProject: Project, profile: QRegionProfile?) {
                    if (project.isDisposed) return
                    AmazonQToolWindow.getInstance(project).disposeAndRecreate()
                    qPanel.setContent(AmazonQToolWindow.getInstance(project).component)
                }
            }
        )

        prepareChatContent(project, qPanel)

        val content = contentManager.factory.createContent(mainPanel, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }
        toolWindow.activate(null)
        contentManager.addContent(content)
    }

    private fun prepareChatContent(
        project: Project,
        qPanel: Wrapper,
    ) {
        /**
         * only render Q Chat when
         * 1. There is a Q connection
         * 2. Q connection is not expired
         * 3. User is not pending region profile selection
         */
        val component = if (isQConnected(project) && !isQExpired(project) && !QRegionProfileManager.getInstance().isPendingProfileSelection(project)) {
            AmazonQToolWindow.getInstance(project).component
        } else {
            QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.AmazonQ))
            QWebviewPanel.getInstance(project).component
        }
        runInEdt {
            qPanel.setContent(component)
        }
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

    override fun shouldBeAvailable(project: Project): Boolean = isQWebviewsAvailable()

    companion object {
        private val LOG = getLogger<AmazonQToolWindowFactory>()
        const val WINDOW_ID = AMAZON_Q_WINDOW_ID
        private const val MINIMUM_TOOLWINDOW_WIDTH = 325
    }
}
