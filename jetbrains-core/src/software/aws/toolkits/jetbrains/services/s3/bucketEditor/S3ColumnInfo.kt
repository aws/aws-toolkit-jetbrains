package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory
import software.aws.toolkits.jetbrains.services.s3.S3VirtualFile
import javax.swing.tree.DefaultMutableTreeNode

open class S3ColumnInfo(columnTitle: String, private val valueGetter: (S3VirtualFile) -> String?) :
    ColumnInfo<Any, String>(columnTitle) {

    override fun valueOf(obj: Any): String? {
        val file = getVirtualFileFromNode(obj)
        return when (file) {
            is S3VirtualFile -> valueGetter.invoke(file)
            else -> ""
        }
    }

    override fun isCellEditable(item: Any?): Boolean = true

    fun getVirtualFileFromNode(obj: Any): VirtualFile? {
        val userObject = (obj as? DefaultMutableTreeNode)?.userObject
        return (userObject as? S3KeyNode)?.virtualFile
    }
}

class S3KeyColumnInfo(columnTitle: String, private val valueGetter: (S3VirtualFile) -> String?) :
    S3ColumnInfo(columnTitle, valueGetter) {

    override fun valueOf(obj: Any): String? {
        val file = super.getVirtualFileFromNode(obj)
        return when (file) {
            is S3VirtualFile -> valueGetter.invoke(file)
            is S3VirtualDirectory -> file.name
            else -> ""
        }
    }

    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
}