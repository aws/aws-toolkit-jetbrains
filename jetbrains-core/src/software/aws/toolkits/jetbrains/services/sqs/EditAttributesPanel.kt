// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.ui.IdeBorderFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.resources.message
import javax.swing.JPanel
import javax.swing.JTextField

class EditAttributesPanel(private val client: SqsClient, private val queue: Queue) {
    lateinit var component: JPanel
    lateinit var queueAttributesPanel: JPanel
    lateinit var visibilityTimeout: JTextField
    lateinit var messageSize: JTextField
    lateinit var retentionPeriod: JTextField
    lateinit var deliveryDelay: JTextField
    lateinit var waitTime: JTextField

    init {
        component.border = IdeBorderFactory.createTitledBorder(message("sqs.edit.attributes.queue.attributes"))
    }
}
