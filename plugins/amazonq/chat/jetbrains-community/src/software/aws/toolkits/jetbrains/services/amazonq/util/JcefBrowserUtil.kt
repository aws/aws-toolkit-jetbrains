// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder

fun createBrowser(parent: Disposable): JBCefBrowserBase {
    val client = JBCefApp.getInstance().createClient()

    Disposer.register(parent, client)

    return JBCefBrowserBuilder()
        .setClient(client)
        .setOffScreenRendering(true)
        .build()
}
