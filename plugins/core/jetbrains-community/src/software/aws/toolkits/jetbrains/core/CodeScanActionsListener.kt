// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.util.messages.Topic
import java.util.EventListener

interface CodeScanActionsListener : EventListener {
    fun sendIssueToQ(issueDescription: String?, issueCode: String?)

    companion object {
        @Topic.AppLevel
        val EXPLAIN_CODESCAN_ISSUE_WITH_Q = Topic.create("Explain Issue with Q ", CodeScanActionsListener::class.java)
    }
}
