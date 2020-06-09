// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import software.aws.toolkits.jetbrains.services.iam.IamResources
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.iam.IamUser
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class CreateDataSourcePanel(private val project: Project) {
    lateinit var panel: JPanel
    lateinit var databaseName: JTextField
    lateinit var authSelector: ComboBox<ComboOption<*>>
    lateinit var usernameWrapper: Wrapper
    lateinit var authType: JLabel

    sealed class ComboOption<T : JComponent>(val label: String, val component: T) {
        class Role(project: Project) : ComboOption<ResourceSelector<IamRole>>(
            message("rds.iam_role"),
            ResourceSelector.builder(project)
                .resource { IamResources.LIST_ALL_ROLES }
                .build())

        class User(project: Project) : ComboOption<ResourceSelector<IamUser>>(
            message("rds.iam_user"),
            ResourceSelector.builder(project)
                .resource(IamResources.LIST_ALL_USERS)
                .build()
        )

        class Custom : ComboOption<JBTextField>(message("rds.custom_username"), JBTextField())

        override fun toString() = label
    }

    private fun createUIComponents() {
        authSelector = ComboBox(arrayOf(ComboOption.Role(project), ComboOption.User(project), ComboOption.Custom()))
        authType = JBLabel("TODO IAM User")
    }

    init {
        val initialSelected = authSelector.selected()
        usernameWrapper.setContent(initialSelected?.component)
        authType.text = initialSelected?.label

        authSelector.addItemListener {
            val selected = it.item as? ComboOption<*>
            usernameWrapper.setContent(selected?.component)
            authType.text = selected?.label
        }
    }

    fun getUsername(): String? = when (val selected = authSelector.selected()) {
        is ComboOption.User -> selected.component.selected()?.user?.userName()
        is ComboOption.Role -> selected.component.selected()?.name
        is ComboOption.Custom -> selected.component.text
        null -> throw IllegalStateException("Configuration combo box is null!")
    }
}
