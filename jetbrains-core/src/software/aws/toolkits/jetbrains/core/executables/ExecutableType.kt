// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.io.createDirectories
import java.nio.file.Path
import java.nio.file.Paths

interface ExecutableType<VersionScheme> {
    val id: String
    val displayName: String

    fun supportedVersions(): List<VersionRange<VersionScheme>> = emptyList()

    /**
     * Determine the version number of the given path
     */
    fun version(path: Path): VersionScheme

    companion object {
        val EP_NAME = ExtensionPointName<ExecutableType<*>>("aws.toolkit.executable")

        internal fun executables(): List<ExecutableType<*>> = EP_NAME.extensionList

        @JvmStatic
        fun <T : ExecutableType<*>> getExecutable(clazz: Class<T>): T = executables().filterIsInstance(clazz).first()

        inline fun <reified T : ExecutableType<*>> getInstance(): ExecutableType<*> = getExecutable(T::class.java)

        val EXECUTABLE_DIRECTORY: Path = Paths.get(PathManager.getSystemPath(), "aws-static-resources", "executables").createDirectories()
    }
}

/**
 * Represents an executable external program such as a CLI
 *
 * Note: It is recommended that all implementations of this interface are stateless and are an `object`
 */
interface ExecutableType2<VersionScheme : Version> {
    /**
     * ID used to represent the executable in caches and settings. Must be globally unique
     */
    val id: String

    /**
     * Name of the executable for users, e.g. the marketing name of the executable
     */
    val displayName: String

    /**
     * List of supported [VersionRange]. An empty list means any version is supported
     */
    fun supportedVersions(): List<VersionRange<VersionScheme>> = emptyList()

    /**
     * Returns the [Version] for the executable of this type located at the specified location
     */
    fun determineVersion(path: Path): VersionScheme
}

/**
 * Indicates that a [ExecutableType2] can be auto-discovered for the user
 */
interface AutoResolvable {
    /**
     * Attempt to automatically resolve the path
     *
     * @return the resolved path or null if not found
     * @throws Exception if an exception occurred attempting to resolve the path
     */
    fun resolve(): Path?
}

interface Managed : AutoResolvable {
    // TODO: How do we want to handle updating....do we block the usage? Download and then lock its usage while installed?

    /**
     * Attempt to automatically install the tool to Toolkit's managed location
     *
     * @return the installed path. This **must** return the same path as [AutoResolvable.resolve]
     * @throws Exception if the installation failed
     */
    fun install(): Path

    fun isUpdateAvailable(): Boolean
}

@Deprecated("Should not be used, delete after ExecutableManager2 migration")
interface Validatable {
    /**
     * Validate the executable at the given path, beyond being a supported version to ensure this executable is compatible wit the toolkit.
     */
    fun validate(path: Path)
}
