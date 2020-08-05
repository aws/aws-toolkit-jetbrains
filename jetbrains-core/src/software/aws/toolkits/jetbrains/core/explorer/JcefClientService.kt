// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.future.await
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRequestContext
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import software.aws.toolkits.core.credentials.AwsConsoleUrlFactory
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*

@Suppress("MissingRecentApi")
class JcefClientService {
    val map = concurrentMapOf<ToolkitCredentialsProvider, CompletableFuture<JBCefBrowser>>()

    suspend fun getBrowserInternal(creds: ToolkitCredentialsProvider, region: AwsRegion): JBCefBrowser {
        val client = map[creds]?.await()
        if (client == null || client.isDisposed) {
            val new = createBrowser(creds, region)
            map[creds] = new.second
            return new.first
        }
        return client
    }

    suspend fun getBrowser(creds: ToolkitCredentialsProvider, region: AwsRegion): JBCefBrowser {
        return map[creds]?.await()!!
    }

    private fun createBrowser(creds: ToolkitCredentialsProvider, region: AwsRegion): Pair<JBCefBrowser, CompletableFuture<JBCefBrowser>> {
        // need to isolate browsers. this can be accomplished by passing in new CefRequestContexts. The defaults appear to keep all state in memory
        val jbCefClient = JBCefApp.getInstance().createClient()
        val cefBrowser = jbCefClient.cefClient.createBrowser("about:blank", false, false, CefRequestContext.createContext(null))
        val browser = JBCefBrowser(cefBrowser, jbCefClient)
        Disposer.register(browser, jbCefClient)
//        createBrowserFrame(creds, region, browser)
        val future = CompletableFuture<JBCefBrowser>()
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser2: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (browser2!!.url.contains("about:blank")) {
                    cefBrowser.loadURL(AwsConsoleUrlFactory.getSigninUrl(creds.resolveCredentials(), null, region))
                }
                if (browser2!!.url.contains("console")) {
                    println("loadend: ${browser2.url}")
                    browser.jbCefClient.removeLoadHandler(this, browser.cefBrowser)
                    future.complete(browser)
                }
            }

            override fun onLoadError(browser: CefBrowser?, frame: CefFrame?, errorCode: CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                println("failed: $failedUrl")
            }
        }, browser.cefBrowser)

        return browser to future
    }

    private fun createBrowserFrame(creds: ToolkitCredentialsProvider, region: AwsRegion, browser: JBCefBrowser) {
        val activeFrame = IdeFrameImpl.getActiveFrame()!!
        val bounds: Rectangle = activeFrame.graphicsConfiguration.bounds
        val frame: JFrame = IdeFrameImpl()
        frame.title = "console: ${creds.displayName}"
        frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        frame.setBounds(bounds.width / 8, bounds.height / 8, (bounds.width / 1.5).toInt(), (bounds.height / 1.5).toInt())
        frame.layout = BorderLayout()
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                Disposer.dispose(browser)
            }
        })
        frame.add(browser.component, BorderLayout.CENTER)

        val urlBar = JTextField(browser.cefBrowser.url)
        urlBar.isEditable = false
        frame.add(urlBar, BorderLayout.NORTH)
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
            BrowserUtil.browse(AwsConsoleUrlFactory.getSigninUrl(creds.resolveCredentials(), urlBar.text, region))
        }

        controlPanel.add(openButton)
        frame.add(controlPanel, BorderLayout.SOUTH)

        frame.isVisible = true
        frame.requestFocus()
    }

    companion object {
        fun getInstance(): JcefClientService = ServiceManager.getService(JcefClientService::class.java)
    }
}
