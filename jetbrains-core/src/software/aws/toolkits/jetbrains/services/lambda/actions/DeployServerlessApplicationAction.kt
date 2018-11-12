// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.cloudformation.CloudFormationTemplate
import software.aws.toolkits.jetbrains.services.lambda.deploy.DeployServerlessApplicationDialog
import software.aws.toolkits.resources.message

class DeployServerlessApplicationAction : AnAction(
        message("serverless.application.deploy"),
        null,
        AwsIcons.Resources.LAMBDA_FUNCTION) {
    private var templateYamlRegex = Regex("template\\.y[a]?ml", RegexOption.IGNORE_CASE)

    override fun actionPerformed(e: AnActionEvent) {

        val project = e.getRequiredData(PlatformDataKeys.PROJECT)

        val samTemplateFile = getSamTemplateFile(e) ?: throw Exception("Could not detect template file")
        val template = CloudFormationTemplate.parse(project, samTemplateFile)

        // TODO : Validate the template file (this is likely too slow to do in update())

        DeployServerlessApplicationDialog(project, template.parameters()).show()
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isVisible = getSamTemplateFile(e) != null
    }

    /**
     * Determines the relevant Sam Template, returns null if one can't be found.
     */
    private fun getSamTemplateFile(e: AnActionEvent): VirtualFile? {
        val virtualFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY) ?: return null
        val virtualFile = virtualFiles.singleOrNull() ?: return null

        if (templateYamlRegex.matches(virtualFile.name)) {
            return virtualFile
        }

        // If the module node was selected, see if there is a template file in the top level folder
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return null
        if (virtualFile == project.baseDir) {
            return virtualFile.children.singleOrNull { templateYamlRegex.matches(it.name) }
        }

        return null
    }
}
