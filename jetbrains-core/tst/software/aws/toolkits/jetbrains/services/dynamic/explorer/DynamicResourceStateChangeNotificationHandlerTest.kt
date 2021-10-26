// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic.explorer

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.cloudcontrol.CloudControlClient
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.credentials.aToolkitCredentialsProvider
import software.aws.toolkits.core.region.anAwsRegion
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.dynamic.CreateResourceFileStatusHandler
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResource
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceStateChangedNotificationHandler
import software.aws.toolkits.jetbrains.services.dynamic.ResourceType

class DynamicResourceStateChangeNotificationHandlerTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManager = MockClientManagerRule()

    private lateinit var cloudControlClient: CloudControlClient
    private lateinit var notificationHandler: DynamicResourceStateChangedNotificationHandler
    private lateinit var connectionSettings: ConnectionSettings
    private lateinit var fileEditorManager: FileEditorManager
    private val resource = DynamicResource(ResourceType("AWS::SampleService::Type", "SampleService", "Type"), "sampleIdentifier")

    @Before
    fun setup() {
        fileEditorManager = FileEditorManager.getInstance(projectRule.project)
        connectionSettings = ConnectionSettings(aToolkitCredentialsProvider(), anAwsRegion())
        cloudControlClient = mockClientManager.create(connectionSettings.region, connectionSettings.credentials)
    }

    @Test
    fun `aa`(){
        notificationHandler =  DynamicResourceStateChangedNotificationHandler(projectRule.project)
    }
}
