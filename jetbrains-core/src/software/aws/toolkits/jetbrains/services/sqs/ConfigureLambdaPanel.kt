// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration
import software.aws.toolkits.jetbrains.services.lambda.resources.LambdaResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel

class ConfigureLambdaPanel(private val project: Project) {
    lateinit var component: JPanel
    lateinit var lambdaFunction: ResourceSelector<FunctionConfiguration>
    lateinit var functionContextHelp: JLabel

    init {
        component.border = IdeBorderFactory.createTitledBorder(message("sqs.configure.lambda.select"))
        functionContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.configure.lambda.tooltip"))
            installOn(functionContextHelp)
        }
    }

    private fun createUIComponents() {
        lambdaFunction = ResourceSelector.builder()
            .resource(LambdaResources.LIST_FUNCTIONS)
            .customRenderer { value, component -> component.append(value.functionName()); component }
            .awsConnection(project)
            .build()
    }
}
