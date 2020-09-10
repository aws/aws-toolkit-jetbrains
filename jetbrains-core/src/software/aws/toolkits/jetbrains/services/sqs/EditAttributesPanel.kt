// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.ide.HelpTooltip
import com.intellij.ui.components.fields.IntegerField
import software.aws.toolkits.resources.message
import javax.swing.JPanel

class EditAttributesPanel {
    lateinit var component: JPanel
    lateinit var visibilityTimeout: IntegerField
    lateinit var messageSize: IntegerField
    lateinit var retentionPeriod: IntegerField
    lateinit var deliveryDelay: IntegerField
    lateinit var waitTime: IntegerField

    private fun createUIComponents() {
        visibilityTimeout = IntegerField("", MIN_VISIBILITY_TIMEOUT, MAX_VISIBILITY_TIMEOUT)
        HelpTooltip().apply {
            setDescription(message("sqs.edit.attributes.visibility_timeout.tooltip", MIN_VISIBILITY_TIMEOUT, MAX_VISIBILITY_TIMEOUT))
            installOn(visibilityTimeout)
        }
        messageSize = IntegerField("", MIN_MESSAGE_SIZE_LIMIT, MAX_MESSAGE_SIZE_LIMIT)
        HelpTooltip().apply {
            setDescription(message("sqs.edit.attributes.message_size.tooltip", MIN_MESSAGE_SIZE_LIMIT, MAX_MESSAGE_SIZE_LIMIT))
            installOn(messageSize)
        }
        retentionPeriod = IntegerField("", MIN_RETENTION_PERIOD, MAX_RETENTION_PERIOD)
        HelpTooltip().apply {
            setDescription(message("sqs.edit.attributes.retention_period.tooltip", MIN_RETENTION_PERIOD, MAX_RETENTION_PERIOD))
            installOn(retentionPeriod)
        }
        deliveryDelay = IntegerField("", MIN_DELIVERY_DELAY, MAX_DELIVERY_DELAY)
        HelpTooltip().apply {
            setDescription(message("sqs.edit.attributes.delivery_delay.tooltip", MIN_DELIVERY_DELAY, MAX_DELIVERY_DELAY))
            installOn(deliveryDelay)
        }
        waitTime = IntegerField("", MIN_WAIT_TIME, MAX_WAIT_TIME)
        HelpTooltip().apply {
            setDescription(message("sqs.edit.attributes.wait_time.tooltip", MIN_WAIT_TIME, MAX_WAIT_TIME))
            installOn(waitTime)
        }
    }
}
