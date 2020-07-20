// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.ui.EditorTextField
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.services.sqs.Queue
import javax.swing.JPanel
import javax.swing.JTextField

class SendMessagePane(
    private val client: SqsClient,
    private val queue: Queue
) {
    lateinit var component: JPanel
    lateinit var inputText: EditorTextField
    lateinit var fifoComponent: JPanel
    lateinit var deduplicationID: JTextField
    lateinit var groupID: JTextField
}
