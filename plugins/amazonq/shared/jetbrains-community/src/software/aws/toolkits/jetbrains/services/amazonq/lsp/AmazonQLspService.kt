// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.ToNumberPolicy
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.notification.NotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.await
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.apache.http.client.utils.URIBuilder
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.launch.LSPLauncher
import org.slf4j.event.Level
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.core.utils.writeText
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.auth.DefaultAuthCredentialsService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.DefaultModuleDependenciesService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AmazonQLspTypeAdapterFactory
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsExtendedInitializeResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsServerCapabilitiesProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.createExtendedClientMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument.TextDocumentServiceHandler
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.WorkspaceFolderUtil.createWorkspaceFolders
import software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace.WorkspaceServiceHandler
import software.aws.toolkits.jetbrains.services.amazonq.profile.QDefaultServiceConfig
import software.aws.toolkits.jetbrains.services.amazonq.profile.QEndpoints
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata
import software.aws.toolkits.jetbrains.settings.LspSettings
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Telemetry
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

// https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/LSPProcessListener.java
// JB impl and redhat both use a wrapper to handle input buffering issue
internal class LSPProcessListener : ProcessListener {
    private val outputStream = PipedOutputStream()
    private val outputStreamWriter = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
    val inputStream = PipedInputStream(outputStream)

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (ProcessOutputType.isStdout(outputType)) {
            try {
                this.outputStreamWriter.write(event.text)
                this.outputStreamWriter.flush()
            } catch (_: IOException) {
                ExecutionManagerImpl.stopProcess(event.processHandler)
            }
        } else if (ProcessOutputType.isStderr(outputType)) {
            LOG.warn { "LSP process stderr: ${event.text}" }
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        try {
            this.outputStreamWriter.close()
            this.outputStream.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private val LOG = getLogger<LSPProcessListener>()
    }
}

@Service(Service.Level.PROJECT)
class AmazonQLspService(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val _flowInstance = MutableSharedFlow<AmazonQServerInstance>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val instanceFlow = _flowInstance.asSharedFlow().map { it.languageServer }

    private var instance: Deferred<AmazonQServerInstance>
    val capabilities
        get() = instance.getCompleted().initializeResult.getCompleted().capabilities

    val encryptionManager
        get() = instance.getCompleted().encryptionManager
    private val heartbeatJob: Job
    private val restartTimestamps = ArrayDeque<Long>()
    private val restartMutex = Mutex() // Separate mutex for restart tracking

    val rawEndpoint
        get() = instance.getCompleted().rawEndpoint

    // dont allow lsp commands if server is restarting
    private val mutex = Mutex(false)

    private fun start() = cs.async {
        // manage lifecycle RAII-like so we can restart at arbitrary time
        // and suppress IDE error if server fails to start
        var attempts = 0
        while (attempts < 3) {
            try {
                val result = withTimeout(30.seconds) {
                    val instance = AmazonQServerInstance(project, cs).also {
                        Disposer.register(this@AmazonQLspService, it)
                    }
                    // wait for handshake to complete
                    instance.initializeResult.join()

                    instance.also {
                        _flowInstance.emit(it)
                    }
                }

                // withTimeout can throw
                return@async result
            } catch (e: Exception) {
                LOG.warn(e) { "Failed to start LSP server" }
            }
            attempts++
        }

        error("Failed to start LSP server in 3 attempts")
    }

    init {
        instance = start()

        // Initialize heartbeat job
        heartbeatJob = cs.launch {
            while (isActive) {
                delay(5.seconds) // Check every 5 seconds
                val shouldLoop = checkConnectionStatus()
                if (!shouldLoop) {
                    break
                }
            }
        }
    }

    private suspend fun checkConnectionStatus(): Boolean {
        try {
            val currentInstance = mutex.withLock { instance }.await()

            // Check if the launcher's Future (startListening) is done
            // If it's done, that means the connection has been terminated
            if (currentInstance.launcherFuture.isDone) {
                LOG.debug { "LSP server connection terminated, checking restart limits" }
                val canRestart = checkForRemainingRestartAttempts()
                if (!canRestart) {
                    return false
                }
                LOG.debug { "Restarting LSP server" }
                restart()
            } else {
                LOG.debug { "LSP server is currently running" }
            }
        } catch (e: Exception) {
            LOG.debug(e) { "Connection status check failed, checking restart limits" }
            val canRestart = checkForRemainingRestartAttempts()
            if (!canRestart) {
                return false
            }
            LOG.debug { "Restarting LSP server" }
            restart()
        }
        return true
    }

    override fun dispose() {
        heartbeatJob.cancel()
    }

    suspend fun restart() = mutex.withLock {
        // stop if running
        instance.let {
            if (it.isActive) {
                // not even running yet
                return
            }

            try {
                val i = it.await()
                if (i.initializeResult.isActive) {
                    // not initialized
                    return
                }

                Disposer.dispose(i)
            } catch (e: Exception) {
                LOG.info(e) { "Exception while disposing LSP server" }
            }
        }

        instance = start()
    }

    private suspend fun checkForRemainingRestartAttempts(): Boolean = restartMutex.withLock {
        val currentTime = System.currentTimeMillis()

        while (restartTimestamps.isNotEmpty() &&
            currentTime - restartTimestamps.first() > RESTART_WINDOW_MS
        ) {
            restartTimestamps.removeFirst()
        }

        if (restartTimestamps.size < MAX_RESTARTS) {
            restartTimestamps.addLast(currentTime)
            return true
        }

        LOG.info { "Rate limit reached for LSP server restarts. Stop attempting to restart." }

        return false
    }

    suspend fun<T> execute(runnable: suspend AmazonQLspService.(AmazonQLanguageServer) -> T): T {
        val lsp = withTimeout(10.seconds) {
            val holder = mutex.withLock { instance }.await()
            holder.initializeResult.join()

            holder.languageServer
        }
        return runnable(lsp)
    }

    fun<T> executeSync(runnable: suspend AmazonQLspService.(AmazonQLanguageServer) -> T): T =
        runBlocking(cs.coroutineContext) {
            execute(runnable)
        }

    companion object {
        private val LOG = getLogger<AmazonQLspService>()
        private const val MAX_RESTARTS = 5
        private const val RESTART_WINDOW_MS = 3 * 60 * 1000
        fun getInstance(project: Project) = project.service<AmazonQLspService>()

        @Deprecated("Easy to accidentally freeze EDT")
        fun <T> executeIfRunning(project: Project, runnable: AmazonQLspService.(AmazonQLanguageServer) -> T): T? =
            project.serviceIfCreated<AmazonQLspService>()?.executeSync(runnable)

        suspend fun <T> asyncExecuteIfRunning(project: Project, runnable: suspend AmazonQLspService.(AmazonQLanguageServer) -> T): T? =
            project.serviceIfCreated<AmazonQLspService>()?.execute(runnable)

        fun didChangeConfiguration(project: Project) {
            executeIfRunning(project) {
                it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams())
            }
        }
    }
}

