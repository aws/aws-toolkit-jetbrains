// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import org.apache.commons.codec.digest.DigestUtils
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.model.ZipCreationResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.createPatternIgnorerFromFileName
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import kotlin.io.path.relativeTo

class FeatureDevSessionContext(val project: Project) {

    private var _projectRoot = project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")
    fun getProjectZip(): ZipCreationResult {
        val zippedProject = runReadAction { zipFiles(projectRoot) }
        val checkSum256: String = Base64.getEncoder().encodeToString(DigestUtils.sha256(FileInputStream(zippedProject)))
        return ZipCreationResult(zippedProject, checkSum256, zippedProject.length())
    }

    private fun zipFiles(projectRoot: VirtualFile): File {
        val shouldIgnore = createPatternIgnorerFromFileName(".gitignore", projectRoot)

        return createTemporaryZipFile {
            VfsUtil.collectChildrenRecursively(projectRoot).forEach { virtualFile ->
                if (virtualFile.isFile && !shouldIgnore(virtualFile.path)) {
                    val relativePath = virtualFile.toNioPath().relativeTo(projectRoot.toNioPath())
                    it.putNextEntry(relativePath.toString(), virtualFile.toNioPath())
                }
            }
        }.toFile()
    }

    var projectRoot: VirtualFile
        set(newRoot) {
            _projectRoot = newRoot
        }
        get() = _projectRoot
}
