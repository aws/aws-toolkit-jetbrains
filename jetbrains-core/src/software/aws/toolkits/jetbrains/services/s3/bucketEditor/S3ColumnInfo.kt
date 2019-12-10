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

class S3Column(private val type: S3ColumnType) : ColumnInfo<Any, String>(type.message) {
    override fun valueOf(item: Any?): String? {
        val userObject = (item as DefaultMutableTreeNode).userObject ?: return ""
        return type.getValue(userObject)
    }

    override fun isCellEditable(item: Any?): Boolean = false
    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
}

enum class S3ColumnType(val message: String) {
    NAME(message("s3.name")),
    SIZE(message("s3.size")),
    LAST_MODIFIED(message("s3.last_modified"));

    fun getValue(userObject: Any): String =
        if (userObject is S3ObjectNode) {
            when (this) {
                NAME -> userObject.key
                SIZE -> StringUtil.formatFileSize(userObject.size)
                LAST_MODIFIED -> {
                    val datetime = LocalDateTime.ofInstant(userObject.lastModified, ZoneId.systemDefault())
                    datetime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d YYYY hh:mm:ss a z"))
                }
            }
        } else {
            ""
        }
}
