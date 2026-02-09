// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager

object ResourceTypeDialogUtils {
    private val LOG = getLogger<ResourceTypeDialogUtils>()

    internal fun showResourceTypeSelectionDialog(project: Project, resourceTypesManager: ResourceTypesManager) {
        val availableTypes = resourceTypesManager.getAvailableResourceTypes()
        val selectedTypes = resourceTypesManager.getSelectedResourceTypes()

        if (availableTypes.isEmpty()) {
            return
        }

        LOG.info { "starting dialog" }
        try {
            val dialog = ResourceTypeSelectionDialog(project, availableTypes, selectedTypes)
            if (dialog.showAndGet()) {
                // Handle both additions and removals
                val newSelections = dialog.selectedResourceTypes.toSet()

                // Add new selections
                newSelections.forEach { type ->
                    if (type !in selectedTypes) {
                        resourceTypesManager.addResourceType(type)
                    }
                }

                // Remove deselected types
                selectedTypes.forEach { type ->
                    if (type !in newSelections) {
                        resourceTypesManager.removeResourceType(type)
                    }
                }

                LOG.info { "finished updating resource types" }
            }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to show dialog" }
        }
    }
}
