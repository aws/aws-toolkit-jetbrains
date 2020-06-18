// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.datagrip.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry
import software.aws.toolkits.jetbrains.services.secretsmanager.SecretsManagerResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SecretsManagerDialogWrapper(private val project: Project) : DialogWrapper(project) {
    private lateinit var secrets: ResourceSelector<SecretListEntry>

    init {
        title = message("datagrip.secretsmanager.action.title")
        setOKButtonText(message("general.create_button"))
        init()
    }

    override fun createCenterPanel(): JComponent? {
        secrets = ResourceSelector.builder(project)
            .resource(SecretsManagerResources.secrets)
            .customRenderer { entry, renderer -> renderer.append(entry.name()); renderer }
            .build()
        val panel = JPanel(BorderLayout())
        panel.add(secrets)
        return panel
    }

    fun selected() = secrets.selected()
}
