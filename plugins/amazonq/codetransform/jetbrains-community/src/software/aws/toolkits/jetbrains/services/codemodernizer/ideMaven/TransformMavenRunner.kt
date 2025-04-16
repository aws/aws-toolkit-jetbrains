// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import com.intellij.openapi.projectRoots.ProjectJdkTable

class TransformMavenRunner(val project: Project) {
    private var handler: ProcessHandler? = null
    fun run(parameters: MavenRunnerParameters, settings: MavenRunnerSettings, onComplete: TransformRunnable, isClientSideBuild: Boolean = false) {
        if (isClientSideBuild) {
            // TODO: if we go with this implementation 1) consult UX and
            // 2) run this check much sooner in chat with an appropriate error message if JDK is not found
            val targetJdkPath = CodeModernizerManager.getInstance(project).codeTransformationSession?.sessionContext?.targetJdkPath
                ?: throw RuntimeException("No target JDK path provided by user; cannot run client-side build")
            val jdkTable = ProjectJdkTable.getInstance()
            val targetJdkName = jdkTable.allJdks.find { it.homePath == targetJdkPath }?.name
                ?: throw RuntimeException("Could not find user's target JDK; cannot run client-side build")
            settings.setJreName(targetJdkName)
        }

        FileDocumentManager.getInstance().saveAllDocuments()
        val callback = ProgramRunner.Callback { descriptor: RunContentDescriptor ->
            val handler = descriptor.processHandler
            this.handler = handler
            if (handler == null) {
                onComplete.setExitCode(-1)
                return@Callback
            }
            handler.addProcessListener(object : ProcessAdapter() {
                var output: String = ""

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    // IntelliJ includes some unneeded lines in stdout; exclude those from build logs
                    if (!event.text.startsWith("[IJ]")) {
                        output += event.text
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    onComplete.setExitCode(event.exitCode)
                    onComplete.setOutput(output)
                }
            })
        }
        MavenRunConfigurationType.runConfiguration(project, parameters, null, settings, callback, false)
    }

    fun cancel() {
        this.handler?.destroyProcess()
    }
}
