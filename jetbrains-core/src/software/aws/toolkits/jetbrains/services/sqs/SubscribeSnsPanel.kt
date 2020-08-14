// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import software.aws.toolkits.jetbrains.services.sqs.resources.SnsResources
import software.aws.toolkits.jetbrains.services.sqs.resources.SnsTopic
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel

class SubscribeSnsPanel(private val project: Project) {
    lateinit var component: JPanel
    lateinit var topicSelector: ResourceSelector<SnsTopic>
    lateinit var topicArn: JBTextField
    lateinit var selectContextHelp: JLabel

    init {
        selectContextHelp.icon = AllIcons.General.ContextHelp
        topicSelector.addActionListener {
            if (topicSelector.selected() != null) {
                topicArn.text = topicSelector.selected()?.arn
            }
        }
        topicArn.emptyText.text = message("sqs.subscribe.sns.example.arn")
    }

    private fun createUIComponents() {
        topicSelector = ResourceSelector.builder(project).resource(SnsResources.LIST_TOPIC_NAMES).build()
    }
}
