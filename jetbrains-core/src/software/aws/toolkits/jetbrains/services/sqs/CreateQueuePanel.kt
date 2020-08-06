// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import software.aws.toolkits.resources.message
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField

class CreateQueuePanel {
    lateinit var component: JPanel
    lateinit var queueName: JTextField
    lateinit var standardType: JRadioButton
    lateinit var fifoType: JRadioButton

    init {
        queueName.toolTipText = message("sqs.queue.name.tooltip", MAX_LENGTH_OF_QUEUE_NAME)
    }
}
