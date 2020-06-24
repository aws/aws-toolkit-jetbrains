// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.actions

import com.intellij.database.DatabaseBundle
import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.RdsDatasourceConfiguration
import software.aws.toolkits.jetbrains.services.rds.RdsNode
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth
import software.aws.toolkits.jetbrains.services.rds.jdbcMysql
import software.aws.toolkits.jetbrains.services.rds.jdbcPostgres
import software.aws.toolkits.jetbrains.services.rds.mysqlEngineType
import software.aws.toolkits.jetbrains.services.rds.postgresEngineType
import software.aws.toolkits.jetbrains.services.sts.StsResources
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.DatabaseCredentials
import software.aws.toolkits.telemetry.RdsTelemetry
import software.aws.toolkits.telemetry.Result

// It is registered in ext-datagrip.xml FIX_WHEN_MIN_IS_201
@Suppress("ComponentNotRegistered")
class CreateIamDataSourceAction : SingleExplorerNodeAction<RdsNode>(message("rds.iam_config")), DumbAware {
    override fun actionPerformed(node: RdsNode, e: AnActionEvent) {
        if (!checkPrerequisites(node)) {
            return
        }
        object : Task.Backgroundable(
            node.nodeProject,
            DatabaseBundle.message("message.text.refreshing.data.source"),
            true,
            PerformInBackgroundOption.ALWAYS_BACKGROUND
        ) {
            override fun run(indicator: ProgressIndicator) {
                val registry = DataSourceRegistry(node.nodeProject)
                createDatasource(node, registry)
                // Asynchronously show the user the configuration dialog to let them save/edit/test the profile
                runInEdt {
                    registry.showDialog()
                }
            }

            override fun onCancel() = recordTelemetry(Result.Cancelled)
            override fun onThrowable(error: Throwable) = recordTelemetry(Result.Failed)
            override fun onSuccess() = recordTelemetry(Result.Succeeded)

            private fun recordTelemetry(result: Result) = RdsTelemetry.createConnectionConfiguration(
                node.nodeProject,
                result,
                DatabaseCredentials.IAM,
                node.dbInstance.engine()
            )
        }.queue()
    }

    internal fun checkPrerequisites(node: RdsNode): Boolean {
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

    internal fun createDatasource(node: RdsNode, registry: DataSourceRegistry) {
        val username = try {
            // use current STS user as username. Split on : because it comes back id:username
            AwsResourceCache.getInstance(node.nodeProject).getResourceNow(StsResources.USER).substringAfter(':')
        } catch (e: Exception) {
            LOG.warn(e) { "Getting username from STS failed, falling back to master username" }
            node.dbInstance.masterUsername()
        }
        registry.createRdsDatasource(
            RdsDatasourceConfiguration(
                regionId = node.nodeProject.activeRegion().id,
                credentialId = node.nodeProject.activeCredentialProvider().id,
                dbEngine = node.dbInstance.engine(),
                endpoint = node.dbInstance.endpoint(),
                username = username
            )
        )
    }

    private companion object {
        val LOG = getLogger<CreateIamDataSourceAction>()
    }
}

fun DataSourceRegistry.createRdsDatasource(config: RdsDatasourceConfiguration) {
    val url = "${config.endpoint.address()}:${config.endpoint.port()}"

    val builder = builder
        .withJdbcAdditionalProperty(CREDENTIAL_ID_PROPERTY, config.credentialId)
        .withJdbcAdditionalProperty(REGION_ID_PROPERTY, config.regionId)
    when (config.dbEngine) {
        mysqlEngineType -> {
            builder
                .withUrl("jdbc:$jdbcMysql://$url/")
                .withUser(config.username)
        }
        postgresEngineType -> {
            builder
                .withUrl("jdbc:$jdbcPostgres://$url/")
                // In postgres this is case sensitive as lower case. If you add a db user for
                // IAM role "Admin", it is inserted as "admin"
                .withUser(config.username.toLowerCase())
        }
        else -> throw IllegalArgumentException("Engine ${config.dbEngine} is not supported for IAM auth!")
    }
    builder.commit()
    // TODO FIX_WHEN_MIN_IS_202 set auth provider ID in builder. There is no way to set it in the builder,
    // so we have to set it after the fact. However, that means we need to pull it out after it is built.
    // The builder doesn't return a reference to it, so we have to pull it out of the committed data sources.
    // newDataSources contains the list of ones added just now, so add it to that
    newDataSources.firstOrNull()?.let {
        it.authProviderId = IamAuth.providerId
    } ?: throw IllegalStateException("Newly inserted data source is not in the data source registry!")
}
