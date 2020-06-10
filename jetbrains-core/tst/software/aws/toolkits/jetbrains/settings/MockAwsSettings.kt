// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import java.util.UUID

class MockAwsSettings : AwsSettings {
    override var isTelemetryEnabled: Boolean = false
    override var promptedForTelemetry: Boolean = false
    override var useDefaultCredentialRegion: UseAwsCredentialRegion = UseAwsCredentialRegion.Prompt
    override val clientId: UUID = UUID.randomUUID()
}
