// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ExceptionUtil
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import software.aws.toolkits.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.message
import java.nio.file.Path
import java.nio.file.Paths

class ExecutableManager2 {
    private val versionCache = ExecutableVersionCache()

    fun <T : ExecutableType2<V>, V : Version> getExecutable(type: T): Executable<T>? = getExecutablePath(type)?.let { getExecutable(type, it) }
    fun <T : ExecutableType2<V>, V : Version> getExecutable(type: T, path: Path): Executable<T> = Executable(type, path)

    private fun <T : ExecutableType2<*>> getExecutablePath(type: T): Path? {
        // Check if user gave a custom path
        ExecutableSettings.getInstance().getExecutablePath(type)?.let {
            return Paths.get(it)
        }

        return detectExecutable(type)
    }

    fun <T> detectExecutable(type: T): Path? = if (type is AutoResolvable) {
        type.resolve()
    } else {
        null
    }

    fun <T : Version> determineVersion(executable: Executable<ExecutableType2<T>>): T {
        assertIsNonDispatchThread()

        return when (val result = versionCache.getValue(executable)) {
            is Result.Failure -> {
                throw RuntimeException(message("failed to identify version of {0}", executable.type.displayName), result.reason)
            }
            is Result.Success<T> -> {
                result.version
            }
        }
    }

    fun <T : Version> validateCompatability(
        project: Project?,
        path: Path,
        type: ExecutableType2<T>,
        stricterMinVersion: T? = null
    ): Validity = validateCompatability(project, getExecutable(type, path), stricterMinVersion)

    fun <T : Version> validateCompatability(
        project: Project?,
        executable: Executable<ExecutableType2<T>>?,
        stricterMinVersion: T? = null
    ): Validity {
        if (executable == null) {
            return Validity.NotInstalled()
        }

//        return runUnderProgressIfNeeded(project, message("version.progress.title"), false) {
        return runUnderProgressIfNeeded(project, "Validating CLI", false) {
            determineCompatability(executable, stricterMinVersion)
        }
    }

    private fun <T : Version> determineCompatability(executable: Executable<ExecutableType2<T>>, stricterMinVersion: T?): Validity {
        assertIsNonDispatchThread()

        val version = when (val cacheResult = versionCache.getValue(executable)) {
            is Result.Failure -> return Validity.NotInstalled(ExceptionUtil.getMessage(cacheResult.reason))
            is Result.Success -> cacheResult.version
        }

        val baseVersionCompatability = isVersionValid(version, executable.type.supportedVersions())
        if (baseVersionCompatability != Validity.Valid) {
            return baseVersionCompatability
        }

        stricterMinVersion?.let {
            if (stricterMinVersion > version) {
                return Validity.VersionTooOld(stricterMinVersion)
            }
        }

        return Validity.Valid
    }

    companion object {
        @JvmStatic
        fun getInstance(): ExecutableManager2 = service()
    }
}
