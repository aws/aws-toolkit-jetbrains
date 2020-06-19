// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.datagrip

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.datagrip.auth.SecretsManagerDbSecret
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.services.rds.RdsNode
import software.aws.toolkits.jetbrains.services.redshift.RedshiftExplorerNode
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

class SecretManager(private val selected: AwsExplorerNode<*>) {
    private val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun getSecret(secret: SecretListEntry?): Pair<SecretsManagerDbSecret, String>? {
        secret ?: return null
        return try {
            val value = AwsClientManager.getInstance(selected.nodeProject).getClient<SecretsManagerClient>().getSecretValue { it.secretId(secret.arn()) }
            val dbSecret = objectMapper.readValue<SecretsManagerDbSecret>(value.secretString())
            Pair(dbSecret, secret.arn())
        } catch (e: Exception) {
            notifyError(
                title = message("datagrip.secretsmanager.validation.failed_to_get", secret.name()),
                content = e.message ?: ""
            )
            null
        }
    }

    fun validateSecret(dbSecret: SecretsManagerDbSecret, secretName: String): ValidationInfo? {
        // Validate the secret has the bare minimum
        dbSecret.username ?: return ValidationInfo(message("datagrip.secretsmanager.validation.no_username", secretName))
        dbSecret.password ?: return ValidationInfo(message("datagrip.secretsmanager.validation.no_password", secretName))
        // If it is a resource node, validate that it is the same resource
        when (selected) {
            is RdsNode -> {
                if (selected.dbInstance.engine() != dbSecret.engine) return ValidationInfo(
                    message(
                        "datagrip.secretsmanager.validation.different_engine",
                        secretName,
                        dbSecret.engine.toString()
                    )
                )
                if (selected.dbInstance.endpoint().address() != dbSecret.host) return ValidationInfo(
                    message("datagrip.secretsmanager.validation.different_address", secretName, dbSecret.host.toString())
                )
            }
            is RedshiftExplorerNode -> {
                if (dbSecret.engine != redshiftEngineType) return ValidationInfo(
                    message(
                        "datagrip.secretsmanager.validation.different_engine",
                        secretName,
                        dbSecret.engine.toString()
                    )
                )
                if (selected.cluster.clusterIdentifier() != dbSecret.dbClusterIdentifier) return ValidationInfo(
                    message("datagrip.secretsmanager.validation.different_cluster_id", secretName, dbSecret.dbClusterIdentifier.toString())
                )
                if (selected.cluster.endpoint().address() != dbSecret.host) return ValidationInfo(
                    message("datagrip.secretsmanager.validation.different_address", secretName, dbSecret.host.toString())
                )
            }
        }
        return null
    }
}
