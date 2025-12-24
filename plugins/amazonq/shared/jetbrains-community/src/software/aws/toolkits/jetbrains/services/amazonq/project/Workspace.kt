// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.q.jetbrains.services.telemetry.ALLOWED_CODE_EXTENSIONS

fun findWorkspaceContentRoots(project: Project): Set<VirtualFile> {
    val contentRoots = mutableSetOf<VirtualFile>()

    project.getBaseDirectories().forEach { contentRoot ->
        if (contentRoot.exists()) {
            contentRoots.add(contentRoot)
        }
    }

    return contentRoots
}

fun findWorkspaceRoot(contentRoots: Set<VirtualFile>): VirtualFile? =
    findCommonAncestor(contentRoots.toList())

fun isContentInWorkspace(path: VirtualFile, contentRoots: Set<VirtualFile>): Boolean =
    contentRoots.any { root -> VfsUtil.isAncestor(root, path, false) }

private fun findCommonAncestor(paths: List<VirtualFile>): VirtualFile? {
    if (paths.isEmpty()) return null
    if (paths.size == 1) return paths.first()

    var commonAncestor: VirtualFile = paths.first()

    while (!paths.all { VfsUtil.isAncestor(commonAncestor, it, false) }) {
        commonAncestor = commonAncestor.parent ?: return null
    }

    return commonAncestor
}

/**
 * Provides extremely limited pattern conversion for global ignore rules.
 */
fun regexTestOf(pattern: String): (path: VirtualFile) -> Boolean =
    pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .let { Regex("^$it$", RegexOption.IGNORE_CASE) }
        .let { fun (path: VirtualFile) = it.matches(path.name) }

/**
 * Provides a lax set of global ignore rules, suitable for agents which may build and test code, avoiding over-filtering.
 *
 * If an agent needs to ignore more files (e.g. to reduce LLM context size), this should be done within the agent, instead of here at the workspace layer.
 */
val additionalGlobalIgnoreRules = setOf(
    ".aws-sam",
    ".gem",
    ".git",
    ".gradle",
    ".hg",
    ".idea",
    ".project",
    ".rvm",
    ".svn",
    "node_modules",
    "build",
    "dist",
).map(::regexTestOf)

/**
 * Provides an extremely strict set of global ignore rules, suitable for purely text-based-sources use cases.
 */
val additionalGlobalIgnoreRulesForStrictSources =
    additionalGlobalIgnoreRules +
        listOf(
            // FIXME: It is incredibly brittle that this source of truth is a "telemetry" component
            // It would be worth considering sniffing text vs arbitrary binary file contents, and making decisions on that basis, rather than file extension.
            fun (path: VirtualFile) = path.extension != null && !ALLOWED_CODE_EXTENSIONS.contains(path.extension),
        )

/**
 * Returns true if workspace source content, false otherwise.
 *
 * Workspace source content is defined as any files/folders which are: 1) within the workspace, and 2) not excluded from version control.
 *
 * @param path The file or folder to check
 * @param changeListManager The VCS change list manager which will be checked for ignored files
 * @param additionalIgnoreTests Additional ignore rules to enforce
 */
fun isWorkspaceSourceContent(
    path: VirtualFile,
    contentRoots: Set<VirtualFile>,
    changeListManager: ChangeListManager,
    additionalIgnoreTests: Iterable<(path: VirtualFile) -> Boolean>,
): Boolean {
    // Exclude paths which are outside the workspace projects:
    if (!isContentInWorkspace(path, contentRoots)) {
        return false
    }

    if (additionalIgnoreTests.any { it(path) }) {
        return false
    }

    // Check whether path is excluded from source control (i.e. gitignore):
    return !changeListManager.isIgnoredFile(path)
}
