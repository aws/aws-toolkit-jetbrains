// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.ToolkitPlaces
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.explorer.AbstractExplorerTreeToolWindow
import software.aws.toolkits.jetbrains.core.gettingstarted.requestCredentialsForExplorer
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.jetbrains.ui.CenteredInfoPanel
import software.aws.toolkits.resources.AwsToolkitBundle.message

@Service(Service.Level.PROJECT)
internal class CloudFormationToolWindow(private val project: Project) :
    AbstractExplorerTreeToolWindow(
        CloudFormationTreeStructure(project),
        initialTreeExpandDepth = 0
    ),
    ConnectionSettingsStateChangeNotifier {
    override val actionPlace = ToolkitPlaces.CFN_TOOL_WINDOW

    init {
        val toolbarGroup = ActionManager.getInstance().getAction("aws.toolkit.cloudformation.toolbar")
        toolbar = ActionManager.getInstance().createActionToolbar(actionPlace, toolbarGroup as ActionGroup, true).apply {
            targetComponent = this@CloudFormationToolWindow
        }.component

        StacksManager.getInstance(project).addListener {
            runInEdt { redrawContent() }
        }
        ChangeSetsManager.getInstance(project).addListener {
            runInEdt { redrawContent() }
        }
        ResourceLoader.getInstance(project).addListener { _, _ ->
            runInEdt { redrawContent() }
        }
        ResourceTypesManager.getInstance(project).addListener {
            runInEdt { redrawContent() }
        }
        subscribeToConnectionChanges()
        updateContent()
    }

    private fun subscribeToConnectionChanges() {
        // Listen to connection state changes (for credential validation)
        project.messageBus.connect(this).subscribe(AwsConnectionManager.CONNECTION_SETTINGS_STATE_CHANGED, this)

        // Listen to active connection changes
        project.messageBus.connect(this).subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    runInEdt { updateContent() }
                }
            }
        )
    }

    override fun settingsStateChanged(newState: ConnectionState) {
        runInEdt { updateContent() }
    }

    private fun updateContent() {
        LOG.debug { "CloudFormationToolWindow updateContent() called" }
        val connectionManager = AwsConnectionManager.getInstance(project)
        when (val connectionState = connectionManager.connectionState) {
            is ConnectionState.ValidConnection -> {
                redrawContent()
            }
            is ConnectionState.ValidatingConnection -> {
                LOG.debug { "Validating connection, showing validation message" }
                setContent(
                    CenteredInfoPanel().apply {
                        addLine("Validating connection to AWS...")
                    }
                )
            }
            else -> {
                LOG.debug { "No valid connection found (state: $connectionState), showing sign-in panel" }
                setContent(
                    CenteredInfoPanel().apply {
                        addLine(message("cloudformation.explorer.sign_in"))
                        addDefaultActionButton(message("gettingstarted.explorer.new.setup")) {
                            requestCredentialsForExplorer(project)
                        }
                    }
                )
            }
        }
    }

    companion object {
        private val LOG = getLogger<CloudFormationToolWindow>()
        fun getInstance(project: Project): CloudFormationToolWindow = project.service()
    }
}
