package software.aws.toolkits.jetbrains.services.s3

import software.aws.toolkits.jetbrains.services.s3.bucketEditor.S3KeyNode
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class S3TreeCellRenderer : DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val renderer = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus) as JLabel
        if (value is DefaultMutableTreeNode) {
            val userObject = value.userObject
            if (userObject is S3KeyNode) {
                val file = userObject.virtualFile
                if (file.isDirectory) {
                    if (expanded) renderer.icon = openIcon else renderer.icon = closedIcon
                } else {
                    renderer.icon = leafIcon
                }
            }
        }
        return renderer
    }
}