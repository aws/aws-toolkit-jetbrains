// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonqDoc.SUPPORTED_DIAGRAM_EXT_SET
import software.aws.toolkits.jetbrains.services.amazonqDoc.SUPPORTED_DIAGRAM_FILE_NAME_SET

class DocSessionContext(project: Project, maxProjectSizeBytes: Long? = null) : FeatureDevSessionContext(project, maxProjectSizeBytes) {

    /**
     * Ensure diagram files are not ignored
     */
    override fun getAdditionalGitIgnoreBinaryFilesRules(): Set<String> {
        val ignoreRules = super.getAdditionalGitIgnoreBinaryFilesRules()
        val diagramExtRulesInGitIgnoreFormatSet = SUPPORTED_DIAGRAM_EXT_SET.map { "*.$it" }.toSet()
        return ignoreRules - diagramExtRulesInGitIgnoreFormatSet
    }

    /**
     * Ensure diagram files are not filtered
     */
    override fun isFileExtensionAllowed(file: VirtualFile): Boolean {
        if (super.isFileExtensionAllowed(file)) {
            return true
        }

        return file.extension != null && SUPPORTED_DIAGRAM_FILE_NAME_SET.contains(file.name)
    }
}
