// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("MissingRecentApi")

package software.aws.toolkits.jetbrains.services.clouddebug

import com.intellij.execution.Platform
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentFactory
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.DeferredLocalTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.executables.CloudDebugExecutable
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutableIfPresent
import software.aws.toolkits.jetbrains.services.clouddebug.CloudDebugTargetEnvironmentFactory.CloudDebugTargetEnvironmentRequest
import software.aws.toolkits.jetbrains.services.clouddebug.resources.CloudDebuggingResources
import software.aws.toolkits.jetbrains.services.ecs.EcsUtils
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

class CloudDebugTargetEnvironmentType : TargetEnvironmentType<CloudDebugTargetEnvironmentConfiguration>("clouddebug") {
    override val displayName: String = "ECS Target"

    override val icon: Icon = AwsIcons.Resources.Ecs.ECS_SERVICE

    override fun createConfigurable(project: Project, config: CloudDebugTargetEnvironmentConfiguration) = CloudDebugTargetConfigurable(project, config)

    override fun createDefaultConfig(): CloudDebugTargetEnvironmentConfiguration = CloudDebugTargetEnvironmentConfiguration()

    override fun createEnvironmentFactory(project: Project, config: CloudDebugTargetEnvironmentConfiguration) = CloudDebugTargetEnvironmentFactory(
        project,
        config
    )

    override fun createSerializer(config: CloudDebugTargetEnvironmentConfiguration): PersistentStateComponent<*> = config
}

@State(name = "ecs-targets", storages = [Storage("aws.xml")])
class CloudDebugTargetEnvironmentConfiguration : TargetEnvironmentConfiguration("clouddebug"),
    PersistentStateComponent<CloudDebugTargetEnvironmentConfiguration.PersistentState> {

    private var state = PersistentState()

    data class PersistentState(
        var cluster: String? = "default",
        var service: String? = "cloud-debug-custom-service",
        var containerName: String? = "custom",
        var workingDirectory: String? = null
    )

    override fun getState(): PersistentState? = state

    override fun loadState(state: PersistentState) {
        this.state = state
    }

    fun workingDirectory(): String = state.workingDirectory ?: throw IllegalStateException("Missing 'workingDirectory'")

    fun cluster(): String = state.cluster ?: throw IllegalStateException("Missing 'cluster'")

    fun service(): String = state.service ?: throw IllegalStateException("Missing 'service'")

    fun containerName(): String = state.containerName ?: throw IllegalStateException("Missing 'containerName'")

    fun commandLineBase() {
        when (val executable = ExecutableManager.getInstance().getExecutableIfPresent<CloudDebugExecutable>()) {
            is ExecutableInstance.Executable -> executable.getCommandLine()
        }
    }
}

class CloudDebugTargetConfigurable(private val project: Project, config: CloudDebugTargetEnvironmentConfiguration) : SearchableConfigurable {
    override fun getId(): String = "clouddebug"

    override fun createComponent(): JComponent? = JLabel("Hello")

    override fun isModified(): Boolean = false

    override fun getDisplayName(): String = "Bob"

    override fun apply() {
        // dun do nothing
    }

}

class CloudDebugTargetEnvironmentFactory(private val project: Project, private val config: CloudDebugTargetEnvironmentConfiguration) :
    TargetEnvironmentFactory {

    private val executable: ExecutableInstance.Executable by lazy {
        val ex = ExecutableManager.getInstance().getExecutableIfPresent<CloudDebugExecutable>()
        if(ex !is ExecutableInstance.Executable) {
            runInEdt {
                notifyError("Can't resolve cloud-debug cli")
            }
            throw RuntimeException("Can't resolve cloud-debug cli")
        }
        ex as ExecutableInstance.Executable
    }

    override fun createRequest(): TargetEnvironmentRequest = CloudDebugTargetEnvironmentRequest()

    override fun getTargetConfiguration(): CloudDebugTargetEnvironmentConfiguration? = config

    override fun getTargetPlatform(): TargetPlatform = TargetPlatform(Platform.UNIX, TargetPlatform.Arch.x64bit)

    override fun prepareRemoteEnvironment(request: TargetEnvironmentRequest, indicator: ProgressIndicator): TargetEnvironment {
        val description = CloudDebuggingResources.describeInstrumentedResource(project, config.cluster(), config.cluster())
        if (description == null || description.status != CloudDebugConstants.INSTRUMENTED_STATUS || description.taskRole.isEmpty()) {
            runInEdt {
                notifyError(message("cloud_debug.execution.failed.not_set_up"))
            }
            throw RuntimeException("Resource not instrumented")
        }
        val role = description.taskRole

        val instrumentResponse = runInstrument(project, executable, config.cluster(), config.service(), role, indicator)

        return CloudDebugTargetEnvironment(project, targetPlatform, request as CloudDebugTargetEnvironmentRequest, executable, instrumentResponse.target, config)
    }

    inner class CloudDebugTargetEnvironmentRequest : TargetEnvironmentRequest {
        private val ports = mutableListOf<PortValue>()

        override fun createUpload(localPath: String): TargetValue<String> {

            return TargetValue.fixed(localPath)
        }

        override fun bindTargetPort(targetPort: Int): TargetValue<Int> = PortValue(targetPort).also { ports.add(it) }

        override fun getTargetPlatform(): TargetPlatform = targetPlatform

        fun cancel() {
            //TODO unwind port mappings
        }
    }
}

class PortValue(private val remotePort: Int) : DeferredLocalTargetValue<Int>(remotePort) {
    private val localPort = 20027

    init {
        resolve(localPort)
    }

    fun binding() = "$localPort:$remotePort"
}

class CloudDebugTargetEnvironment(
    private val project: Project,
    private val targetPlatform: TargetPlatform,
    private val request: CloudDebugTargetEnvironmentRequest,
    private val executable: ExecutableInstance.Executable,
    private val target: String,
    private val config: CloudDebugTargetEnvironmentConfiguration
) : TargetEnvironment {
    override fun getRemotePlatform() = targetPlatform

    override fun getRequest() = request

    override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
        return buildBaseCmdLine(project, executable).withParameters("exec")
            .withParameters("--target")
            .withParameters(target)
            /* TODO remove this when the cli conforms to the contract */
            .withParameters("--selector")
            .withParameters(config.containerName())
            .withParameters(commandLine.getCommandPresentation(this)).createProcess()
    }
}

private fun buildBaseCmdLine(project: Project, executable: ExecutableInstance.Executable) = executable.getCommandLine()
    .withEnvironment(project.activeRegion().toEnvironmentVariables())
    .withEnvironment(project.activeCredentialProvider().resolveCredentials().toEnvironmentVariables())

private fun runInstrument(
    project: Project,
    executable: ExecutableInstance.Executable,
    cluster: String,
    service: String,
    role: String,
    progressIndicator: ProgressIndicator
): InstrumentResponse {
    // first instrument to grab the instrumentation target and ensure connection
    val instrumentCmd = buildBaseCmdLine(project, executable)
        .withParameters("instrument")
        .withParameters("ecs")
        .withParameters("service")
        .withParameters("--cluster")
        .withParameters(EcsUtils.clusterArnToName(cluster))
        .withParameters("--service")
        .withParameters(EcsUtils.originalServiceName(service))
        .withParameters("--iam-role")
        .withParameters(role)
    return CapturingProcessHandler(instrumentCmd).runProcessWithProgressIndicator(progressIndicator).stdout.let {
        CliOutputParser.parseInstrumentResponse(it)
    } ?: throw RuntimeException("CLI provided no response")
}
