// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import java.util.Collections

@Service(Service.Level.APP)
class QRegionProfileManager {

    // Map to store connectionId to its active profile
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, QRegionProfile>(mutableMapOf())
    private val connectionIdToProfileList = mutableMapOf<String, MutableList<QRegionProfile>>()

    fun activeProfile(project: Project): QRegionProfile? = (
        ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        )?.takeIf { !it.isSono() }
        ?.let {
            connectionIdToActiveProfile[it.id]
        }

    fun shouldDisplayCustomNode(project: Project): Boolean = (
        ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        )?.takeIf { !it.isSono() }
        ?.let { (connectionIdToProfileList[it.id]?.size ?: 0) > 1 } ?: false

    companion object {
        fun getInstance(): QRegionProfileManager = service<QRegionProfileManager>()
    }
}
