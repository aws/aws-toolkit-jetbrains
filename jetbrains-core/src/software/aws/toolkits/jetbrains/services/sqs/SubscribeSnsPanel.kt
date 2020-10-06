// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import software.amazon.awssdk.services.sns.model.Topic
import software.aws.toolkits.jetbrains.services.sns.resources.SnsResources
import software.aws.toolkits.jetbrains.services.sns.resources.getName
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel

class SubscribeSnsPanel(private val project: Project) {
    lateinit var component: JPanel
    lateinit var topicSelector: ResourceSelector<Topic>
    lateinit var selectContextHelp: JLabel

    init {
        component.border = IdeBorderFactory.createTitledBorder(message("sqs.subscribe.sns.select"))
        selectContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.subscribe.sns.select.tooltip"))
            installOn(selectContextHelp)
        }
    }

    private fun createUIComponents() {
        topicSelector = ResourceSelector.builder()
            .resource(SnsResources.LIST_TOPICS)
            .customRenderer { value, component -> component.append(value.getName()); component }
            .awsConnection(project)
            .build()
    }
}
