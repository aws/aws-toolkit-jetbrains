// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import javax.swing.JPanel
import javax.swing.JTextField

class EditAttributesPanel {
    lateinit var component: JPanel
    lateinit var visibilityTimeout: JTextField
    lateinit var messageSize: JTextField
    lateinit var retentionPeriod: JTextField
    lateinit var deliveryDelay: JTextField
    lateinit var waitTime: JTextField
}
