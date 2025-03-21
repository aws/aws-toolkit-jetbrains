// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.gettingstarted

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import software.aws.toolkits.jetbrains.utils.isQWebviewsAvailable

class QGettingStartedPanel(
    val project: Project,
) : Disposable {
    private val webviewContainer = Wrapper()
    var browser: QGettingStartedContent? = null
        private set

    val component = panel {
        row {
            cell(webviewContainer)
                .align(Align.FILL)
        }.resizableRow()
    }

    init {
        if (!isQWebviewsAvailable()) {
            // Fallback to an alternative browser-less solution
            webviewContainer.add(JBTextArea("JCEF not supported"))
            browser = null
        } else {
            browser = QGettingStartedContent(project).also {
                webviewContainer.add(it.component())
            }
        }
    }

    override fun dispose() {
        browser?.let {
            Disposer.dispose(it)
        }
    }
}