private class AmazonQServerInstance(private val project: Project, private val cs: CoroutineScope) : Disposable {
    val encryptionManager = JwtEncryptionManager()

    private val launcher: Launcher<AmazonQLanguageServer>

    val languageServer: AmazonQLanguageServer
        get() = launcher.remoteProxy

    val rawEndpoint: RemoteEndpoint
        get() = launcher.remoteEndpoint

    @Suppress("ForbiddenVoid")
    val launcherFuture: Future<Void>
    private val launcherHandler: KillableProcessHandler
    val initializeResult: Deferred<InitializeResult>

    private fun createClientCapabilities(): ClientCapabilities =
        ClientCapabilities().apply {
            textDocument = TextDocumentClientCapabilities().apply {
                // For didSaveTextDocument, other textDocument/ messages always mandatory
                synchronization = SynchronizationCapabilities().apply {
                    didSave = true
                }
            }

            workspace = WorkspaceClientCapabilities().apply {
                applyEdit = false

                // For workspace folder changes
                workspaceFolders = true

                // For file operations (create, delete)
                fileOperations = FileOperationsWorkspaceCapabilities().apply {
                    didCreate = true
                    didDelete = true
                    didRename = true
                }
            }
        }

    private fun createClientInfo(): ClientInfo {
        val metadata = ClientMetadata.getDefault()
        return ClientInfo().apply {
            name = metadata.awsProduct.toString()
            version = metadata.awsVersion
        }
    }

