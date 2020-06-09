// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.rds.model.DBInstance
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.services.rds.postgresEngineType
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class CreateConfigurationDialogWrapper(private val project: Project, private val dbInstance: DBInstance) : DialogWrapper(project) {
    private val panel = CreateConfigurationDialog(project)

    init {
        title = message("rds.configure_source")
        setOKButtonText(message("general.create_button"))

        init()
    }

    override fun createCenterPanel(): JComponent? = panel.panel
    override fun getHelpId(): String? = HelpIds.RDS_SETUP_IAM_AUTH.id
    override fun doValidate(): ValidationInfo? {
        if (getUsername().isNullOrBlank()) {
            return ValidationInfo(message("rds.validation.username"))
        }
        if (dbInstance.engine() == postgresEngineType) {
            if (getDatabaseName().isBlank()) {
                return ValidationInfo(message("rds.validation.postgres_db_name"))
            }
        }
        return null
    }

    fun getUsername(): String? = panel.getUsername()
    fun getDatabaseName(): String = panel.databaseName.text
}
