// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.createDirectories
import software.aws.toolkits.core.utils.createParentDirectories
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.services.cwc.editor.context.project.manifest.ManifestManager
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories

@Service(Service.Level.PROJECT)
class EncoderServer (val project: Project): Disposable {
    val cachePath = Paths.get(PluginPathManager.getPluginHomePath("amazonq")).resolve("projectContext").createDirectories()
// TODO: update the final file names
    private val NODE_RUNNABLE_NAME = "node"
    private val SERVER_DIRECTORY_NAME = "qserver.zip"
    private val isRunning = AtomicBoolean(false)
    private val portManager: EncoderServerPortManager = EncoderServerPortManager.getInstance()
    private lateinit var serverThread: Thread
    private var processOutput: ProcessOutput? = null
    private var numberOfRetry = 0
    lateinit var currentPort: String
    private val manifestManager = ManifestManager.getInstance()

    init {
            ApplicationManager.getApplication().executeOnPooledThread(){
                portManager.getPort().also { currentPort = it }
                downloadArtifactsIfNeeded()
                start()
            }
    }

    private val serverRunnable = Runnable {
        logger.info("encoder port : $currentPort")
        while (numberOfRetry < 10) {
            runCommand(getCommand())
        }
    }

    private fun runCommand (command: GeneralCommandLine) {
        try {
            processOutput = ExecUtil.execAndGetOutput(command)
            logger.info("encoder server output: ${processOutput?.stdout}")
            if(processOutput?.exitCode != 0) {
                throw Exception(processOutput?.stderr)
            }
        } catch (e: Exception){
            logger.info("error running encoder server: $e")
            if(e.stackTraceToString().contains("address already in use")) {
                portManager.addUsedPort(currentPort)
                numberOfRetry++
                currentPort = portManager.getPort()
            } else {
                throw Exception(e.message)
            }
        }
    }

    private fun getCommand (): GeneralCommandLine {
        val map = mutableMapOf<String, String>()
        map["PORT"] = currentPort
        val command = GeneralCommandLine(cachePath.resolve(NODE_RUNNABLE_NAME).toString(), cachePath.resolve("qserver").resolve("extension.js").toString())
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

    fun close() {
        if (isRunning.getAndSet(false)) {
            serverThread.interrupt()
        }
    }

    private fun downloadArtifactsIfNeeded (){
        val nodePath = cachePath.resolve(NODE_RUNNABLE_NAME)
        val zipFilePath = cachePath.resolve(SERVER_DIRECTORY_NAME)
        try {
            if(!Files.exists(nodePath)) {
                val nodeUrl = manifestManager.getManifest()?.let { manifestManager.getNodeUrlFromManifest(it) }
                if(nodeUrl != null) {
                    downloadFromRemote(nodeUrl, nodePath)
                }
            }
            if(!Files.exists(zipFilePath)) {
                val serverUrl = manifestManager.getManifest()?.let { manifestManager.getZipUrlFromManifest(it) }
                if(serverUrl != null) {
                    downloadFromRemote(serverUrl, zipFilePath)
                    val isZipSuccess = unzipFile(zipFilePath, cachePath)
                    if(!isZipSuccess) {
                        throw Exception("error unzipping encoder server zipfile: $zipFilePath")
                    }
                }
            }
        } catch (e: Exception){
            logger.info("error downloading artifacts $e.message")
        }
    }

//    fun unzipFile(zipFilePath: String, outputDirectory: String) {
//        val zipFile = File(zipFilePath)
//        val zipInputStream = ZipInputStream(FileInputStream(zipFile))
//
//        var zipEntry: ZipEntry?
//        while (zipInputStream.nextEntry.also { zipEntry = it } != null) {
//            if (!zipEntry!!.isDirectory) {
//                val fileName = zipEntry!!.name
//                val outputFile = File(outputDirectory, fileName)
//
//                // Create the directory structure if necessary
//                outputFile.parentFile?.mkdirs()
//
//                // Write the file content
//                FileOutputStream(outputFile).use { fos ->
//                    zipInputStream.copyTo(fos)
//                }
//            }
//        }
//
//        zipInputStream.closeEntry()
//        zipInputStream.close()
//    }

    fun unzipFile(zipFilePath: Path, destDir: Path): Boolean {
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


    fun downloadFromRemote(url: String, path: Path) {
        try{
            val response= HttpRequests.request(url).saveToFile(path, null)
        } catch (e: IOException) {
            logger.info("error downloading node from remote ${e.message}")
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
