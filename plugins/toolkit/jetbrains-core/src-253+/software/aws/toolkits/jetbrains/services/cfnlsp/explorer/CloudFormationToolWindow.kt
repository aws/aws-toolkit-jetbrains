// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkit.jetbrains.ToolkitPlaces
import software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.core.explorer.AbstractExplorerTreeToolWindow
import software.aws.toolkits.jetbrains.core.gettingstarted.requestCredentialsForExplorer
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnCredentialsService
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.jetbrains.ui.CenteredInfoPanel
import software.aws.toolkits.resources.AwsToolkitBundle.message
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
internal class CloudFormationToolWindow(private val project: Project) : AbstractExplorerTreeToolWindow(
    CloudFormationTreeStructure(project),
    initialTreeExpandDepth = 1
) {
    override val actionPlace = ToolkitPlaces.CFN_TOOL_WINDOW

    init {
        setupToolbar()
        StacksManager.getInstance(project).addListener {
            runInEdt {
                redrawContent()
            }
        }
        ChangeSetsManager.getInstance(project).addListener {
            runInEdt {
                redrawContent()
            }
        }
        subscribeToConnectionChanges()
        updateContent()
        ensureLspServerStarted()
    }

    private fun setupToolbar() {
        val toolbarGroup = DefaultActionGroup().apply {
            add(RegionComboBoxAction(project))
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, toolbarGroup, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
    }

    private fun subscribeToConnectionChanges() {
        project.messageBus.connect(this).subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    runInEdt { updateContent() }
                }
            }
        )
    }

    private fun updateContent() {
        if (CredentialManager.getInstance().getCredentialIdentifiers().isEmpty()) {
            setContent(
                CenteredInfoPanel().apply {
                    addLine(message("cloudformation.explorer.sign_in"))
                    addDefaultActionButton(message("gettingstarted.explorer.new.setup")) {
                        requestCredentialsForExplorer(project)
                    }
                }
            )
        } else {
            setContent(getTree())
        }
    }

    private fun ensureLspServerStarted() {
        LspServerManager.getInstance(project).ensureServerStarted(
            CfnLspServerSupportProvider::class.java,
            CfnLspServerDescriptor.getInstance(project)
        )
    }

    companion object {
        fun getInstance(project: Project): CloudFormationToolWindow = project.service()
    }
}

private class RegionComboBoxAction(private val project: Project) : ComboBoxAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun createPopupActionGroup(button: JComponent, context: com.intellij.openapi.actionSystem.DataContext): DefaultActionGroup {
        val regionManager = CloudFormationRegionManager.getInstance()
        val currentRegion = regionManager.getSelectedRegion(project)
        val regionProvider = AwsRegionProvider.getInstance()

        return DefaultActionGroup().apply {
            regionProvider.regions(regionProvider.defaultPartition().id).values
                .groupBy { it.category }
                .forEach { (category, categoryRegions) ->
                    addSeparator(category)
                    categoryRegions.sortedBy { it.displayName }.forEach { region ->
                        add(object : AnAction(region.displayName) {
                            override fun actionPerformed(e: AnActionEvent) {
                                if (region.id != currentRegion.id) {
                                    regionManager.setSelectedRegion(region)
                                    val stacksManager = StacksManager.getInstance(project)
                                    stacksManager.clear()
                                    CfnCredentialsService.getInstance(project).sendCredentials()
                                    // Reload stacks with new region
                                    stacksManager.reload()
                                }
                            }

                            override fun update(e: AnActionEvent) {
                                e.presentation.isEnabled = region.id != currentRegion.id
                            }

                            override fun getActionUpdateThread() = ActionUpdateThread.BGT
                        })
                    }
                }
        }
    }

    override fun update(e: AnActionEvent) {
        val region = CloudFormationRegionManager.getInstance().getSelectedRegion(e.project)
        e.presentation.text = region.id
    }
}
