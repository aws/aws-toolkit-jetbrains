// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import com.intellij.util.io.write
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import software.aws.toolkits.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.message
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.Period
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Service for managing external tools such as CLIs.
 */
class ToolManager @NonInjectable constructor(private val clock: Clock = Clock.systemUTC()) {
    private val versionCache = ToolVersionCache()
    private val updateCheckCache = ConcurrentHashMap<ManagedToolType<*>, Instant>()
    private val managedToolLock = ReentrantLock()

    /**
     * Returns a reference to the requested tool.
     *
     * The returned Tool will first be checked if the user has set a path for it explicitly. If they have not, we will attempt to detect it for them if
     * supported.
     * Note: The tool may not have been checked for [Validity] yet. Caller must make the check before using the tool.
     */
    fun <V : Version> getTool(type: ToolType<V>): Tool<ToolType<V>>? {
        // Check if user gave a custom path
        ToolSettings.getInstance().getExecutablePath(type)?.let {
            return Tool(type, Paths.get(it))
        }

        if (type is ManagedToolType<V>) {
            val managedToolPath = checkForInstalledTool(type)
            if (managedToolPath != null) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    checkForUpdates(type)
                }
                return managedToolPath
            }
        }

        return detectTool(type)?.let {
            Tool(type, it)
        }
    }

    fun <V : Version> getOrInstallTool(type: ManagedToolType<V>, project: Project? = null): Tool<ToolType<V>> {
        val existingTool = getTool(type)
        if (existingTool != null) {
            return existingTool
        }

        val progressManager = ProgressManager.getInstance()
        return progressManager.runProcessWithProgressSynchronously(
            ThrowableComputable { installTool(type, progressManager.progressIndicator) },
            message("executableCommon.installing", type.displayName),
            /* canBeCanceled */ false,
            project
        )
    }

    /**
     * Returns a reference to the requested tool located at the specified path.
     *
     * Note: The tool may not have been checked for [Validity] yet. Caller must make the check before using the tool.
     */
    fun <V : Version> getToolForPath(type: ToolType<V>, toolExecutablePath: Path): Tool<ToolType<V>> = Tool(type, toolExecutablePath)

    /**
     * Attempts to detect the requested Tool on the local file system.
     *
     * @return Either the path to the tool if found, or null if the tool can't be found or not auto-detectable
     */
    fun <V : Version> detectTool(type: ToolType<V>): Path? {
        if (type is AutoDetectableToolType<*>) {
            return type.resolve()
        }
        return null
    }

    /**
     * Checks the [Validity] of the specified tool at the specified path. An optional stricter version can be specified to override the minimum version.
     *
     * If called on a UI thread, a modal dialog is shown while the validation is in progress to avoid UI lock-ups
     */
    fun <T : Version> validateCompatability(
        path: Path,
        type: ToolType<T>,
        stricterMinVersion: T? = null,
        project: Project? = null
    ): Validity = validateCompatability(getToolForPath(type, path), stricterMinVersion, project)

    /**
     * Checks the [Validity] of the specified tool instance. An optional stricter version can be specified to override the minimum version.
     *
     * If called on a UI thread, a modal dialog is shown while the validation is in progress to avoid UI lock-ups
     */
    fun <T : Version> validateCompatability(
        tool: Tool<ToolType<T>>?,
        stricterMinVersion: T? = null,
        project: Project? = null
    ): Validity {
        if (tool == null) {
            return Validity.NotInstalled()
        }

        return runUnderProgressIfNeeded(project, message("executableCommon.validating", tool.type.displayName), false) {
            determineCompatability(tool, stricterMinVersion)
        }
    }

    private fun <T : Version> determineCompatability(tool: Tool<ToolType<T>>, stricterMinVersion: T?): Validity {
        assertIsNonDispatchThread()

        val version = when (val cacheResult = versionCache.getValue(tool)) {
            is ToolVersionCache.Result.Failure -> return Validity.NotInstalled(ExceptionUtil.getMessage(cacheResult.reason))
            is ToolVersionCache.Result.Success -> cacheResult.version
        }

        val baseVersionCompatability = tool.type.supportedVersions()?.let {
            version.isValid(it)
        } ?: Validity.Valid(version)

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

    internal fun <V : Version> checkForUpdates(type: ManagedToolType<V>, project: Project? = null) {
        assertIsNonDispatchThread()

        val now = Instant.now(clock)
        val lastCheck = updateCheckCache.put(type, now) ?: Instant.MIN
        val needCheck = lastCheck.plus(UPDATE_CHECK_INTERVAL).isBefore(now)
        if (!needCheck) {
            LOG.debug { "Checked for newer versions of ${type.id} recently, nothing to do" }
            return
        }

        val latestVersion = type.determineLatestVersion()
        type.supportedVersions()?.let {
            val latestVersionCompatibility = latestVersion.isValid(it)
            if (latestVersionCompatibility !is Validity.Valid) {
                LOG.warn { "Latest version of ${type.id} is not compatible with the toolkit: ${type.supportedVersions()}" }
                return
            }
        }

        val currentInstall = checkForInstalledTool(type)
        val currentVersion = currentInstall?.let { type.determineVersion(it.path) }
        if (currentVersion != null && currentVersion >= latestVersion) {
            LOG.debug { "Current version of ${type.id} is greater than or equal to latest version, nothing to do" }
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                message("executableCommon.updating", type.displayName), /* canBeCanceled */
                false,
                PerformInBackgroundOption.ALWAYS_BACKGROUND
            ) {
                override fun run(indicator: ProgressIndicator) {
                    installTool(type, indicator)
                }
            }
        )
    }

    private fun <V : Version> installTool(type: ManagedToolType<V>, indicator: ProgressIndicator?): Tool<ToolType<V>> {
        assertIsNonDispatchThread()

        try {
            val latestVersion = type.determineLatestVersion()

            type.supportedVersions()?.let {
                val latestVersionCompatibility = latestVersion.isValid(it)
                if (latestVersionCompatibility !is Validity.Valid) {
                    throw IllegalStateException(message("executableCommon.latest_not_compatible", type.displayName, it.displayValue()))
                }
            }

            return performInstall(type, latestVersion, indicator)
        } catch (e: Exception) {
            throw IllegalStateException(message("executableCommon.failed_install", type.displayName), e)
        }
    }

    private fun <V : Version> performInstall(type: ManagedToolType<V>, version: V, indicator: ProgressIndicator?): Tool<ToolType<V>> {
        assertIsNonDispatchThread()

        val downloadDir = Files.createTempDirectory(type.id)
        try {
            val downloadFile = type.downloadVersion(version, downloadDir, indicator)

            val versionString = version.displayValue()
            val installLocation = managedToolInstallDir(type.id, versionString)

            return ProgressIndicatorUtils.computeWithLockAndCheckingCanceled(
                managedToolLock,
                50,
                TimeUnit.MILLISECONDS,
                ThrowableComputable {
                    // Default the indicator to be indeterminate, a tool type can change it back if it can track status better
                    indicator?.isIndeterminate = true

                    type.installVersion(downloadFile, installLocation, indicator)

                    // Check install before updating marker
                    val tool = type.toTool(installLocation)

                    managedToolMarkerFile(type.id).write(versionString)

                    return@ThrowableComputable tool
                }
            )
        } finally {
            FileUtil.delete(downloadDir)
        }
    }

    private fun <V : Version> checkForInstalledTool(type: ManagedToolType<V>): Tool<ToolType<V>>? {
        val markerFile = managedToolMarkerFile(type.id).takeIf { it.exists() } ?: return null
        val markerVersion = managedToolLock.withLock {
            markerFile.readText()
        }
        // Avoid dir traversal
        if (markerVersion.contains("/") || markerVersion.contains("\\")) {
            return null
        }
        val installLocation = managedToolInstallDir(type.id, markerVersion).takeIf { it.exists() } ?: return null
        return type.toTool(installLocation)
    }

    companion object {
        private val LOG = getLogger<ToolManager>()
        private val UPDATE_CHECK_INTERVAL = Period.ofDays(1)
        private const val VERSION_MARKER_FILENAME = "VERSION"
        private val MANAGED_TOOL_INSTALL_ROOT by lazy {
            Paths.get(PathManager.getSystemPath(), "aws-static-resources").resolve("tools").createDirectories()
        }

        internal fun managedToolMarkerFile(toolId: String) = MANAGED_TOOL_INSTALL_ROOT.resolve(toolId).resolve(VERSION_MARKER_FILENAME)
        internal fun managedToolInstallDir(toolId: String, version: String) = MANAGED_TOOL_INSTALL_ROOT.resolve(toolId).resolve(version)

        fun getInstance(): ToolManager = service()
    }
}
