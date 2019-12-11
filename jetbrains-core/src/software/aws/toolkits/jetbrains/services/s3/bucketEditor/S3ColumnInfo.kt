// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketEditor

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import software.aws.toolkits.resources.message
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.tree.DefaultMutableTreeNode

class S3Column(private val type: S3ColumnType) : ColumnInfo<Any, String>(type.title) {
    override fun valueOf(item: Any?): String? {
        val userObject = (item as DefaultMutableTreeNode).userObject ?: return ""
        return getValue(userObject)
    }

    override fun isCellEditable(item: Any?): Boolean = false
    override fun getColumnClass(): Class<*> = if (type == S3ColumnType.NAME) TreeTableModel::class.java else super.getColumnClass()

    private fun getValue(userObject: Any): String =
        if (userObject is S3TreeObjectNode) {
            when (type) {
                S3ColumnType.NAME -> userObject.key
                S3ColumnType.SIZE -> StringUtil.formatFileSize(userObject.size)
                S3ColumnType.LAST_MODIFIED -> {
                    val datetime = LocalDateTime.ofInstant(userObject.lastModified, ZoneId.systemDefault())
                    datetime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d YYYY hh:mm:ss a z"))
                }
            }
        } else {
            ""
        }
}

enum class S3ColumnType(val title: String) {
    NAME(message("s3.name")),
    SIZE(message("s3.size")),
    LAST_MODIFIED(message("s3.last_modified"));
}
