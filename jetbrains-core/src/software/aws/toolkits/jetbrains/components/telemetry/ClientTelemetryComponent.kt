// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.components.telemetry

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.ServiceManager
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.aws.toolkits.core.telemetry.*
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import software.aws.toolkits.jetbrains.services.telemetry.ClientTelemetryService
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.net.URI
import java.time.Duration
import java.time.Instant

interface ClientTelemetryComponent : ApplicationComponent

class DefaultClientTelemetryComponent : ClientTelemetryComponent {
    override fun initComponent() {
        // Force the telemetry services to be started on IDE startup.
        ServiceManager.getService(ClientTelemetryService::class.java)
    }

    override fun getComponentName(): String = javaClass.simpleName
}
