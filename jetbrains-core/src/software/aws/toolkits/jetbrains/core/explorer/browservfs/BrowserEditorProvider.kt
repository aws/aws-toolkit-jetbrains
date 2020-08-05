// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.browservfs

import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.runBlocking
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import software.aws.toolkits.core.credentials.AwsConsoleUrlFactory
import software.aws.toolkits.jetbrains.core.explorer.JcefClientService
import software.aws.toolkits.jetbrains.core.explorer.browservfs.BrowserEditorProvider.Companion.EDITOR_TYPE_ID
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class BrowserEditorProvider : FileEditorProvider, PossiblyDumbAware {
    override fun accept(project: Project, file: VirtualFile) = file is BrowserVirtualFile

    override fun isDumbAware() = true

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val file = file as BrowserVirtualFile
        val browser = runBlocking { JcefClientService.getInstance().getBrowserInternal(file.creds, file.region) }
        return BrowserEditor(browser, file)
    }

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    companion object {
        const val EDITOR_TYPE_ID = "Browser Panel"
    }
}

class BrowserEditor(private val browser: JBCefBrowser, private val browserFile: BrowserVirtualFile) : FileEditorBase() {
    val panel = JPanel()
    init {
        panel.layout = BorderLayout()
        panel.add(browser.component, BorderLayout.CENTER)

        val urlBar = JTextField(browser.cefBrowser.url)
        urlBar.isEditable = false
        panel.add(urlBar, BorderLayout.NORTH)
        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                urlBar.text = frame!!.url
            }
        }, browser.cefBrowser)

        val controlPanel = JPanel()
        controlPanel.layout = BoxLayout(controlPanel, BoxLayout.X_AXIS)
        val openButton = JButton("Open in Browser")

        openButton.addActionListener {
            println(urlBar.text)
            BrowserUtil.browse(AwsConsoleUrlFactory.getSigninUrl(browserFile.creds.resolveCredentials(), urlBar.text, browserFile.region))
        }

        controlPanel.add(openButton)
        panel.add(controlPanel, BorderLayout.SOUTH)
    }

    override fun getComponent(): JComponent = panel

    override fun getName(): String = EDITOR_TYPE_ID

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun dispose() {
        Disposer.dispose(browser)
    }
}
