// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.amazon.q.jetbrains.settings

import com.intellij.openapi.components.service
import software.amazon.q.jetbrains.settings.ProfilesNotification
import software.amazon.q.jetbrains.settings.UseAwsCredentialRegion
import java.util.UUID

interface AwsSettings {
    var isTelemetryEnabled: Boolean
    var promptedForTelemetry: Boolean
    var useDefaultCredentialRegion: UseAwsCredentialRegion
    var profilesNotification: ProfilesNotification
    var isAutoUpdateEnabled: Boolean
    var isAutoUpdateNotificationEnabled: Boolean
    var isAutoUpdateFeatureNotificationShownOnce: Boolean
    var isQMigrationNotificationShownOnce: Boolean
    val clientId: UUID

    companion object {
        @JvmStatic
        fun getInstance(): AwsSettings = service()
    }
}
