// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager.Companion.CONNECTION_SETTINGS_STATE_CHANGED
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.getResourceNow
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import software.aws.toolkits.resources.message

class CloudFormationStackFilter private constructor(
    private val stackName: String,
    private val project: Project,
    private val resources: Map<String, Set<String>>
) :
    AwsExplorerFilter, Disposable, ConnectionSettingsStateChangeNotifier {

    init {
        project.messageBus.connect(this).subscribe(CONNECTION_SETTINGS_STATE_CHANGED, this)
        ApplicationManager.getApplication().messageBus
    }

    override fun displayName() = message("cloudformation.filter.text", stackName)

    override fun show(node: AwsExplorerNode<*>) = when {
        node is CloudFormationResourceNode && node is CloudFormationResourceParentNode -> showResource(node) || showParent(node)
        node is CloudFormationResourceNode -> showResource(node)
        node is CloudFormationResourceParentNode -> showParent(node)
        else -> true
    }

    private fun showResource(node: CloudFormationResourceNode) = resources[node.cfnResourceType]?.contains(node.cfnPhysicalIdentifier) == true

    private fun showParent(node: CloudFormationResourceParentNode) = node.cfnResourceTypes().intersect(resources.keys).isNotEmpty()

    companion object {
        fun newInstance(project: Project, stackName: String, stackId: String): CloudFormationStackFilter {
            val resources = project.getResourceNow(CloudFormationResources.stackResources(stackName))
                .groupBy { it.resourceType() }
                .mapValues { it.value.map { summary -> summary.physicalResourceId() }.toSet() } + mapOf("AWS::CloudFormation::Stack" to setOf(stackId))

            return CloudFormationStackFilter(stackName, project, resources)
        }
    }

    override fun dispose() {
    }

    override fun settingsStateChanged(newState: ConnectionState) {
        AwsExplorerFilterManager.getInstance(project).let { filterManager ->
            if (filterManager.currentFilter() == this) {
                filterManager.clearFilter()
            }
        }
    }
}

/**
 * A marker interface for explorer nodes that have children that implement [CloudFormationResourceNode]
 */
interface CloudFormationResourceParentNode {
    /**
     * The types of CloudFormation resources that this parent node has as children.
     */
    fun cfnResourceTypes(): Set<String>
}

/**
 * Marker interface used for filtering now, but in the future can also be used by CloudAPI
 */
interface CloudFormationResourceNode {
    val cfnResourceType: String
    val cfnPhysicalIdentifier: String
}
