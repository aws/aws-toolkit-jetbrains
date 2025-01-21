// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import software.aws.toolkits.jetbrains.services.amazonq.webview.AssetResourceHandler
import java.io.IOException

fun createBrowser(parent: Disposable): JBCefBrowser {
    val client = JBCefApp.getInstance().createClient().apply {
        setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 5)
    }

    Disposer.register(parent, client)

    return JBCefBrowserBuilder()
        .setClient(client)
        .setOffScreenRendering(true)
        .build()
        .also { browser ->
            browser.jbCefClient.addRequestHandler(
                object : CefRequestHandlerAdapter() {
                    override fun getResourceRequestHandler(
                        browser: CefBrowser,
                        frame: CefFrame,
                        request: CefRequest,
                        isNavigation: Boolean,
                        isDownload: Boolean,
                        requestInitiator: String,
                        disableDefaultHandling: BoolRef
                    ): CefResourceRequestHandler? {
                        return null
//                        println("pewpewpew")
//                        if (request.url.contains("mynah")) {
//                            println("mynahmynahmynah")
//
//                            return object : CefResourceRequestHandlerAdapter() {
//                                override fun getResourceHandler(browser: CefBrowser, frame: CefFrame, request: CefRequest): CefResourceHandler? {
//                                    val resourceUri = request.url ?: return null
//                                    if (!resourceUri.startsWith("http://mynah/")) return null
//
//                                    val resource = resourceUri.replace("http://mynah/", "/assets/")
//                                    val resourceInputStream = this.javaClass.getResourceAsStream(resource)
//
//                                    try {
//                                        resourceInputStream.use {
//                                            if (resourceInputStream != null) {
//                                                println("assetsassetsassets")
//                                                return AssetResourceHandler(resourceInputStream.readAllBytes())
//                                            }
//                                            return null
//                                        }
//                                    } catch (e: IOException) {
//                                        throw RuntimeException(e)
//                                    }
//                                }
//                            }
//                        }
                        return super.getResourceRequestHandler(browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling)
                    }
                }, browser.cefBrowser
            )
        }
}
