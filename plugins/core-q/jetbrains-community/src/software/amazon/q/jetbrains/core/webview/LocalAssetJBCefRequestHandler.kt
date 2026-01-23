// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.webview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBase
import contrib.org.intellij.images.editor.impl.jcef.JBCefLocalRequestHandler
import contrib.org.intellij.images.editor.impl.jcef.JBCefStreamResourceHandler
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.io.InputStream
import java.net.URI
import java.net.URLConnection
import kotlin.io.byteInputStream

class LocalAssetJBCefRequestHandler(jbCefBrowser: JBCefBrowserBase) : JBCefLocalRequestHandler(PROTOCOL, AUTHORITY), Disposable {
    init {
        jbCefBrowser.jbCefClient.addRequestHandler(this, jbCefBrowser.cefBrowser)

        Disposer.register(jbCefBrowser, this)
    }

    private val wildcardHandlers = mutableMapOf<String, (String) -> CefResourceHandler?>()

    private fun streamHandler(path: String, stream: InputStream) =
        JBCefStreamResourceHandler(
            stream,
            if (path.endsWith(".wasm") == true) "application/wasm" else URLConnection.getFileNameMap().getContentTypeFor(path),
            this
        )

    fun addWildcardHandler(prefix: String, handler: (String) -> InputStream?) {
        wildcardHandlers[prefix] = { path ->
            handler(path)?.let { stream ->
                streamHandler(path, stream)
            }
        }
    }

    fun addResource(path: String, stream: InputStream?) =
        addResource(path) {
            stream?.let { streamHandler(path, it) }
        }

    fun createResource(path: String, stream: InputStream?) =
        createResource(path) {
            stream?.let { streamHandler(path, it) }
        }

    fun createResource(path: String, content: String) =
        createResource(path, content.byteInputStream())

    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler {
        val resource = request?.url?.let { URI(it).path.trim('/') }
        val handler = wildcardHandlers.entries.firstOrNull { (k, _) -> resource?.startsWith(k) == true }
        if (handler != null) {
            return resourceHandlerWrapper { path -> handler.value(path) }
        }
        return super.getResourceRequestHandler(browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling)
    }

    override fun dispose() {
    }

    companion object {
        const val PROTOCOL = "https"
        const val AUTHORITY = "toolkitasset"
    }
}