    private fun createInitializeParams(): InitializeParams =
        InitializeParams().apply {
            processId = ProcessHandle.current().pid().toInt()
            capabilities = createClientCapabilities()
            clientInfo = createClientInfo()
            workspaceFolders = createWorkspaceFolders(project)
            initializationOptions = createExtendedClientMetadata(project)
        }

    init {
        // will cause slow service init, but maybe fine for now. will not block UI since fetch/extract will be under background progress
        val artifact = runBlocking { service<ArtifactManager>().fetchArtifact(project) }.toAbsolutePath()

        // make some network calls for troubleshooting
        listOf(*QEndpoints.listRegionEndpoints().map { it.endpoint }.toTypedArray(), QDefaultServiceConfig.ENDPOINT).forEach { endpoint ->
            try {
                val qUri = URI(endpoint)
                val rtsTrustChain = TrustChainUtil.getTrustChain(qUri)
                val trustRoot = rtsTrustChain.last()
                // ATS is cross-signed against starfield certs: https://www.amazontrust.com/repository/
                if (listOf("Amazon Root CA", "Starfield Technologies").any { trustRoot.subjectX500Principal.name.contains(it) }) {
                    LOG.info { "Trust chain for $endpoint ends with public-like CA with sha256 fingerprint: ${DigestUtil.sha256Hex(trustRoot.encoded)}"}
                } else {
                    LOG.info {
                        """
                            |Trust chain for $endpoint transits private CA:
                            |${buildString {
                                rtsTrustChain.forEach { cert ->
                                    append("Issuer: ${cert.issuerX500Principal}, ")
                                    append("Subject: ${cert.subjectX500Principal}, ")
                                    append("Fingerprint: ${DigestUtil.sha256Hex(cert.encoded)}\n\t")
                                }
                            }}
                        """.trimMargin("|")
                    }
                    LOG.debug { "Full trust chain info for $endpoint: $rtsTrustChain" }
                }
            } catch (e: Exception) {
                LOG.info { "${e.message}: Could not resolve trust chain for $endpoint" }
            }
        }

        val userEnvNodeCaCerts = EnvironmentUtil.getValue("NODE_EXTRA_CA_CERTS")
        // if user has NODE_EXTRA_CA_CERTS in their environment, assume they know what they're doing
        val extraCaCerts = if (!userEnvNodeCaCerts.isNullOrEmpty()) {
            LOG.info { "Skipping injection of IDE trust store, user already defines NODE_EXTRA_CA_CERTS: $userEnvNodeCaCerts"}

            null
        } else {
            try {
                // otherwise include everything the IDE knows about
                val allAcceptedIssuers = CertificateManager.getInstance().trustManager.acceptedIssuers
                val customIssuers = CertificateManager.getInstance().customTrustManager.acceptedIssuers
                LOG.info { "Injecting ${allAcceptedIssuers.size} IDE trusted certificates (${customIssuers.size} from IDE custom manager) into NODE_EXTRA_CA_CERTS" }

                Files.createTempFile("q-extra-ca", ".pem").apply {
                    writeText(
                        TrustChainUtil.certsToPem(CertificateManager.getInstance().trustManager.acceptedIssuers.toList())
                    )
                }.toAbsolutePath().toString()
            } catch (e: Exception) {
                LOG.warn(e) { "Could not inject IDE trust store into NODE_EXTRA_CA_CERTS" }

                null
            }
        }

        val node = if (SystemInfo.isWindows) "node.exe" else "node"
        val nodePath = getNodeRuntimePath(artifact.resolve(node))

        val cmd = NodeExePatcher.patch(nodePath)
            .withParameters(
                LspSettings.getInstance().getArtifactPath() ?: artifact.resolve("aws-lsp-codewhisperer.js").toString(),
                "--stdio",
                "--set-credentials-encryption-key",
            ).withEnvironment(
                buildMap {
                    extraCaCerts?.let {
                        LOG.info { "Starting Flare with NODE_EXTRA_CA_CERTS: $it"}
                        put("NODE_EXTRA_CA_CERTS", it)
                    }

                    // assume default endpoint will pick correct proxy if needed
                    val qUri = URI(QDefaultServiceConfig.ENDPOINT)
                    val proxy = JdkProxyProvider.getInstance().proxySelector.select(qUri)
                        // log if only socks proxy available
                        .firstOrNull { it.type() == Proxy.Type.HTTP }

                    if (proxy != null) {
                        val address = proxy.address()
                        if (address is java.net.InetSocketAddress) {
                            put(
                                "HTTPS_PROXY",
                                URIBuilder("http://${address.hostName}:${address.port}").apply {
                                    val login = HttpConfigurable.getInstance().proxyLogin
                                    if (login != null) {
                                        setUserInfo(login, HttpConfigurable.getInstance().plainProxyPassword)
                                    }
                                }.build().toASCIIString()
                            )
                        }
                    }
                }
            )
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        launcherHandler = KillableColoredProcessHandler.Silent(cmd)
        val inputWrapper = LSPProcessListener()
        launcherHandler.addProcessListener(inputWrapper)
        launcherHandler.startNotify()

        launcher = object : LSPLauncher.Builder<AmazonQLanguageServer>() {
            override fun getSupportedMethods(): Map<String, JsonRpcMethod> =
                super.getSupportedMethods() + AmazonQChatServer.supportedMethods()
        }
            .wrapMessages { consumer ->
                MessageConsumer { message ->
                    if (message is ResponseMessage && message.result is AwsExtendedInitializeResult) {
                        val result = message.result as AwsExtendedInitializeResult
                        AwsServerCapabilitiesProvider.getInstance(project).setAwsServerCapabilities(result.getAwsServerCapabilities())
                    }
                    consumer?.consume(message)
                }
            }
            .setLocalService(AmazonQLanguageClientImpl(project))
            .setRemoteInterface(AmazonQLanguageServer::class.java)
            .configureGson {
                // TODO: maybe need adapter for initialize:
                //   https://github.com/aws/amazon-q-eclipse/blob/b9d5bdcd5c38e1dd8ad371d37ab93a16113d7d4b/plugin/src/software/aws/toolkits/eclipse/amazonq/lsp/QLspTypeAdapterFactory.java

                // otherwise Gson treats all numbers as double which causes deser issues
                it.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                it.registerTypeAdapterFactory(AmazonQLspTypeAdapterFactory())
            }.traceMessages(
                PrintWriter(
                    object : StringWriter() {
                        private val traceLogger = LOG.atLevel(if (isDeveloperMode()) Level.INFO else Level.DEBUG)

                        override fun flush() {
                            traceLogger.log { buffer.toString() }
                            buffer.setLength(0)
                        }
                    }
                )
            )
            .setInput(inputWrapper.inputStream)
            .setOutput(launcherHandler.process.outputStream)
            .create()

        launcherFuture = launcher.startListening()

        initializeResult = cs.async {
            // encryption info must be sent within 5s or Flare process will exit
            encryptionManager.writeInitializationPayload(launcherHandler.process.outputStream)

            val initializeResult = try {
                languageServer.initialize(createInitializeParams()).await()
            } catch (e: Exception) {
                LOG.warn(e) { "LSP initialization failed" }
                null
            }

            // then if this succeeds then we can allow the client to send requests
            if (initializeResult == null) {
                launcherHandler.destroyProcess()
                error("LSP initialization failed")
            }
            languageServer.initialized(InitializedParams())

            initializeResult
        }

        // invokeOnCompletion results in weird lock/timeout error
        initializeResult.asCompletableFuture().handleAsync { lspInitResult, ex ->
            if (ex != null) {
                return@handleAsync
            }

            this@AmazonQServerInstance.apply {
                DefaultAuthCredentialsService(project, encryptionManager).also {
                    Disposer.register(this, it)
                }
                TextDocumentServiceHandler(project).also {
                    Disposer.register(this, it)
                }
                WorkspaceServiceHandler(project, lspInitResult).also {
                    Disposer.register(this, it)
                }
                DefaultModuleDependenciesService(project).also {
                    Disposer.register(this, it)
                }
            }
        }
    }

