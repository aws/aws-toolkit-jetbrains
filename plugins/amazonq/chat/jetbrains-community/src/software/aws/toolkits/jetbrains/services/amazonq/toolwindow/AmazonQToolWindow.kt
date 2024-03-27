// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageTypeRegistry
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessageConnector
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteraction
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteractionType
import software.aws.toolkits.jetbrains.services.amazonq.webview.BrowserConnector
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.EditorThemeAdapter
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.auth.isFeatureDevAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.auth.isCodeTransformAvailable
import javax.swing.JComponent

class AmazonQToolWindow @NonInjectable constructor(
    private val project: Project,
    private val appSource: AppSource,
    private val browserConnector: BrowserConnector,
    private val editorThemeAdapter: EditorThemeAdapter,
) : Disposable {

    private val panel = AmazonQPanel(parent = this)

    val component: JComponent
        get() = panel.component

    private val appConnections = mutableListOf<AppConnection>()

    private val scope = disposableCoroutineScope(this)

    constructor(project: Project) : this(
        project = project,
        appSource = AppSource(),
        browserConnector = BrowserConnector(),
        editorThemeAdapter = EditorThemeAdapter(),
    )

    init {
        initConnections()
        connectUi()
        connectApps()
    }

    private fun sendMessage(message: AmazonQMessage, tabType: String) {
        appConnections.filter { it.app.tabTypes.contains(tabType) }.forEach {
            scope.launch {
                it.messagesFromUiToApp.publish(message)
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

    private fun connectApps() {
        val browser = panel.browser ?: return

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

    private fun connectUi() {
        val browser = panel.browser ?: return

        browser.init(
            isCodeTransformAvailable = isCodeTransformAvailable(project),
            isFeatureDevAvailable = isFeatureDevAvailable(project)
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
                browser = browser,
                themeSource = editorThemeAdapter.onThemeChange(),
            )
        }
    }

    companion object {
        fun getInstance(project: Project): AmazonQToolWindow = project.service<AmazonQToolWindow>()

        fun getStarted(project: Project) {
            // Make sure the window is shown
            runInEdt {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID)
                toolWindow?.show()
            }

            // Send the interaction message
            val window = getInstance(project)
            window.sendMessage(OnboardingPageInteraction(OnboardingPageInteractionType.CwcButtonClick), "cwc")
        }
    }

    override fun dispose() {
        // Nothing to do
    }
}
