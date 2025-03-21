// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.services.amazonq.calculateIfIamIdentityCenterConnection
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle.message
import java.util.Collections

@Service(Service.Level.APP)
class QRegionProfileManager {

    // Map to store connectionId to its active profile
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, QRegionProfile>(mutableMapOf())
    private val connectionIdToProfileList = mutableMapOf<String, MutableList<QRegionProfile>>()

    fun listRegionProfiles(project: Project): List<QRegionProfile>? =
        // todo replace with real response
        (
            ToolkitConnectionManager.getInstance(project)
                .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
            )
            ?.takeIf { !it.isSono() }
            ?.let { connection ->
                listOf(
                    QRegionProfile(
                        "ACME platform work",
                        "arn:aws:codewhisperer:us-west-2:533267146179:profile/PYWHHDDNKQP9"
                    ),
                    QRegionProfile(
                        "EU Payments Team",
                        "arn:aws:codewhisperer:us-west-2:123122323123:profile/PYWHHDDNKQP9"
                    )
                ).also { profiles ->
                    connectionIdToProfileList[connection.id] = profiles.toMutableList()
                    connectionIdToActiveProfile[connection.id] = connectionIdToProfileList[connection.id]?.get(0)
                }
            }

    fun switchProfile(project: Project, newProfile: QRegionProfile?) {
        calculateIfIamIdentityCenterConnection(project) {
            if (newProfile == null || newProfile.arn.isEmpty()) {
                return@calculateIfIamIdentityCenterConnection
            }
            val oldPro = connectionIdToActiveProfile[it.id]
            if (oldPro != newProfile) {
                newProfile.let { newPro ->
                    connectionIdToActiveProfile[it.id] = newPro
                }
                LOG.debug { "Switch from profile $oldPro to $newProfile" }
                // TODO uncomment this message
//                ApplicationManager.getApplication().messageBus.syncPublisher(QRegionProfileSelectedListener.TOPIC).onProfileSelected(project, newProfile)
                notifyInfo(
                    title = message("action.q.switchProfiles.dialog.panel.text"),
                    content = message("action.q.profile.usage", newProfile.profileName),
                    project = project
                )
            }
        }
    }
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
        private val LOG = getLogger<QRegionProfileManager>()
        fun getInstance(): QRegionProfileManager = service<QRegionProfileManager>()
    }
}
