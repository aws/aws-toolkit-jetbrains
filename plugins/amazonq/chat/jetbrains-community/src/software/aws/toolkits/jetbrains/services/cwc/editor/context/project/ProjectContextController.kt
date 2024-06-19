// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings

@Service(Service.Level.PROJECT)
class ProjectContextController (val project: Project) : Disposable {
    private lateinit var encoderServer: EncoderServer
    private lateinit var projectContextProvider : ProjectContextProvider
    init {
        ApplicationManager.getApplication().executeOnPooledThread() {
            if(CodeWhispererSettings.getInstance().isProjectContextEnabled()) {
                encoderServer = EncoderServer.getInstance(project)
                projectContextProvider = ProjectContextProvider.getInstance(project, encoderServer!!)
                projectContextProvider!!.index()
            }
        }
    }

    fun index() {
        if (encoderServer?.isServerRunning() != true) {
            logger.debug("encoder server is not running, skipping index for project context")
            return
        }
        try {
            projectContextProvider!!.index()
        } catch (e: Exception) {
            logger.warn("error while indexing for project context $e.message")
        }
    }

    fun query(prompt: String) : List<RelevantDocument> {
        if (encoderServer?.isServerRunning() != true) {
            logger.debug("encoder server is not running, skipping query for project context")
            return emptyList()
        }
        try {
            return projectContextProvider!!.query(prompt)
        } catch (e: Exception) {
            return emptyList()
            logger.warn("error while querying for project context $e.message")
        }
    }

    fun updateIndex(filePath: String) {
        if (encoderServer?.isServerRunning() != true) {
            logger.debug("encoder server is not running, skipping update index for project context")
        }
        try {
            return projectContextProvider!!.updateIndex(filePath)
        } catch (e: Exception) {
            logger.warn("error while updating index for project context $e.message")
        }
    }

    override fun dispose() {
        encoderServer?.dispose()
        projectContextProvider?.dispose()
    }

    companion object {
        private val logger = getLogger<ProjectContextController>()
        fun getInstance(project: Project) = project.service<ProjectContextController>()
    }
}
