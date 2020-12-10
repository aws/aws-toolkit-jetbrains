// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

/**
 * "Light" ides like Goland do not rely on marking folders as source root, so infer it based on
 * the go.mod file
 *
 * @throws IllegalStateException If the contentRoot cannot be located
 */
fun inferSourceRoot(project: Project, virtualFile: VirtualFile): VirtualFile? {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    return projectFileIndex.getContentRootForFile(virtualFile)?.let {
        findChildGoMod(virtualFile.parent, it)
    }
}

private fun findChildGoMod(file: VirtualFile, contentRoot: VirtualFile): VirtualFile =
    when {
        file.isDirectory && file.children.any { !it.isDirectory && it.name == "go.mod" } -> file
        file == contentRoot -> file
        else -> findChildGoMod(file.parent, contentRoot)
    }
