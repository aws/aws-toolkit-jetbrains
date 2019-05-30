// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.welcome

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import icons.AwsIcons
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.components.telemetry.AnActionWrapper
import software.aws.toolkits.resources.message
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

class WelcomePageEditorProvider : FileEditorProvider {

    override fun getEditorTypeId(): String = "Aws.WelcomePageEditorProvider"

    override fun accept(project: Project, file: VirtualFile): Boolean = file is WelcomePageFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = WelcomePageEditor()

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class WelcomePageEditor : UserDataHolderBase(), FileEditor {
    private val view: WelcomePagePanel = WelcomePagePanel()

    override fun isModified(): Boolean = false

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getName(): String = "WelcomePageEditor"

    override fun setState(state: FileEditorState) {}

    override fun getComponent(): JComponent = view.contentPanel

    override fun getPreferredFocusedComponent(): JComponent? = null

    override fun selectNotify() {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun deselectNotify() {}

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

    override fun isValid(): Boolean = true

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun dispose() {}
}

object WelcomePageFile : LightVirtualFile(message("welcome_page.title"), WelcomePageFileType(), "")

class WelcomePageFileType : FakeFileType() {
    override fun getName(): String = "AwsWelcomePageFileType"

    override fun getDescription(): String = "AWS Toolkit Welcome Page"

    override fun isMyFileType(file: VirtualFile): Boolean = file is WelcomePageFile

    override fun getIcon(): Icon? = AwsIcons.Logos.AWS
}

class OpenWelcomePageAction(title: String) : AnActionWrapper(title) {
    override fun doActionPerformed(e: AnActionEvent) {
        e.project?.let {
            val fileEditorManager = FileEditorManager.getInstance(it)
            fileEditorManager.openEditor(OpenFileDescriptor(it, WelcomePageFile), true)
        } ?: LOG.warn { "Cannot find project from the event!" }
    }

    companion object {
        private val LOG = getLogger<OpenWelcomePageAction>()
    }
}