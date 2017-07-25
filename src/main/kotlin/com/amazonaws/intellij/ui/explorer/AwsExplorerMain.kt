package com.amazonaws.intellij.ui.explorer

import com.amazonaws.intellij.aws.S3ClientProvider
import com.amazonaws.intellij.aws.s3.S3BucketVirtualFile
import com.amazonaws.intellij.aws.s3.S3VirtualFileSystem
import com.amazonaws.intellij.ui.AWS_ICON
import com.amazonaws.intellij.ui.S3_BUCKET_ICON
import com.amazonaws.intellij.ui.S3_SERVICE_ICON
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.Bucket
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

class AwsExplorerMainEventHandler(private val project: Project,
                                  private val s3DetailsController: S3BucketDetailController) {
    private val editorManager = FileEditorManager.getInstance(project);

    fun resourceSelected(selected: Any) {
        when(selected) {
            is AwsTreeNode<*> -> when(selected.value) {
                is Bucket -> {
                    s3DetailsController.update(selected.value)
                    val bucketVirtualFile = S3BucketVirtualFile(S3VirtualFileSystem(AmazonS3ClientBuilder.standard().withRegion("us-east-1").build()), selected.value)
                        editorManager.openFile(bucketVirtualFile, true)
                }
            }
        }
    }
}

class AwsExplorerMainController(private val s3Provider: S3ClientProvider, private val view: AwsExplorerMainView) {
    fun load() {
        view.updateResources(createResourcesTree())
    }

    private fun createResourcesTree(): TreeNode {
        val root = DefaultMutableTreeNode(AwsTreeNode(AWS_ICON, "Resources"))
        val s3Node = AwsTreeNode(S3_SERVICE_ICON, "S3")
        s3Provider.s3Client().listBuckets().forEach { s3Node.add(AwsTreeNode(S3_BUCKET_ICON, it, Bucket::getName)) }
        root.add(s3Node)
        return root
    }
}

class AwsExplorerMainView(eventHandler: AwsExplorerMainEventHandler, s3DetailsView : S3BucketDetailView) : JPanel(GridLayout()) {
    val resources = JTree()

    init {
        val details = JPanel(GridLayout())
        details.add(s3DetailsView)
        resources.isRootVisible = false
        resources.autoscrolls = true
        resources.addTreeSelectionListener { eventHandler.resourceSelected(it.path.lastPathComponent) }
        resources.cellRenderer = TreeCellRenderer()
        val main = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(resources), details)
        main.leftComponent.preferredSize = Dimension(500, 100)
        main.dividerSize = 2
        add(main)
    }

    fun updateResources(root: TreeNode) {
        (resources.model as DefaultTreeModel).setRoot(root)
    }
}