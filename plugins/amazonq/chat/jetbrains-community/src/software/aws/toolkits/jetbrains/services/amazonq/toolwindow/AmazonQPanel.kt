// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefApp
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactHelper
import software.aws.toolkits.jetbrains.services.amazonq.webview.Browser
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.JButton

class AmazonQPanel(private val parent: Disposable, val project: Project) {
    private val webviewContainer = Wrapper()
    val browser = CompletableFuture<Browser>()

    val component = panel {
        row {
            cell(webviewContainer)
                .align(Align.FILL)
        }.resizableRow()

        // Button to show the web debugger for debugging the UI:
        if (isDeveloperMode()) {
            row {
                cell(
                    JButton("Show Web Debugger").apply {
                        addActionListener(
                            ActionListener {
                                // Code to be executed when the button is clicked
                                // Add your logic here

                                browser.get().jcefBrowser.openDevtools()
                            },
                        )
                    },
                )
                    .align(AlignX.CENTER)
                    .align(AlignY.BOTTOM)
            }
        }
    }

    init {
        init()
    }

    fun disposeAndRecreate() {
        webviewContainer.removeAll()
        val toDispose = browser.get()
        init()
        if (toDispose != null) {
            Disposer.dispose(toDispose)
        }
    }

    private fun init() {
        if (!JBCefApp.isSupported()) {
            // Fallback to an alternative browser-less solution
            if (AppMode.isRemoteDevHost()) {
                webviewContainer.add(JBTextArea("Amazon Q chat is not supported in remote dev environment."))
            } else {
                webviewContainer.add(JBTextArea("JCEF not supported"))
            }
            browser.complete(null)
        } else {
            val loadingPanel = JBLoadingPanel(null, parent, 0)
            val wrapper = Wrapper()
            loadingPanel.startLoading()

            loadingPanel.add(JBPanelWithEmptyText().withEmptyText("Wait for chat to be ready"))
            webviewContainer.add(wrapper)
            wrapper.setContent(loadingPanel)

            ApplicationManager.getApplication().executeOnPooledThread {
                val webUri = ArtifactHelper().getLatestLocalLspArtifact().resolve("amazonq-ui.js").toUri()
                loadingPanel.stopLoading()
                runInEdt {
                    browser.complete(
                        Browser(parent, webUri, project).also {
                            wrapper.setContent(it.component())
                        }
                    )
                }
            }
        }
    }
}
