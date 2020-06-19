// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.datagrip.actions

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry
import software.aws.toolkits.jetbrains.core.datagrip.SecretManager
import software.aws.toolkits.jetbrains.core.datagrip.auth.SecretsManagerDbSecret
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.services.secretsmanager.SecretsManagerResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SecretsManagerDialogWrapper(private val selected: AwsExplorerNode<*>) : DialogWrapper(selected.nodeProject) {
    private lateinit var secrets: ResourceSelector<SecretListEntry>
    var dbSecret: SecretsManagerDbSecret? = null
        private set
    var dbSecretArn: String? = null
        private set

    init {
        title = message("datagrip.secretsmanager.action.title")
        setOKButtonText(message("general.create_button"))
        init()
    }

    override fun createCenterPanel(): JComponent? {
        secrets = ResourceSelector.builder(selected.nodeProject)
            .resource(SecretsManagerResources.secrets)
            .customRenderer { entry, renderer -> renderer.append(entry.name()); renderer }
            .build().also {
                // When it is changed, make sure the OK button is re-enabled
                it.addActionListener {
                    isOKActionEnabled = true
                }
            }
        val panel = JPanel(BorderLayout())
        panel.add(secrets)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val manager = SecretManager(selected)
        val response = manager.getSecret(secrets.selected()) ?: return ValidationInfo(
            message(
                "datagrip.secretsmanager.validation.failed_to_get",
                secrets.selected()?.arn().toString()
            )
        )
        dbSecret = response.first
        dbSecretArn = response.second
        return manager.validateSecret(response.first, response.second)
    }
}
