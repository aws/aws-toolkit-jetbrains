// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams

@Service(Service.Level.PROJECT)
internal class LastValidationService {
    var lastParams: CreateValidationParams? = null

    companion object {
        fun getInstance(project: Project): LastValidationService = project.service()
    }
}
