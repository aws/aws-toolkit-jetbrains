// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.aws.toolkits.core.ClientConnectionSettings
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import java.time.Duration

/**
 * Save Amazon Q Profile Resource Cache
 */
object QProfileResources {
    /**
     * save available Q Profile list as cache with default duration 60 s。
     */
    val LIST_REGION_PROFILES = object : Resource.Cached<List<QRegionProfile>>() {
        override val id: String = "amazonq.allProfiles"

        override fun fetch(connectionSettings: ClientConnectionSettings<*>): List<QRegionProfile> {
            val mappedProfiles = QEndpoints.listRegionEndpoints().flatMap { (regionKey, _) ->
                val awsRegion = AwsRegionProvider.getInstance()[regionKey] ?: return@flatMap emptyList()
                val client = AwsClientManager
                    .getInstance()
                    .getClient(CodeWhispererRuntimeClient::class, connectionSettings.withRegion(awsRegion))

                try {
                    val profiles = client.listAvailableProfilesPaginator {}
                        .profiles()
                        .map { p -> QRegionProfile(arn = p.arn(), profileName = p.profileName() ?: "<no name>") }
                    LOG.debug { "Found profiles for region $regionKey : $profiles" }

                    profiles
                } catch (e: Exception) {
                    LOG.warn(e) { "Failed to list Q profiles for region $regionKey" }
                    emptyList()
                }
            }
            return mappedProfiles
        }

        override fun expiry(): Duration = Duration.ofSeconds(60)
    }

    private val LOG = getLogger<QProfileResources>()
}
