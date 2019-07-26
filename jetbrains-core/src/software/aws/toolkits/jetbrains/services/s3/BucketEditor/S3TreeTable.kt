// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.BucketEditor

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.treeTable.TreeTableModelWithColumns
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket

open class S3TreeTable(treeTableModel: TreeTableModel) : TreeTable(treeTableModel) {
    val tModel: TreeTableModel = treeTableModel
//    val list : TreeModelListener = adapter()
    fun refresh(bucket: S3VirtualBucket) {

        // find a better way without requiring the need for creating the model all over again
        runInEdt {
            clearSelection()

                val columns = (tableModel as TreeTableModelWithColumns).columns
                val s3Node = S3KeyNode(bucket)
                val myTreeModelDisposable = Disposer.newDisposable()
                val treeStructure = SimpleTreeStructure.Impl(s3Node)
                val myTreeModel = StructureTreeModel<SimpleTreeStructure>(treeStructure)

                val model = (TreeTableModelWithColumns(AsyncTreeModel(myTreeModel, true, myTreeModelDisposable), columns))

                setModel(model)
                setRootVisible(false)

            }
        }
    }

//    fun addTreeMode(treeTableModel: TreeTableModel){
////        val treeListener = TreeTableModelAdapter(treeTableModel, tree, )
////        treeTableModel.addTreeModelListener(treeListener as TreeModelListener)
//    }
//
//
//class adapter: TreeModelAdapter(){
//
//    override fun treeNodesChanged(event: TreeModelEvent?) {




