// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.PythonProjectGenerator
import com.jetbrains.python.remote.PyProjectSynchronizer
import com.jetbrains.python.sdk.PythonSdkType
import software.amazon.awssdk.services.lambda.model.Runtime
import java.io.File

class SamInitProjectBuilderPyCharm : PythonProjectGenerator<PyNewProjectSettings>() {
    val settingsPanel = SamInitDirectoryBasedSettingsPanel(SAM_TEMPLATES)

    override fun getName() = SamModuleType.ID

    // "More Options" panel
    override fun getSettingsPanel(baseDir: File?) = settingsPanel.component

    override fun getLogo() = SamModuleType.ICON

    override fun configureProject(project: Project, baseDir: VirtualFile, settings: PyNewProjectSettings, module: Module, synchronizer: PyProjectSynchronizer?) {
        val langLevel = PythonSdkType.getLanguageLevelForSdk(settings.sdk)
        val runtime =
            when {
                langLevel.isPython2 -> Runtime.PYTHON2_7
                langLevel.isPy3K -> Runtime.PYTHON3_6
                else -> Runtime.UNKNOWN_TO_SDK_VERSION
            }

        val template = settingsPanel.templateField.selectedItem as SamProjectTemplate
        template.build(runtime, baseDir)

        super.configureProject(project, baseDir, settings, module, synchronizer)
    }
}