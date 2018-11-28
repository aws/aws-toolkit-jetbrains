// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.LoadingNode
import com.intellij.ui.SimpleTextAttributes
import icons.AwsIcons
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.resources.message
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

interface AwsNodeAlwaysExpandable

interface AwsNodeChildCache {
    fun isInitialChildState(): Boolean
    fun getChildren(refresh: Boolean): Collection<AbstractTreeNode<Any>>
}

abstract class AwsExplorerNode<T>(val nodeProject: Project, value: T, private val awsIcon: Icon?) : AbstractTreeNode<T>(nodeProject, value) {

    override fun update(presentation: PresentationData) {
        presentation.let {
            it.setIcon(awsIcon)
            it.addText(displayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            statusText()?.let { status ->
                it.addText(" [$status]", SimpleTextAttributes.GRAY_ATTRIBUTES)
            }
        }
    }

    open fun displayName() = value.toString()

    open fun statusText(): String? = null

    open fun onDoubleClick(model: DefaultTreeModel, selectedElement: DefaultMutableTreeNode) {}

    protected val region = nodeProject.activeRegion()
    protected val credentialProvider = nodeProject.activeCredentialProvider()
}

class AwsExplorerRootNode(project: Project) : AwsExplorerNode<String>(project, "ROOT", AwsIcons.Logos.AWS) {

    private val regionProvider = AwsRegionProvider.getInstance()
    private val settings = ProjectAccountSettingsManager.getInstance(project)

    override fun getChildren(): Collection<AbstractTreeNode<String>> {
        val childrenList = mutableListOf<AbstractTreeNode<String>>()
        AwsExplorerService.values()
                .filter {
                    regionProvider.isServiceSupported(settings.activeRegion, it.serviceId)
                }
                .mapTo(childrenList) { it.buildServiceRootNode(project!!) }

        return childrenList
    }
}

abstract class AwsExplorerPageableNode<T>(project: Project, value: T, icon: Icon?) : AwsExplorerNode<T>(project, value, icon) {
    private val childNodes: MutableList<AwsExplorerNode<*>> by lazy {
        val initialList = mutableListOf<AwsExplorerNode<*>>()

        val data = loadData()
        if (data.isEmpty()) {
            initialList.add(AwsExplorerEmptyNode(project))
        } else {
            initialList.addAll(data)
        }
        initialList
    }

    internal fun loadData(paginationToken: String? = null): Collection<AwsExplorerNode<*>> = try {
        loadResources(paginationToken)
    } catch (e: Exception) {
        LOG.warn("Failed to load explorer nodes", e)
        // Return the ErrorNode as the single Node of the list
        listOf(AwsExplorerErrorNode(project!!, e))
    }

    protected abstract fun loadResources(paginationToken: String? = null): Collection<AwsExplorerNode<*>>

    final override fun getChildren(): MutableList<AwsExplorerNode<*>> = childNodes

    private companion object {
        private val LOG = getLogger<AwsExplorerPageableNode<*>>()
    }
}

abstract class AwsExplorerServiceRootNode(project: Project, value: String) : AwsExplorerPageableNode<String>(project, value, null) {
    abstract fun serviceName(): String
}

abstract class AwsExplorerResourceNode<T>(
    project: Project,
    val serviceName: String,
    value: T,
    awsIcon: Icon,
    val immutable: Boolean = false
) : AwsExplorerNode<T>(project, value, awsIcon) {
    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    abstract fun resourceType(): String
}

class AwsTruncatedResultNode(private val parentNode: AwsExplorerPageableNode<*>, private val paginationToken: String) :
        AwsExplorerNode<String>(parentNode.project!!, MESSAGE, null) {

    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.addText(value, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun onDoubleClick(model: DefaultTreeModel, selectedElement: DefaultMutableTreeNode) {
        // The Tree is represented by two systems. The entire tree (AbstractTreeNode) and the subsection
        // of the tree that is visible (MutableTreeNode). We have to update both systems if we wish to show the
        // data correctly, else we will lose data either when nodes are collapsed/expanded again, or the tree UI does
        // not show the new data. Tree Node will refer to nodes of type AbstractTreeNode, UI node will refer to
        // MutableTreeNode

        // Remove the truncated node from the Tree Node and add our fake loading node so that if they collapse and
        // expand quick enough it'll stay in loading mode
        val children = parentNode.children
        children.asReversed().removeIf { it is AwsTruncatedResultNode }
        children.add(AwsExplorerLoadingNode(project!!))

        // Update the UI version to remove the truncation node and add loading
        val parent = selectedElement.parent as DefaultMutableTreeNode
        model.removeNodeFromParent(selectedElement)
        val loadingNode = LoadingNode()
        model.insertNodeInto(loadingNode, parent, parent.childCount)

        ApplicationManager.getApplication().executeOnPooledThread {
            val nextSetOfResources = parentNode.loadData(paginationToken)

            // Run next steps on the EDT thread
            ApplicationManager.getApplication().invokeLater {
                // If the parent UI node is gone, we re-collapsed so no reason to remove it
                val parentTreeNode = loadingNode.parent as? MutableTreeNode
                if (parentTreeNode != null) {
                    model.removeNodeFromParent(loadingNode)
                }

                // Remove our fake loading node from Tree Node
                children.removeIf {
                    it is AwsExplorerLoadingNode
                }

                nextSetOfResources.forEach {
                    // This call is usually handled by the Tree infrastructure, but we are doing pagination manually
                    // so we also need to update the presentation data manually else it will render without the
                    // proper text
                    it.update()
                    children.add(it)

                    if (parentTreeNode != null) {
                        model.insertNodeInto(DefaultMutableTreeNode(it), parentTreeNode, parentTreeNode.childCount)
                    }
                }
            }
        }
    }

    override fun isAlwaysLeaf() = true

    companion object {
        val MESSAGE get() = message("explorer.results_truncated")
    }
}

class AwsExplorerLoadingNode(project: Project) :
        AwsExplorerNode<String>(project, IdeBundle.message("treenode.loading"), null) {

    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.addText(value, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun isAlwaysLeaf() = true
}

class AwsExplorerErrorNode(project: Project, exception: Exception) :
        AwsExplorerNode<Exception>(project, exception, null) {

    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.apply {
            // If we don't have a message, at least give them the error type
            tooltip = value.message ?: value.javaClass.simpleName
            addText(MSG, SimpleTextAttributes.ERROR_ATTRIBUTES)
        }
    }

    override fun isAlwaysLeaf() = true

    companion object {
        val MSG get() = message("explorer.error_loading_resources")
    }
}

class AwsExplorerEmptyNode(project: Project, value: String = message("explorer.empty_node")) : AwsExplorerNode<String>(project, value, awsIcon = null) {
    override fun getChildren(): Collection<AbstractTreeNode<Any>> = emptyList()

    override fun update(presentation: PresentationData) {
        presentation.addText(displayName(), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun isAlwaysLeaf() = true
}
