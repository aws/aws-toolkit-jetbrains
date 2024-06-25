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
    val cachePath = Paths.get(PluginPathManager.getPluginHomePath("amazonq")).resolve("projectContext").createDirectories()
// TODO: update the final file names
    private val SERVER_DIRECTORY_NAME = "qserver.zip"
    private val isRunning = AtomicBoolean(false)
    private val portManager: EncoderServerPortManager = EncoderServerPortManager.getInstance()
    lateinit var serverThread: Thread
    private var numberOfRetry = AtomicInteger(0)
    lateinit var currentPort: String
    private val manifestManager = ManifestManager.getInstance()
    private val NODE_RUNNABLE_NAME = if (manifestManager.getOs() == "windows") "node.exe" else "node"
    private val MAX_NUMBER_OF_RETRIES: Int = 10
    val key = generateHmacKey()
    var process : Process? = null

    fun downloadArtifactsAndStartServer () {
        portManager.getPort().also { currentPort = it }
        downloadArtifactsIfNeeded()
        start()
    }

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

    private val serverRunnable = Runnable {
        logger.info("encoder server started in port : $currentPort")
        while (numberOfRetry.get() < MAX_NUMBER_OF_RETRIES) {
            runCommand(getCommand())
        }
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

    private fun runCommand (command: GeneralCommandLine) {
        try {
//            val request = getEncryptionRequest()
            process = command.createProcess()
            val output = process?.inputStream?.bufferedReader().use { it?.readText()}
            logger.info("started process: ${output}")
            if(process?.exitValue() != 0) {
                throw Exception(process?.errorStream?.bufferedReader().use { it?.readText() })
            }
        } catch (e: Exception){
            logger.info("error running encoder server: $e")
            if(e.stackTraceToString().contains("address already in use")) {
                portManager.addUsedPort(currentPort)
                numberOfRetry.incrementAndGet()
                currentPort = portManager.getPort()
            } else {
                throw Exception(e.message)
            }
        }
    }

    private fun getCommand (): GeneralCommandLine {
        val map = mutableMapOf<String, String>()
        map["PORT"] = currentPort
        map["CACHE_DIR"] = PluginPathManager.getPluginHomePath("amazonq")
        map["START_AMAZONQ_LSP"] = "true"
        val jsPath = cachePath.resolve("qserver").resolve("extension.js").toString()
        val nodePath = cachePath.resolve(NODE_RUNNABLE_NAME).toString()
        val command = GeneralCommandLine(nodePath, jsPath)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(map)
        return command
    }

    fun isServerRunning(): Boolean = isRunning.get()

    fun start() {
        if (!isRunning.getAndSet(true)) {
            serverThread = Thread(serverRunnable)
            serverThread.start()
        } else {
            throw IllegalStateException("Encoder server is already running!")
        }
    }

    private fun close() {
        if (isRunning.getAndSet(false)) {
            process?.let { ProcessCloseUtil.close(it) }
            serverThread.interrupt()
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
                        val isZipSuccess = unzipFile(zipFilePath, cachePath)
                        if (!isZipSuccess) {
                            throw Exception("error unzipping encoder server zipfile: $zipFilePath")
                        }
                    }
                }
            }
        } catch (e: Exception){
            logger.info("error downloading artifacts $e.message")
        }
    }

    private fun validateHash (expectedHash: String?, input: ByteArray): Boolean {
        if (expectedHash == null) { return false}
        val hash = Base64.getEncoder().encodeToString(
            DigestUtils.sha256(input))
        return hash == expectedHash
    }

    private fun unzipFile(zipFilePath: Path, destDir: Path): Boolean {
        if (!zipFilePath.exists()) return false
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
        return true
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
