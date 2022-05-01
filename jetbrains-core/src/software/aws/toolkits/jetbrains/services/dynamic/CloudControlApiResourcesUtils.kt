// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object CloudControlApiResourcesUtils {
    fun getResourceFile(project: Project, dynamicResourceIdentifier: DynamicResourceIdentifier): VirtualFile? =
        try {
            FileEditorManager.getInstance(project).openFiles.first {
                it is ViewEditableDynamicResourceVirtualFile && it.dynamicResourceIdentifier == dynamicResourceIdentifier
            }
        } catch (e: NoSuchElementException) {
            null
        }
}
