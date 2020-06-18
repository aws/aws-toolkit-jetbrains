// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.datagrip.actions

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.database.dataSource.DataSourceSslConfiguration
import com.intellij.database.remote.jdbc.helpers.JdbcSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.connectionSettings
import software.aws.toolkits.jetbrains.core.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.core.datagrip.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.core.datagrip.auth.SECRET_ID_PROPERTY
import software.aws.toolkits.jetbrains.core.datagrip.auth.SecretsManagerAuth
import software.aws.toolkits.jetbrains.core.datagrip.auth.SecretsManagerDbSecret
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.services.rds.RdsParentNode

class AddSecretsManagerConnection : SingleExplorerNodeAction<RdsParentNode>("TODO localize"), DumbAware {
    private val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    // TODO put in background
    override fun actionPerformed(node: RdsParentNode, e: AnActionEvent) {
        val dialogWrapper = SecretsManagerDialogWrapper(node.nodeProject)
        val ok = dialogWrapper.showAndGet()
        if (!ok) {
            return
        }
        val secret = dialogWrapper.selected() ?: throw IllegalStateException("TODO localize")
        val value = AwsClientManager.getInstance(node.nodeProject).getClient<SecretsManagerClient>().getSecretValue { it.secretId(secret.arn()) }
        val dbSecret = objectMapper.readValue<SecretsManagerDbSecret>(value.secretString())
        val registry = DataSourceRegistry(node.nodeProject)
        registry.createDatasource(node.nodeProject, dbSecret, secret.arn())
        // Asynchronously show the user the configuration dialog to let them save/edit/test the profile
        runInEdt {
            registry.showDialog()
        }
    }
}

fun DataSourceRegistry.createDatasource(project: Project, secret: SecretsManagerDbSecret, secretArn: String) {
    val connectionSettings = project.connectionSettings()
    builder
        .withJdbcAdditionalProperty(CREDENTIAL_ID_PROPERTY, connectionSettings?.credentials?.id)
        .withJdbcAdditionalProperty(REGION_ID_PROPERTY, connectionSettings?.region?.id)
        .withJdbcAdditionalProperty(SECRET_ID_PROPERTY, secretArn)
        .withUrl(secret.host)
        .withUser(secret.username)
        .withUrl("jdbc:redshift://${secret.host}:${secret.port}")
        .commit()
    // TODO FIX_WHEN_MIN_IS_202 set auth provider ID in builder
    newDataSources.firstOrNull()?.let {
        it.authProviderId = SecretsManagerAuth.providerId
        it.sslCfg = it.sslCfg?.also { cfg -> cfg.myEnabled = true }
            ?: DataSourceSslConfiguration("", "", "", true, JdbcSettings.SslMode.VERIFY_FULL)
    }
}
