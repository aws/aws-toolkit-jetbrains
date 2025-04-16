// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.CefApp
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.util.HighlightCommand
import software.aws.toolkits.jetbrains.services.amazonq.util.createBrowser
import software.aws.toolkits.jetbrains.settings.MeetQSettings

/*
Displays the web view for the Amazon Q tool window
 */
class Browser(parent: Disposable) : Disposable {

    val jcefBrowser = createBrowser(parent)

    val receiveMessageQuery = JBCefJSQuery.create(jcefBrowser)

    fun init(
        isCodeTransformAvailable: Boolean,
        isFeatureDevAvailable: Boolean,
        isDocAvailable: Boolean,
        isCodeScanAvailable: Boolean,
        isCodeTestAvailable: Boolean,
        highlightCommand: HighlightCommand?,
        activeProfile: QRegionProfile?,
    ) {
        // register the scheme handler to route http://mynah/ URIs to the resources/assets directory on classpath
        CefApp.getInstance()
            .registerSchemeHandlerFactory(
                "http",
                "mynah",
                AssetResourceHandler.AssetResourceHandlerFactory(),
            )

        loadWebView(isCodeTransformAvailable, isFeatureDevAvailable, isDocAvailable, isCodeScanAvailable, isCodeTestAvailable, highlightCommand, activeProfile)
    }

    override fun dispose() {
        Disposer.dispose(jcefBrowser)
    }

    fun component() = jcefBrowser.component

    fun post(message: String) =
        jcefBrowser
            .cefBrowser
            .executeJavaScript("window.postMessage(JSON.stringify($message))", jcefBrowser.cefBrowser.url, 0)

    // Load the chat web app into the jcefBrowser
    private fun loadWebView(
        isCodeTransformAvailable: Boolean,
        isFeatureDevAvailable: Boolean,
        isDocAvailable: Boolean,
        isCodeScanAvailable: Boolean,
        isCodeTestAvailable: Boolean,
        highlightCommand: HighlightCommand?,
        activeProfile: QRegionProfile?,
    ) {
        // setup empty state. The message request handlers use this for storing state
        // that's persistent between page loads.
        jcefBrowser.setProperty("state", "")
        // load the web app
        jcefBrowser.loadHTML(
            getWebviewHTML(
                isCodeTransformAvailable,
                isFeatureDevAvailable,
                isDocAvailable,
                isCodeScanAvailable,
                isCodeTestAvailable,
                highlightCommand,
                activeProfile
            )
        )
    }

    /**
     * Generate index.html for the web view
     * @return HTML source
     */
    private fun getWebviewHTML(
        isCodeTransformAvailable: Boolean,
        isFeatureDevAvailable: Boolean,
        isDocAvailable: Boolean,
        isCodeScanAvailable: Boolean,
        isCodeTestAvailable: Boolean,
        highlightCommand: HighlightCommand?,
        activeProfile: QRegionProfile?,
    ): String {
        val postMessageToJavaJsCode = receiveMessageQuery.inject("JSON.stringify(message)")

        val jsScripts = """
            <script type="text/javascript" src="$WEB_SCRIPT_URI" defer onload="init()"></script>
            <script type="text/javascript">
                const init = () => {
                    mynahUI.createMynahUI(
                        {
                            postMessage: message => {
                                $postMessageToJavaJsCode
                            }
                        },
                        ${MeetQSettings.getInstance().reinvent2024OnboardingCount < MAX_ONBOARDING_PAGE_COUNT},
                        ${MeetQSettings.getInstance().disclaimerAcknowledged},
                        $isFeatureDevAvailable, // whether /dev is available
                        $isCodeTransformAvailable, // whether /transform is available
                        $isDocAvailable, // whether /doc is available
                        $isCodeScanAvailable, // whether /scan is available
                        $isCodeTestAvailable, // whether /test is available
                        ${OBJECT_MAPPER.writeValueAsString(highlightCommand)},
                        "${activeProfile?.profileName.orEmpty()}"
                    );
                }
            </script>        
        """.trimIndent()

        return """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>AWS Q</title>
                    $jsScripts
                </head>
                <body>
                </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val WEB_SCRIPT_URI = "http://mynah/js/mynah-ui.js"
        private const val MAX_ONBOARDING_PAGE_COUNT = 3
        private val OBJECT_MAPPER = jacksonObjectMapper()
    }
}
