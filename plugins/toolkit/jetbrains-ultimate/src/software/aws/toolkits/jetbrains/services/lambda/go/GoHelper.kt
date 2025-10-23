// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

// TODO: Re-enable when Go plugin APIs are available in 2025.3
// import com.goide.dlv.DlvDebugProcessUtil
import com.goide.dlv.DlvDisconnectOption
import com.goide.execution.GoRunUtil
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import software.aws.toolkits.jetbrains.services.lambda.steps.SamRunnerStep
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Light" ides like Goland do not rely on marking folders as source root, so infer it based on
 * the go.mod file. This function is based off of the similar PackageJsonUtil#findUpPackageJson
 *
 * @throws IllegalStateException If the contentRoot cannot be located
 */
fun inferSourceRoot(project: Project, virtualFile: VirtualFile): VirtualFile? {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val contentRoot = runReadAction {
        projectFileIndex.getContentRootForFile(virtualFile)
    }

    return contentRoot?.let { root ->
        var file = virtualFile.parent
        while (file != null) {
            if ((file.isDirectory && file.children.any { !it.isDirectory && it.name == "go.mod" })) {
                return file
            }
            // If we go up to the root and it's still not found, stop going up and mark source root as
            // not found, since it will fail to build
            if (file == root) {
                return null
            }
            file = file.parent
        }
        return null
    }
}

object GoDebugHelper {
    // TODO see https://youtrack.jetbrains.com/issue/GO-10775 for "Debugger disconnected unexpectedly" when the lambda finishes
    suspend fun createGoDebugProcess(
        debugHost: String,
        debugPorts: List<Int>,
        context: Context,
    ): XDebugProcessStarter = object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
            // TODO: Re-enable when Go plugin APIs are available in 2025.3
            // val process = DlvDebugProcessUtil.createDlvDebugProcess(session, DlvDisconnectOption.KILL, null, true)
            throw UnsupportedOperationException("Go debugging temporarily disabled in 2025.3 - Go plugin APIs moved")
        }
    }

    fun copyDlv(): String {
        // This can take a target platform, but that pulls directly from GOOS, so we have to walk back up the file tree
        // either way. Goland comes with mac/window/linux dlv since it supports remote debugging, so it is always safe to
        // pull the linux one
        val dlvFolder = GoRunUtil.getBundledDlv(null)?.parentFile?.parentFile?.resolve("linux")
            ?: throw IllegalStateException("Packaged Devle debugger is not found!")
        val directory = Files.createTempDirectory("goDebugger")
        Files.copy(dlvFolder.resolve("dlv").toPath(), directory.resolve("dlv"))
        // Delve that comes packaged with the IDE does not have the executable flag set
        directory.resolve("dlv").toFile().setExecutable(true)
        return directory.toAbsolutePath().toString()
    }
}
