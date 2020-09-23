// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import java.nio.file.Path

fun createMockFileChooser(disposable: Disposable, vararg files: Path) {
    val dialog = object : FileChooserDialog {
        override fun choose(toSelect: VirtualFile?, project: Project?): Array<VirtualFile> = toSelect?.let {
            choose(project, it)
        } ?: choose(project)

        override fun choose(project: Project?, vararg toSelect: VirtualFile): Array<VirtualFile> {
            val lfs = LocalFileSystem.getInstance()
            return files.mapNotNull {
                lfs.refreshAndFindFileByIoFile(it.toFile())
            }.toTypedArray()
        }
    }

    val mock = mock<FileChooserFactory> {
        on { createFileChooser(any(), anyOrNull(), anyOrNull()) }.thenReturn(dialog)
    }

    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, mock, disposable)
}
