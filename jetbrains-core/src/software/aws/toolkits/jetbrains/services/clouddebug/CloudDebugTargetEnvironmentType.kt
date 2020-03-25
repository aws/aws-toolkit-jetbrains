// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("MissingRecentApi")

package software.aws.toolkits.jetbrains.services.clouddebug

import com.intellij.build.BuildViewManager
import com.intellij.execution.Platform
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentFactory
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.DeferredLocalTargetValue
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.isDirectory
import icons.AwsIcons
import io.netty.util.concurrent.CompleteFuture
import org.jetbrains.concurrency.AsyncPromise
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.tryOrThrow
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.executables.CloudDebugExecutable
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutableIfPresent
import software.aws.toolkits.jetbrains.services.clouddebug.CloudDebugTargetEnvironmentFactory.CloudDebugTargetEnvironmentRequest
import software.aws.toolkits.jetbrains.services.clouddebug.execution.DefaultMessageEmitter
import software.aws.toolkits.jetbrains.services.clouddebug.resources.CloudDebuggingResources
import software.aws.toolkits.jetbrains.services.ecs.EcsUtils
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        var workingDirectory: String? = "/tmp/wsp"
    )

    override fun getState(): PersistentState? = state

    override fun loadState(state: PersistentState) {
        this.state = state
    }

    fun workingDirectory(): String = state.workingDirectory ?: "/tmp/wsp" //throw IllegalStateException("Missing 'workingDirectory'")

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
        if (ex !is ExecutableInstance.Executable) {
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
        val messageEmitter = DefaultMessageEmitter.createRoot(ServiceManager.getService(project, BuildViewManager::class.java), "Resolve CLI")
        CloudDebugResolver.validateOrUpdateCloudDebug(project, messageEmitter, null)
        val description = CloudDebuggingResources.describeInstrumentedResource(project, config.cluster(), config.service())
        if (description == null || description.status != CloudDebugConstants.INSTRUMENTED_STATUS || description.taskRole.isEmpty()) {
            runInEdt {
                notifyError(message("cloud_debug.execution.failed.not_set_up"))
            }
            throw RuntimeException("Resource not instrumented")
        }
        val role = description.taskRole

        val instrumentResponse = runInstrument(project, executable, config.cluster(), config.service(), role, indicator)

        (request as CloudDebugTargetEnvironmentRequest).target = instrumentResponse.target

        return CloudDebugTargetEnvironment(
            project,
            targetPlatform,
            request,
            executable,
            instrumentResponse.target,
            config
        )
    }

    inner class CloudDebugTargetEnvironmentRequest : TargetEnvironmentRequest {
        private val ports = mutableListOf<PortValue>()
        internal var target: String? = null

        override fun createUpload(localPath: String): TargetValue<String> {
            val tgt = target ?: throw IllegalStateException("Container target not set")
            return PathValue(localPath, config.workingDirectory(), tgt, config.containerName(), project, executable)
        }

        override fun bindTargetPort(targetPort: Int): TargetValue<Int> = PortValue(targetPort).also { ports.add(it) }

        override fun getTargetPlatform(): TargetPlatform = this@CloudDebugTargetEnvironmentFactory.targetPlatform

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

class PathValue(
    localPath: String,
    remoteWorkingDirectory: String,
    target: String,
    container: String,
    project: Project,
    executable: ExecutableInstance.Executable
) : DeferredTargetValue<String>(localPath) {
    init {
        val remotePath = "$remoteWorkingDirectory/${UUID.randomUUID()}"

        val createPath =  buildBaseCmdLine(project, executable).withParameters("exec")
            .withParameters("--target")
            .withParameters(target)
            /* TODO remove this when the cli conforms to the contract */
            .withParameters("--selector")
            .withParameters(container)
            .withParameters("mkdir", "-p", remotePath)

        val copyCommand = buildBaseCmdLine(project, executable).withParameters("--verbose")
            .withParameters("--json")
            .withParameters("copy")
            .withParameters("--src")
            .withParameters(localPath)
            .withParameters("--dest")
            .withParameters("remote://$target://$container://$remotePath")

        val future = CompletableFuture<Nothing>()
        runCommand(createPath, {future.completeExceptionally(RuntimeException(it))}) {
            runCommand(copyCommand, {future.completeExceptionally(RuntimeException(it))}) {
                val resolvedLocalPath = Paths.get(localPath)
                val resolvedPath = "$remotePath/${localPath.substringAfterLast("/")}" //if (resolvedLocalPath.isDirectory()) { remotePath } else {  }
                resolve(resolvedPath)
                future.complete(null)
            }
        }
        future.get(5000, TimeUnit.MILLISECONDS)
    }

    private fun runCommand(command: GeneralCommandLine, error: (String) -> Unit = {}, callback: () -> Unit) {
        LOG.info { "About to run command: $command" }
        ProcessHandlerFactory.getInstance().createProcessHandler(command).apply {
            addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    LOG.info { "Process Terminated (${event.exitCode}): [${event.source}] ${event.text}" }
                    if (event.exitCode == 0) {
                        callback()
                    } else {
                        val msg = "Command $command failed: ${event.text}"
                        error(msg)
                        (targetValue as AsyncPromise).setError(msg)
                    }
                }

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    LOG.info { event.text }
                }
            })
        }.startNotify()
    }

    companion object {
        private val LOG = getLogger<PathValue>()
    }
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
        val command = LOG.tryOrThrow("Failed to get command") {
            commandLine.collectCommandsSynchronously().toTypedArray()
        }

        return buildBaseCmdLine(project, executable).withParameters("exec")
            .withParameters("--target")
            .withParameters(target)
            /* TODO remove this when the cli conforms to the contract */
            .withParameters("--selector")
            .withParameters(config.containerName())
            .withParameters(*command).createProcess()
    }

    companion object {
        val LOG = getLogger<CloudDebugTargetEnvironment>()
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
