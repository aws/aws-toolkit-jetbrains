// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.lambda.resources.LambdaResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField

class ConfigureLambdaPanel(private val project: Project) {
    lateinit var component: JPanel
    lateinit var functionArn: JTextField
    lateinit var lambdaFunction: ResourceSelector<String>
    lateinit var listButton: JRadioButton
    lateinit var inputButton: JRadioButton
    lateinit var functionContextHelp: JLabel

    private fun createUIComponents() {
        lambdaFunction = ResourceSelector.builder(project).resource(LambdaResources.LIST_FUNCTION_NAMES).build()
    }

    init {
        functionArn.isEnabled = false
        listButton.addActionListener {
            functionArn.isEnabled = inputButton.isSelected
            lambdaFunction.isEnabled = listButton.isSelected
        }
        inputButton.addActionListener {
            functionArn.isEnabled = inputButton.isSelected
            lambdaFunction.isEnabled = listButton.isSelected
        }

        functionContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.configure.lambda.tooltip"))
            installOn(functionContextHelp)
        }
    }
}
