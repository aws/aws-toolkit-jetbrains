// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ExceptionUtil
import software.aws.toolkits.jetbrains.core.executables.AutoResolvable
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import software.aws.toolkits.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.message
import java.nio.file.Path
import java.nio.file.Paths

class ToolManager {
    private val versionCache = ToolVersionCache()

    fun <T : ToolType<V>, V : Version> getExecutable(type: T): Tool<T>? = getExecutablePath(type)?.let { getExecutable(type, it) }
    fun <T : ToolType<V>, V : Version> getExecutable(type: T, path: Path): Tool<T> = Tool(type, path)

    private fun <T : ToolType<*>> getExecutablePath(type: T): Path? {
        // Check if user gave a custom path
        ToolSettings.getInstance().getExecutablePath(type)?.let {
            return Paths.get(it)
        }

        return detectExecutable(type)
    }

    fun <T> detectExecutable(type: T): Path? = if (type is AutoResolvable) {
        type.resolve()
    } else {
        null
    }

    fun <T : Version> determineVersion(tool: Tool<ToolType<T>>): T {
        assertIsNonDispatchThread()

        return when (val result = versionCache.getValue(tool)) {
            is ToolVersionCache.Result.Failure -> {
                throw RuntimeException(message("failed to identify version of {0}", tool.type.displayName), result.reason)
            }
            is ToolVersionCache.Result.Success<T> -> {
                result.version
            }
        }
    }

    fun <T : Version> validateCompatability(
        project: Project?,
        path: Path,
        type: ToolType<T>,
        stricterMinVersion: T? = null
    ): Validity = validateCompatability(project, getExecutable(type, path), stricterMinVersion)

    fun <T : Version> validateCompatability(
        project: Project?,
        tool: Tool<ToolType<T>>?,
        stricterMinVersion: T? = null
    ): Validity {
        if (tool == null) {
            return Validity.NotInstalled()
        }

//        return runUnderProgressIfNeeded(project, message("version.progress.title"), false) {
        return runUnderProgressIfNeeded(project, "Validating CLI", false) {
            determineCompatability(tool, stricterMinVersion)
        }
    }

    private fun <T : Version> determineCompatability(tool: Tool<ToolType<T>>, stricterMinVersion: T?): Validity {
        assertIsNonDispatchThread()

        val version = when (val cacheResult = versionCache.getValue(tool)) {
            is ToolVersionCache.Result.Failure -> return Validity.NotInstalled(ExceptionUtil.getMessage(cacheResult.reason))
            is ToolVersionCache.Result.Success -> cacheResult.version
        }

        val baseVersionCompatability = isVersionValid(version, tool.type.supportedVersions())
        if (baseVersionCompatability !is Validity.Valid) {
            return baseVersionCompatability
        }

        stricterMinVersion?.let {
            if (stricterMinVersion > version) {
                return Validity.VersionTooOld(stricterMinVersion)
            }
        }

        return Validity.Valid(version)
    }

    companion object {
        @JvmStatic
        fun getInstance(): ToolManager = service()
    }
}
