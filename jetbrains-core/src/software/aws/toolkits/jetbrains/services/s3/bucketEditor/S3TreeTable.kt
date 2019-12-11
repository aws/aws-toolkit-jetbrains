// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.treeStructure.treetable.TreeTable
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket
import software.aws.toolkits.jetbrains.services.s3.objectActions.UploadObjectAction
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.tree.DefaultMutableTreeNode

class S3TreeTable(
    private val treeTableModel: S3TreeTableModel,
    private val bucketVirtual: S3VirtualBucket,
    private val project: Project,
    private val s3Client: S3Client
) :
    TreeTable(treeTableModel) {
    init {
        dropTarget = createDropTarget()
    }

    fun refresh() {
        runInEdt {
            clearSelection()
            treeTableModel.structureTreeModel.invalidate()
        }
    }

    private val mouseListener = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            val row = rowAtPoint(e.point).takeIf { it >= 0 } ?: return
            handleLoadingMore(row, e)
        }
    }

    private fun handleLoadingMore(row: Int, e: MouseEvent) {
        if (e.clickCount != 2) {
            return
        }
        val continuationNode = (tree.getPathForRow(row).lastPathComponent as? DefaultMutableTreeNode)?.userObject as? S3TreeContinuationNode ?: return
        val parent = continuationNode.parent ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            parent.loadMore(continuationNode.token)
            refresh()
        }
    }

    init {
        super.addMouseListener(mouseListener)
    }

    fun getNodeForRow(row: Int): S3TreeNode? {
        val path = tree.getPathForRow(convertRowIndexToModel(row))
        return (path.lastPathComponent as DefaultMutableTreeNode).userObject as? S3TreeNode
    }

    fun getSelectedNodes(): List<S3TreeNode> = selectedRows.map { getNodeForRow(it) }.filterNotNull()

    fun removeRows(rows: List<Int>) =
        runInEdt {
            rows.map {
                val path = tree.getPathForRow(it)
                path.lastPathComponent as DefaultMutableTreeNode
            }.forEach {
                val userNode = it.userObject as? S3TreeNode ?: return@forEach
                ((it.parent as? DefaultMutableTreeNode)?.userObject as? S3TreeDirectoryNode)?.removeChild(userNode)
            }
        }

    fun invalidateLevel(node: S3TreeNode) {
        node.parent?.removeAllChildren()
    }

    private fun createDropTarget(): DropTarget {
        val dropTarget = DropTarget()
        try {
            dropTarget.addDropTargetListener(object : DropTargetAdapter() {
                override fun drop(dropEvent: DropTargetDropEvent) {
                    try {
                        dropEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
                        val data = dropEvent.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        val row = rowAtPoint(dropEvent.location).takeIf { it >= 0 } ?: return
                        val node = getNodeForRow(row) ?: return

                        val lfs = LocalFileSystem.getInstance()
                        val virtualFiles = data.mapNotNull {
                            lfs.findFileByIoFile(it)
                        }

                        ApplicationManager.getApplication().executeOnPooledThread {
                            val action = UploadObjectAction(bucketVirtual, this@S3TreeTable)
                            virtualFiles.forEach {
                                try {
                                    action.uploadObjectAction(s3Client, project, it, node)
                                } catch (e: Exception) {
                                    LOG.error("unable to upload to s3 from drag and drop", e)
                                }
                            }

                            invalidateLevel(node)
                            refresh()
                        }
                    } catch (e: UnsupportedFlavorException) {
                        LOG.info("Unsupported flavor attempted to be dragged and dropped", e)
                        // When the drag and drop data is not what we expect (like when it is text) this is thrown and can be safey ignored
                    } catch (e: Exception) {
                        LOG.error("Drag and drop threw", e)
                    }
                }
            })
        } catch (e: Exception) {
            println(e)
        }
        return dropTarget
    }

    companion object {
        private val LOG = getLogger<S3TreeTable>()
    }
}