    /**
     * Resolves the path to a valid Node.js runtime in the following order of preference:
     * 1. Uses the provided nodePath if it exists and is executable
     * 2. Uses user-specified runtime path from LSP settings if available
     * 3. Uses system Node.js if version 18+ is available
     * 4. Falls back to original nodePath with a notification to configure runtime
     *
     * @param nodePath The initial Node.js runtime path to check, typically from the artifact directory
     * @return Path The resolved Node.js runtime path to use for the LSP server
     *
     * Side effects:
     * - Logs warnings if initial runtime path is invalid
     * - Logs info when using alternative runtime path
     * - Shows notification to user if no valid Node.js runtime is found
     *
     * Note: The function will return a path even if no valid runtime is found, but the LSP server
     * may fail to start in that case. The caller should handle potential runtime initialization failures.
     */
    private fun getNodeRuntimePath(nodePath: Path): Path {
        val resolveNodeMetric = { isBundled: Boolean, success: Boolean ->
            Telemetry.languageserver.setup.use {
                it.id("q")
                it.metadata("languageServerSetupStage", "resolveNode")
                it.metadata("credentialStartUrl", getStartUrl(project))
                it.setAttribute("isBundledNode", isBundled)
                it.success(success)
            }
        }

        if (Files.exists(nodePath) && Files.isExecutable(nodePath)) {
            resolveNodeMetric(true, true)
            return nodePath
        }

        // use alternative node runtime if it is not found
        LOG.warn { "Node Runtime download failed. Fallback to user specified node runtime " }
        // attempt to use user provided node runtime path
        val nodeRuntime = LspSettings.getInstance().getNodeRuntimePath()
        if (!nodeRuntime.isNullOrEmpty()) {
            LOG.info { "Using node from $nodeRuntime " }

            resolveNodeMetric(false, true)
            return Path.of(nodeRuntime)
        } else {
            val localNode = locateNodeCommand()
            if (localNode != null) {
                LOG.info { "Using node from ${localNode.toAbsolutePath()}" }

                resolveNodeMetric(false, true)
                return localNode
            }
            notifyInfo(
                "Amazon Q",
                message("amazonqFeatureDev.placeholder.node_runtime_message"),
                project = project,
                listOf(
                    NotificationAction.create(
                        message("codewhisperer.actions.open_settings.title")
                    ) { _, notification ->
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, message("aws.settings.codewhisperer.configurable.title"))
                    },
                    NotificationAction.create(
                        message("codewhisperer.notification.custom.simple.button.got_it")
                    ) { _, notification -> notification.expire() }
                )
            )

            resolveNodeMetric(false, false)
            return nodePath
        }
    }

    /**
     * Locates node executable ≥18 in system PATH.
     * Uses IntelliJ's PathEnvironmentVariableUtil to find executables.
     *
     * @return Path? The absolute path to node ≥18 if found, null otherwise
     */
    private fun locateNodeCommand(): Path? {
        val exeName = if (SystemInfo.isWindows) "node.exe" else "node"

        return PathEnvironmentVariableUtil.findAllExeFilesInPath(exeName)
            .asSequence()
            .map { it.toPath() }
            .filter { Files.isRegularFile(it) && Files.isExecutable(it) }
            .firstNotNullOfOrNull { path ->
                try {
                    val process = ProcessBuilder(path.toString(), "--version")
                        .redirectErrorStream(true)
                        .start()

                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroy()
                        null
                    } else if (process.exitValue() == 0) {
                        val version = process.inputStream.bufferedReader().readText().trim()
                        val majorVersion = version.removePrefix("v").split(".")[0].toIntOrNull()

                        if (majorVersion != null && majorVersion >= 18) {
                            path.toAbsolutePath()
                        } else {
                            LOG.debug { "Node version < 18 found at: $path (version: $version)" }
                            null
                        }
                    } else {
                        LOG.debug { "Failed to get version from node at: $path" }
                        null
                    }
                } catch (e: Exception) {
                    LOG.debug(e) { "Failed to check version for node at: $path" }
                    null
                }
            }
    }

    override fun dispose() {
        if (!launcherFuture.isDone) {
            try {
                languageServer.apply {
                    shutdown().thenRun { exit() }
                }
            } catch (e: Exception) {
                LOG.warn(e) { "LSP shutdown failed" }
                launcherHandler.destroyProcess()
            }
        } else if (!launcherHandler.isProcessTerminated) {
            launcherHandler.destroyProcess()
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQServerInstance>()
    }
}
