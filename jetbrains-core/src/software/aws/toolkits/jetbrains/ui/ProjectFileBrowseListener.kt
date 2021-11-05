// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.utils.tryOrNull

/** Similar to [com.intellij.openapi.ui.TextBrowseFolderListener], but tries to set the initial directory to the assumed project root **/
open class ProjectFileBrowseListener @JvmOverloads constructor(
    project: Project,
    chooserDescriptor: FileChooserDescriptor,
    private val onChosen: ((VirtualFile) -> Unit)? = null
) : TextBrowseFolderListener(chooserDescriptor, project) {
    override fun getInitialFile(): VirtualFile? {
        // we use this listener on ComboboxWithBrowseButton, but it will never return valid text since it's not a JTextField
        val text = tryOrNull { componentText } ?: return null
        if (text.isEmpty()) {
            val file = project?.guessProjectDir()
            if (file != null) {
                return file
            }
        }
        return super.getInitialFile()
    }

    override fun onFileChosen(chosenFile: VirtualFile) {
        onChosen?.invoke(chosenFile) ?: super.onFileChosen(chosenFile)
    }
}
