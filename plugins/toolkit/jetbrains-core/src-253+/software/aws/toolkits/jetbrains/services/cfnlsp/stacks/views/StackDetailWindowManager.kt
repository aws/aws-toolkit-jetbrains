// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import software.aws.toolkit.core.utils.getLogger
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class StackDetailWindowManager(private val project: Project) {

    private val activeStacks = ConcurrentHashMap<String, StackDetailView>()
    private val maxTabs = 10

    fun openStack(stackName: String, stackId: String) {
        LOG.info("StackDetailWindowManager.openStack called for: $stackName (ARN: $stackId)")
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("cloudformation.lsp.stack.view")
        
        if (toolWindow == null) {
            LOG.error("Tool window 'cloudformation.lsp.stack.view' not found")
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
            LOG.info("Tab already exists for stack $stackId, activating")
            contentManager.setSelectedContent(existingContent)
            runInEdt {
                toolWindow.show()
                toolWindow.activate(null, true)
            }
            return
        }

        // Enforce tab limit
        if (contentManager.contentCount >= maxTabs) {
            LOG.info("Tab limit reached, removing oldest tab")
            removeOldestTab()
        }

        // Create new stack view
        val stackView = try {
            StackDetailView(project, stackName, stackId)
        } catch (e: Exception) {
            LOG.error("Failed to create StackDetailView", e)
            return
        }

        // Create new tab
        val content = ContentFactory.getInstance().createContent(
            stackView.getComponent(),
            stackName, // Tab title is stack name
            true // Closeable
        )
        content.putUserData(STACK_ARN_KEY, stackId)
        
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        
        activeStacks[stackId] = stackView
        stackView.start()

        runInEdt {
            toolWindow.show()
            toolWindow.activate(null, true)
            LOG.info("New tab created and activated for stack $stackId")
        }
    }

    fun closeStack(stackArn: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("cloudformation.lsp.stack.view") ?: return
        val contentManager = toolWindow.contentManager

        val content = contentManager.contents.find { 
            it.getUserData(STACK_ARN_KEY) == stackArn 
        }
        
        content?.let {
            activeStacks[stackArn]?.dispose()
            activeStacks.remove(stackArn)
            contentManager.removeContent(it, true)
            LOG.info("Closed tab for stack $stackArn")
        }
    }

    private fun removeOldestTab() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("cloudformation.lsp.stack.view") ?: return
        val contentManager = toolWindow.contentManager
        
        if (contentManager.contentCount > 0) {
            val oldestContent = contentManager.contents.first()
            val stackArn = oldestContent.getUserData(STACK_ARN_KEY)
            stackArn?.let { 
                activeStacks[it]?.dispose()
                activeStacks.remove(it)
            }
            contentManager.removeContent(oldestContent, true)
            LOG.info("Removed oldest tab for stack $stackArn")
        }
    }

    companion object {
        private val LOG = getLogger<StackDetailWindowManager>()
        private val STACK_ARN_KEY = Key.create<String>("STACK_ARN")
        
        fun getInstance(project: Project): StackDetailWindowManager = project.service()
    }
}
