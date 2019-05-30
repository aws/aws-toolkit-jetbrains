// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.welcome

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.ui.wizard.SamProjectTemplate

class WelcomePageStartup : StartupActivity {
    override fun runActivity(project: Project) {
        if (!ApplicationManager.getApplication().isUnitTestMode && AwsSettings.getInstance().lastInstalledVersion != AwsToolkit.PLUGIN_VERSION) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openTextEditor(OpenFileDescriptor(project, WelcomePageFile), true)
                ?: SamProjectTemplate.LOG.warn { "Failed to open welcome page" }
            AwsSettings.getInstance().lastInstalledVersion = AwsToolkit.PLUGIN_VERSION
        }
    }
}