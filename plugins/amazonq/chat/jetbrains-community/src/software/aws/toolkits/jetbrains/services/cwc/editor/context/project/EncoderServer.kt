// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.process.ProcessCloseUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.createDirectories
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.apache.commons.codec.digest.DigestUtils
import software.aws.toolkits.core.utils.createParentDirectories
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.services.cwc.editor.context.project.manifest.ManifestManager
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path
import java.security.Key
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import javax.crypto.spec.SecretKeySpec

@Service(Service.Level.PROJECT)
class EncoderServer (val project: Project): Disposable {
    private val cachePath = Paths.get(PluginPathManager.getPluginHomePath("amazonq")).resolve("projectContext").createDirectories()
    private val SERVER_DIRECTORY_NAME = "qserver.zip"
    private val isRunning = AtomicBoolean(false)
    private val portManager: EncoderServerPortManager = EncoderServerPortManager.getInstance()
    private var numberOfRetry = AtomicInteger(0)
    lateinit var currentPort: String
    private val manifestManager = ManifestManager.getInstance()
    private val NODE_RUNNABLE_NAME = if (manifestManager.getOs() == "windows") "node.exe" else "node"
    private val MAX_NUMBER_OF_RETRIES: Int = 10
    val key = generateHmacKey()
    lateinit var process : Process

    fun downloadArtifactsAndStartServer () {
        portManager.getPort().also { currentPort = it }
        downloadArtifactsIfNeeded()
        start()
        //
    }

    fun isNodeProcessRunning () = ::process.isInitialized && process.isAlive

    private fun generateHmacKey(): Key {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "HmacSHA256")
    }


    fun encrypt( data: String ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT)
            .build()

        val claimsSet = JWTClaimsSet.Builder()
            .subject(Base64.getUrlEncoder().withoutPadding().encodeToString(data.toByteArray()))
            .build()

        val signedJWT = SignedJWT(header, claimsSet)
        signedJWT.sign(MACSigner(key.encoded))

        return signedJWT.serialize()
    }

    data class EncryptionRequest (
        val version: String = "1.0",
        val mode: String = "JWT",
        val key: String,
    )

    fun getEncryptionRequest () : String {
        val request = EncryptionRequest(key = Base64.getUrlEncoder().withoutPadding().encodeToString(key.encoded))
        return jacksonObjectMapper().writeValueAsString(request)
    }

    private fun runCommand (command: GeneralCommandLine) : Boolean {
        try {
            process = command.createProcess()
            val exitCode = process.waitFor()
            if(exitCode != 0) {
                throw Exception(process?.errorStream?.bufferedReader().use { it?.readText() })
            }
            return true
        } catch (e: Exception){
            logger.info("error running encoder server: $e")
            if(e.stackTraceToString().contains("address already in use")) {
               portManager.addUsedPort(currentPort)
               numberOfRetry.incrementAndGet()
               currentPort = portManager.getPort()
            } else {
                throw Exception(e.message)
            }
            return false
        }
    }

    private fun getCommand (): GeneralCommandLine {
        val threadCount = CodeWhispererSettings.getInstance().getProjectContextIndexThreadCount()
        val map = mutableMapOf<String, String>()
        val cacheDir = cachePath.resolve("cache").createDirectories()
        map["PORT"] = currentPort
        map["CACHE_DIR"] = cacheDir.toString()
        map["START_AMAZONQ_LSP"] = "true"
        map["Q_WORKER_THREADS"] = threadCount.toString()
        val jsPath = cachePath.resolve("qserver").resolve("dist").resolve("extension.js").toString()
        val nodePath = cachePath.resolve(NODE_RUNNABLE_NAME).toString()
        val command = GeneralCommandLine(nodePath, jsPath)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(map)
        return command
    }

    fun start() {
        if (!isRunning.getAndSet(true)) {
            while (numberOfRetry.get() < MAX_NUMBER_OF_RETRIES) {
                val isSuccess = runCommand(getCommand())
                if (isSuccess) {
                    break
                }
            }
        } else {
            throw IllegalStateException("Encoder server is already running!")
        }
    }

    private fun close() {
        if (isRunning.getAndSet(false)) {
            logger.debug(process?.inputStream?.bufferedReader().use { it?.readText() })
            process?.let { ProcessCloseUtil.close(it) }
        }
    }

    private fun downloadArtifactsIfNeeded (){
        val nodePath = cachePath.resolve(NODE_RUNNABLE_NAME)
        val zipFilePath = cachePath.resolve(SERVER_DIRECTORY_NAME)
        val manifest = manifestManager.getManifest()
        try {
            if(!Files.exists(nodePath) && manifest != null) {
                val nodeContent = manifestManager.getNodeContentFromManifest(manifest)
                if(nodeContent?.url != null) {
                    if(validateHash(nodeContent.hashes?.first(), HttpRequests.request(nodeContent.url).readBytes(null))) {
                        downloadFromRemote(nodeContent.url, nodePath)
                    }
                }
            }
            if(!Files.exists(zipFilePath) && manifest != null) {
                val serverContent = manifestManager.getZipContentFromManifest(manifest)
                if(serverContent?.url != null) {
                    if(validateHash(serverContent.hashes?.first(), HttpRequests.request(serverContent.url).readBytes(null))) {
                        downloadFromRemote(serverContent.url, zipFilePath)
                        unzipFile(zipFilePath, cachePath)
                    }
                }
            }
        } catch (e: Exception){
            logger.info("error downloading artifacts $e.message")
        }
    }

    private fun validateHash (expectedHash: String?, input: ByteArray): Boolean {
        if (expectedHash == null) { return false}
        val sha384 = DigestUtils.sha384Hex(input)
        return ("sha384:$sha384") == expectedHash
    }

    private fun unzipFile(zipFilePath: Path, destDir: Path) {
        if (!zipFilePath.exists()) return
        try {
            val zipFile = ZipFile(zipFilePath.toFile())
            zipFile.use { file ->
                file.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .map { zipEntry ->
                        val destPath = destDir.resolve(zipEntry.name)
                        destPath.createParentDirectories()
                        FileOutputStream(destPath.toFile()).use { targetFile ->
                            zipFile.getInputStream(zipEntry).copyTo(targetFile)
                        }
                    }.toList()
            }
        } catch (e: Exception){
            logger.warn("error while unzipping project context artifact: ${e.message}")
        }
    }


    private fun downloadFromRemote(url: String, path: Path) {
        try{
            HttpRequests.request(url).saveToFile(path, null)
        } catch (e: IOException) {
            logger.info("error downloading from remote ${e.message}")
        }
    }

    override fun dispose() {
        close()
    }

    companion object {
        private val logger = getLogger<EncoderServer>()

        fun getInstance(project: Project) = project.service<EncoderServer>()
    }
}
