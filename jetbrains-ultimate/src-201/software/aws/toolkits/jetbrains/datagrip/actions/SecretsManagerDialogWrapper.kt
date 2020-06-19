// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.actions

import com.intellij.openapi.ui.DialogWrapper
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.datagrip.SecretManager
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerDbSecret
import software.aws.toolkits.jetbrains.services.secretsmanager.SecretsManagerResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.jetbrains.utils.notifyError
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

    override fun doOKAction() {
        if (!okAction.isEnabled) {
            return
        }
        val selectedSecret = secrets.selected()
        val manager = SecretManager(selected)
        val response = manager.getSecret(selectedSecret)
        if (response == null) {
            notifyError(content = message("datagrip.secretsmanager.validation.failed_to_get", selectedSecret?.arn().toString()))
            return
        }
        // Save content and arn so we don't have to retrieve them again
        dbSecret = response.first
        dbSecretArn = response.second
        // validate the content of the secret
        val validationInfo = manager.validateSecret(response.first, selectedSecret?.name() ?: "")
        if (validationInfo != null) {
            val ok = ConfirmCredentialsDialogWrapper(selected.nodeProject, validationInfo).showAndGet()
            if (!ok) {
                return
            }
        }
        super.doOKAction()
    }
}
