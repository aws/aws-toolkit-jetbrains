// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ui.JBColor
import software.aws.toolkits.core.utils.getLogger
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object StackStatusUtils {
    fun getStatusColors(status: String): Pair<Color?, Color?> = when {
        status.contains("COMPLETE") && !status.contains("ROLLBACK") ->
            JBColor.GREEN to JBColor.BLACK
        status.contains("FAILED") || status.contains("ROLLBACK") ->
            JBColor.RED to JBColor.BLACK
        status.contains("PROGRESS") ->
            JBColor.YELLOW to JBColor.BLACK
        else -> null to null
    }

    fun isInTransientState(status: String): Boolean = status.contains("_IN_PROGRESS")
}

internal object StackDateFormatter {
    private val LOG = getLogger<StackDateFormatter>()
    private val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy, h:mm:ss a")

    fun formatDate(dateString: String): String? = try {
        val instant = Instant.parse(dateString)
        instant.atZone(ZoneId.systemDefault()).format(dateFormatter)
    } catch (e: Exception) {
        LOG.warn("Failed to parse date string: $dateString", e)
        null
    }
}
