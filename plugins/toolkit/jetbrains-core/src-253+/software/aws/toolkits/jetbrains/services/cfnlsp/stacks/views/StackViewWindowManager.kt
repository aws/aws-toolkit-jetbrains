// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class StackViewWindowManager(private val project: Project) {

    private val activeStacks = ConcurrentHashMap<String, StackViewPanelTabber>()
    private var listenerRegistered = false

    fun getTabberByName(stackName: String): StackViewPanelTabber? =
        activeStacks.values.firstOrNull { it.stackName == stackName }

    fun getOrOpenTabber(stackName: String): StackViewPanelTabber? {
        var tabber = getTabberByName(stackName)
        if (tabber == null) {
            try {
                val stackResult = CfnClientService.getInstance(project)
                    .describeStack(DescribeStackParams(stackName)).get()
                val stackId = stackResult?.stack?.stackId

                if (stackId == null) {
                    LOG.error { "Failed to get stackId for stack: $stackName" }
                    return null
                }

                openStack(stackName, stackId)
                tabber = getTabberByName(stackName)
            } catch (e: Exception) {
                LOG.error(e) { "Failed to ensure stack view is open for $stackName" }
                return null
            }
        }
        return tabber
    }

    fun openStack(stackName: String, stackId: String) {
        LOG.info { "openStack called for stackName: $stackName, stackId: $stackId" }

        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)

        if (toolWindow == null) {
            LOG.error { "Tool window '$TOOL_WINDOW_ID' not found" }
            return
        }

        // Make tool window available if it's not already
        if (!toolWindow.isAvailable) {
            toolWindow.setAvailable(true, null)
        }

        val contentManager = toolWindow.contentManager

        // Check if tab already exists
        val existingContent = contentManager.contents.find {
            it.getUserData(STACK_ARN_KEY) == stackId
        }

        if (existingContent != null) {
            contentManager.setSelectedContent(existingContent)
            runInEdt {
                toolWindow.show()
                toolWindow.activate(null, true)
            }
            return
        }

        if (contentManager.contentCount >= MAX_TABS) {
            removeOldestTab()
        }

        // Create new stack view
        val stackView = try {
            StackViewPanelTabber(project, stackName, stackId)
        } catch (e: Exception) {
            LOG.error(e) { "Failed to create StackDetailView" }
            return
        }

        val content = ContentFactory.getInstance().createContent(
            stackView.getComponent(),
            stackName, // Tab title is stack name, key is stack arn
            true
        )
        content.putUserData(STACK_ARN_KEY, stackId)

        setupContentManagerListener(contentManager)

        contentManager.addContent(content)
        contentManager.setSelectedContent(content)

        activeStacks[stackId] = stackView

        setupStackStatusListener(stackId, stackName, content)

        stackView.start()

        runInEdt {
            toolWindow.show()
            toolWindow.activate(null, true)
        }
    }

    private fun setupContentManagerListener(contentManager: ContentManager) {
        if (!listenerRegistered) {
            contentManager.addContentManagerListener(object : ContentManagerListener {
                override fun contentRemoved(event: ContentManagerEvent) {
                    val removedContent = event.content
                    val stackArn = removedContent.getUserData(STACK_ARN_KEY)
                    if (stackArn != null) {
                        LOG.info { "Tab closed by user, disposing resources for stack: $stackArn" }
                        activeStacks[stackArn]?.dispose()
                        activeStacks.remove(stackArn)
                    }
                }
            })
            listenerRegistered = true
        }
    }

    private fun setupStackStatusListener(stackId: String, stackName: String, content: Content) {
        val coordinator = StackViewCoordinator.getInstance(project)
        coordinator.addStatusListener(
            stackId,
            object : StackStatusListener {
                override fun onStackStatusUpdated() {
                    val stackState = coordinator.getStackState(stackId)
                    val status = stackState?.status
                    LOG.info { "Updating tab title for stack: $stackId, status: $status" }
                    runInEdt {
                        val displayName = if (status != null) {
                            "$stackName [$status]"
                        } else {
                            stackName
                        }
                        content.displayName = displayName
                    }
                }
            }
        )
    }

    private fun removeOldestTab() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return
        val contentManager = toolWindow.contentManager

        if (contentManager.contentCount > 0) {
            val oldestContent = contentManager.contents.first()
            val stackArn = oldestContent.getUserData(STACK_ARN_KEY)
            stackArn?.let {
                activeStacks[it]?.dispose()
                activeStacks.remove(it)
            }
            contentManager.removeContent(oldestContent, true)
        }
    }

    companion object {
        private val LOG = getLogger<StackViewWindowManager>()
        private val STACK_ARN_KEY = Key.create<String>("STACK_ARN")

        private const val MAX_TABS = 10
        private const val TOOL_WINDOW_ID = "cloudformation.lsp.stack.view"

        fun getInstance(project: Project): StackViewWindowManager = project.service()
    }
}
