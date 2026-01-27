// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.region.AwsRegion
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider

/**
 * Manages the selected region for the CloudFormation panel.
 * This is separate from the main AWS connection region to allow users
 * to view stacks, resources and deploy in a different region than
 * their active connection.
 */
@Service(Service.Level.APP)
@State(name = "cfnLspExplorerRegion", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class CloudFormationRegionManager : PersistentStateComponent<CloudFormationRegionManager.State> {
    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Gets the selected region for CloudFormation operations.
     * Priority:
     * 1. CloudFormation-specific region (if set)
     * 2. Active connection's region
     * 3. Default region (us-east-1)
     */
    fun getSelectedRegion(project: Project? = null): AwsRegion {
        val regionProvider = AwsRegionProvider.getInstance()

        // If CloudFormation-specific region is set, use it
        state.selectedRegionId?.let { regionId ->
            regionProvider.allRegions()[regionId]?.let { return it }
        }

        // Fall back to active connection's region
        project?.let { it ->
            AwsConnectionManager.getInstance(it).selectedRegion?.let { return it }
        }

        // Final fallback
        return regionProvider.defaultRegion()
    }

    fun setSelectedRegion(region: AwsRegion) {
        state.selectedRegionId = region.id
    }

    fun clearSelectedRegion() {
        state.selectedRegionId = null
    }

    data class State(
        var selectedRegionId: String? = null,
    )

    companion object {
        fun getInstance(): CloudFormationRegionManager = service()
    }
}
