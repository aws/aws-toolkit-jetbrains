// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefApp
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.webview.Browser
import java.awt.event.ActionListener
import javax.swing.JButton

class AmazonQPanel(private val parent: Disposable) {
    private val webviewContainer = Wrapper()
    var browser: Browser? = null
        private set

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

                                browser?.jcefBrowser?.openDevtools()
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
        val toDispose = browser
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
            browser = null
        } else {
            browser = Browser(parent).also {
                webviewContainer.add(it.component())
            }
        }
    }
}
