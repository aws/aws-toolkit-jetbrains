@file:Suppress("all")

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// adapted from https://github.com/JetBrains/intellij-community/blob/54429f3ba00c695c22d09e164135b0713f2cfc0f/platform/ui.jcef/jcef/utils/JBCefLocalRequestHandler.kt

package contrib.org.intellij.images.editor.impl.jcef

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.net.URL

/**
 * Handles local protocol-specific CEF resource requests for a defined `protocol` and `authority`.
 *
 * This class implements a mechanism to serve protocol-specific resources based on mappings provided
 * through the `addResource` function. Only requests matching the configured protocol and authority are processed,
 * while others are rejected.
 *
 * @param myProtocol The protocol to handle (e.g., "http", "file").
 * @param myAuthority The authority of the requests (e.g., "localhost", "mydomain").
 */
open class JBCefLocalRequestHandler(
    private val myProtocol: String,
    private val myAuthority: String,
) : CefRequestHandlerAdapter() {
    private val myResources: MutableMap<String, () -> CefResourceHandler?> = HashMap()

    private val REJECTING_RESOURCE_HANDLER: CefResourceHandler = object : CefResourceHandlerAdapter() {
        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            callback.cancel()
            return false
        }
    }

    private val RESOURCE_REQUEST_HANDLER = resourceHandlerWrapper { path ->
        myResources[path]?.let { it() }
    }

    protected fun resourceHandlerWrapper(handler: (String) -> CefResourceHandler?): CefResourceRequestHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler {
                val url = URL(request.url)
                url.protocol
                if (!url.protocol.equals(myProtocol) || !url.authority.equals(myAuthority)) {
                    return REJECTING_RESOURCE_HANDLER
                }
                return try {
                    val path = url.path.trim('/')
                    handler(path) ?: REJECTING_RESOURCE_HANDLER
                } catch (e: RuntimeException) {
                    println(e.message)
                    REJECTING_RESOURCE_HANDLER
                }
            }
        }

    fun addResource(resourcePath: String, resourceProvider: () -> CefResourceHandler?) {
        val normalisedPath = resourcePath.trim('/')
        myResources[normalisedPath] = resourceProvider
    }

    fun createResource(resourcePath: String, resourceProvider: () -> CefResourceHandler?): String {
        val normalisedPath = resourcePath.trim('/')
        myResources[normalisedPath] = resourceProvider
        return "$myProtocol://$myAuthority/$normalisedPath"
    }

    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler {
        return RESOURCE_REQUEST_HANDLER
    }
}
