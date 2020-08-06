// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

class ResourceFilterManager : Disposable {
    fun getActiveFilters(): Map<String, List<String>> {
        return mapOf(
            "SoftwareType" to listOf("Infrastructure", "Long-Running Server-Side Software")
        )
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): ResourceFilterManager = ServiceManager.getService(project, ResourceFilterManager::class.java)
    }
}
