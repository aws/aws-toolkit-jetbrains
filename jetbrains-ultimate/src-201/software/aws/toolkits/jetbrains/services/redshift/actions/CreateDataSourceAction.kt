// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.actions

import com.intellij.database.DatabaseBundle
import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.redshift.model.Cluster
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.services.redshift.RedshiftExplorerNode
import software.aws.toolkits.jetbrains.services.redshift.auth.ApiAuth
import software.aws.toolkits.jetbrains.ui.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.ui.REGION_ID_PROPERTY
import software.aws.toolkits.resources.message

// It is registered in ext-datagrip.xml FIX_WHEN_MIN_IS_201
@Suppress("ComponentNotRegistered")
class CreateDataSourceAction : SingleExplorerNodeAction<RedshiftExplorerNode>(message("redshift.connect_aws_credentials")), DumbAware {
    override fun actionPerformed(selected: RedshiftExplorerNode, e: AnActionEvent) {
        object : Backgroundable(
            selected.nodeProject,
            DatabaseBundle.message("message.text.refreshing.data.source"),
            true,
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        ) {
            override fun run(indicator: ProgressIndicator) {
                val registry = DataSourceRegistry(selected.nodeProject)
                registry.createDatasource(selected.nodeProject, selected.cluster)
                // Asynchronously show the user the configuration dialog to let them save/edit/test the profile
                runInEdt {
                    registry.showDialog()
                }
            }
        }.queue()
    }

    internal fun DataSourceRegistry.createDatasource(project: Project, cluster: Cluster) {
        builder
            .withJdbcAdditionalProperty(CREDENTIAL_ID_PROPERTY, project.activeCredentialProvider().id)
            .withJdbcAdditionalProperty(REGION_ID_PROPERTY, project.activeRegion().id)
            .withUrl(cluster.clusterIdentifier())
            .withUser(cluster.masterUsername())
            .withUrl("jdbc:redshift://${cluster.endpoint().address()}:${cluster.endpoint().port()}/${cluster.dbName()}")
            .commit()
        // TODO FIX_WHEN_MIN_IS_202 set auth provider ID in builder
        newDataSources.firstOrNull()?.let {
            it.authProviderId = ApiAuth.providerId
        } ?: throw IllegalStateException("Newly inserted data source is not in the data source registry!")
    }
}
