// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.JBColor
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object StackStatusUtils {
    fun getStatusColors(status: String): Pair<Color?, Color?> = when {
        status.contains("COMPLETE") && !status.contains("ROLLBACK") ->
            JBColor(0x28A745, 0x28A745) to JBColor.WHITE
        status.contains("FAILED") || status.contains("ROLLBACK") ->
            JBColor(0xDC3545, 0xDC3545) to JBColor.WHITE
        status.contains("PROGRESS") ->
            JBColor(0xFFC107, 0xFFC107) to JBColor(0x212529, 0x212529)
        else -> null to null
    }

    fun isInTransientState(status: String): Boolean =
        status.contains("_IN_PROGRESS") || status.contains("_CLEANUP_IN_PROGRESS")
}

object StackDateFormatter {
    private val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy, h:mm:ss a")

    fun formatDate(dateString: String?): String? = try {
        dateString?.let {
            val instant = Instant.parse(it)
            instant.atZone(ZoneId.systemDefault()).format(dateFormatter)
        }
    } catch (e: Exception) {
        dateString
    }
}
