// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "cfnValidateAndDeploySettings", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class ValidateAndDeployPersistence : PersistentStateComponent<ValidateAndDeployPersistenceState> {
    private var state = ValidateAndDeployPersistenceState()

    override fun getState(): ValidateAndDeployPersistenceState = state
    override fun loadState(state: ValidateAndDeployPersistenceState) { this.state = state }

    companion object {
        fun getInstance(project: Project): ValidateAndDeployPersistence = project.service()
    }
}

internal data class ValidateAndDeployPersistenceState(
    var lastTemplatePath: String? = null,
    var lastStackName: String? = null,
    var s3Bucket: String? = null,
    var s3Key: String? = null,
    var onStackFailure: String? = null,
    var includeNestedStacks: Boolean = false,
    var importExistingResources: Boolean = false,
    var tags: String? = null,
    var capabilities: String? = null,
)
