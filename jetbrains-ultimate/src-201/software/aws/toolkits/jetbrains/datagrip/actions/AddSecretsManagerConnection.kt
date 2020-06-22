// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.actions

import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.connectionSettings
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.FullSslValidation
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.auth.SECRET_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerAuth
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerDbSecret
import software.aws.toolkits.jetbrains.datagrip.jdbcAdapterFromRuntime
import software.aws.toolkits.resources.message

class AddSecretsManagerConnection : SingleExplorerNodeAction<AwsExplorerNode<*>>(message("datagrip.secretsmanager.action")), DumbAware {

    override fun actionPerformed(selected: AwsExplorerNode<*>, e: AnActionEvent) {
        val dialogWrapper = SecretsManagerDialogWrapper(selected)
        val ok = dialogWrapper.showAndGet()
        if (!ok) {
            return
        }
        val secret = dialogWrapper.dbSecret ?: throw IllegalStateException("DBSecret is null, but should have been set by the dialog")
        val secretArn = dialogWrapper.dbSecretArn ?: throw IllegalStateException("DBSecret ARN is null, but should have been set by the dialog")

        val registry = DataSourceRegistry(selected.nodeProject)
        val adapter = jdbcAdapterFromRuntime(secret.engine)
            ?: throw IllegalStateException(message("datagrip.secretsmanager.validation.unkown_engine", secret.engine.toString()))
        registry.createDatasource(selected.nodeProject, secret, secretArn, adapter)
        // Show the user the configuration dialog to let them save/edit/test the profile
        runInEdt {
            registry.showDialog()
        }
    }
}

fun DataSourceRegistry.createDatasource(project: Project, secret: SecretsManagerDbSecret, secretArn: String, jdbcAdapter: String) {
    val connectionSettings = project.connectionSettings()
    builder
        .withJdbcAdditionalProperty(CREDENTIAL_ID_PROPERTY, connectionSettings?.credentials?.id)
        .withJdbcAdditionalProperty(REGION_ID_PROPERTY, connectionSettings?.region?.id)
        .withJdbcAdditionalProperty(SECRET_ID_PROPERTY, secretArn)
        .withUrl(secret.host)
        .withUser(secret.username)
        .withUrl("jdbc:$jdbcAdapter://${secret.host}:${secret.port}")
        .commit()
    // TODO FIX_WHEN_MIN_IS_202 set auth provider ID in builder
    newDataSources.firstOrNull()?.let {
        it.authProviderId = SecretsManagerAuth.providerId
        it.sslCfg = FullSslValidation
    }
}
