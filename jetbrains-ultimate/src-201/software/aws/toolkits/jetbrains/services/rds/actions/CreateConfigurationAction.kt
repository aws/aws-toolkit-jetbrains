// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.actions

import com.intellij.database.DatabaseBundle
import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeActionGroup
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.services.rds.RdsNode
import software.aws.toolkits.jetbrains.services.rds.auth.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth
import software.aws.toolkits.jetbrains.services.rds.auth.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.jdbcMysql
import software.aws.toolkits.jetbrains.services.rds.jdbcPostgres
import software.aws.toolkits.jetbrains.services.rds.mysqlEngineType
import software.aws.toolkits.jetbrains.services.rds.postgresEngineType
import software.aws.toolkits.jetbrains.services.rds.ui.CreateDataSourceDialogWrapper
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

// It is registered in ext-datagrip.xml FIX_WHEN_MIN_IS_201
@Suppress("ComponentNotRegistered")
class CreateDataSourceActionGroup : SingleExplorerNodeActionGroup<RdsNode>("rds.connect"), DumbAware {
    override fun getChildren(selected: RdsNode, e: AnActionEvent): List<AnAction> = listOf(
        CreateIamDataSourceAction(selected)
    )
}

class CreateIamDataSourceAction(private val node: RdsNode) : AnAction(message("rds.iam_config")) {
    override fun actionPerformed(e: AnActionEvent) {
        if (!checkPrerequisites()) {
            return
        }
        val dialog = CreateDataSourceDialogWrapper(node.nodeProject, node.dbInstance)
        val ok = dialog.showAndGet()
        if (!ok) {
            return
        }
        object : Task.Backgroundable(
            node.nodeProject,
            DatabaseBundle.message("message.text.refreshing.data.source"),
            true,
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        ) {
            override fun run(indicator: ProgressIndicator) {
                val username = dialog.getUsername() ?: throw IllegalStateException("Username is null, but it should have already been validated not null!")
                val database = dialog.getDatabaseName()
                val registry = DataSourceRegistry(node.nodeProject)
                createDatasource(registry, username, database)
                // Asynchronously show the user the configuration dialog to let them save/edit/test the profile
                runInEdt {
                    registry.showDialog()
                }
            }
        }.queue()
    }

    @TestOnly
    internal fun checkPrerequisites(): Boolean {
        // Assert IAM auth enabled
        if (!node.dbInstance.iamDatabaseAuthenticationEnabled()) {
            notifyError(
                project = node.nodeProject,
                title = message("aws.notification.title"),
                content = message("rds.validation.no_iam_auth", node.dbInstance.dbName()),
                action = OpenBrowserAction(message("rds.validation.setup_guide"), null, HelpIds.RDS_SETUP_IAM_AUTH.url)
            )
            return false
        }
        return true
    }

    @TestOnly
    internal fun createDatasource(registry: DataSourceRegistry, username: String, database: String) {
        val endpoint = node.dbInstance.endpoint()
        val url = "${endpoint.address()}:${endpoint.port()}"

        val builder = registry.builder
            .withJdbcAdditionalProperty(CREDENTIAL_ID_PROPERTY, node.nodeProject.activeCredentialProvider().id)
            .withJdbcAdditionalProperty(REGION_ID_PROPERTY, node.nodeProject.activeRegion().id)
        when (node.dbInstance.engine()) {
            mysqlEngineType -> {
                builder
                    .withUrl("jdbc:$jdbcMysql://$url/$database")
                    .withUser(username)
            }
            postgresEngineType -> {
                builder
                    .withUrl("jdbc:$jdbcPostgres://$url/$database")
                    // In postgres this is case sensitive as lower case. If you add a db user for
                    // IAM role "Admin", it is inserted as "admin"
                    .withUser(username.toLowerCase())
            }
            else -> throw IllegalArgumentException("Engine ${node.dbInstance.engine()} is not supported!")
        }
        builder.commit()
        // TODO FIX_WHEN_MIN_IS_202 set auth provider ID in builder
        registry.newDataSources.firstOrNull()?.let {
            it.authProviderId = IamAuth.providerId
        } ?: throw IllegalStateException("Newly inserted data source is not in the data source registry!")
    }
}
