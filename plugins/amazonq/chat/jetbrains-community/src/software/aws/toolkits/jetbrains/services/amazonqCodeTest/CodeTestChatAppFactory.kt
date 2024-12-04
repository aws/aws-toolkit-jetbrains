// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppFactory

class CodeTestChatAppFactory(private val cs: CoroutineScope) : AmazonQAppFactory {
    override fun createApp(project: Project) = CodeTestChatApp(cs)
}
