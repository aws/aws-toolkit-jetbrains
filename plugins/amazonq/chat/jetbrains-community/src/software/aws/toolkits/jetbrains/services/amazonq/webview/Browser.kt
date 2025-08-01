// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.CefApp
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsServerCapabilitiesProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.util.HighlightCommand
import software.aws.toolkits.jetbrains.services.amazonq.util.createBrowser
import software.aws.toolkits.jetbrains.settings.MeetQSettings
import java.net.URI

/*
Displays the web view for the Amazon Q tool window
 */

class Browser(parent: Disposable, private val webUri: URI, val project: Project) : Disposable {

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

        loadWebView(
            isCodeTransformAvailable,
            isFeatureDevAvailable,
            isDocAvailable,
            isCodeScanAvailable,
            isCodeTestAvailable,
            highlightCommand,
            activeProfile
        )
    }

    override fun dispose() {
        Disposer.dispose(jcefBrowser)
    }

    fun component() = jcefBrowser.component

    fun postChat(command: FlareUiMessage) = postChat(Gson().toJson(command))

    @Deprecated("shouldn't need this version")
    fun postChat(message: String) {
        jcefBrowser
            .cefBrowser
            .executeJavaScript("window.postMessage($message)", jcefBrowser.cefBrowser.url, 0)
    }

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
        jcefBrowser.jbCefClient.addDragHandler({ browser, dragData, mask ->
            true // Allow drag operations
        }, jcefBrowser.cefBrowser)
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
        val connectorAdapterPath = "http://mynah/js/connectorAdapter.js"
        generateQuickActionConfig()
        // https://github.com/highlightjs/highlight.js/issues/1387
        // language=HTML
        val jsScripts = """
            <script type="text/javascript" charset="UTF-8" src="$connectorAdapterPath"></script>
            <script type="text/javascript" charset="UTF-8" src="$webUri" defer onload="init()"></script>
            
            <script type="text/javascript">
                
                const init = () => {
                    const hybridChatConnector = connectorAdapter.initiateAdapter(
                     ${MeetQSettings.getInstance().reinvent2024OnboardingCount < MAX_ONBOARDING_PAGE_COUNT},
                     ${MeetQSettings.getInstance().disclaimerAcknowledged},
                     $isFeatureDevAvailable,
                     $isCodeTransformAvailable,
                     $isDocAvailable,
                     $isCodeScanAvailable,
                     $isCodeTestAvailable,
                     {
                            postMessage: message => {
                                $postMessageToJavaJsCode
                            }
                        },
                    
                     "${activeProfile?.profileName.orEmpty()}")
                    const qChat = amazonQChat.createChat(
                        {
                            postMessage: message => {
                                $postMessageToJavaJsCode
                            }
                        }, 
                        {
                        agenticMode: true,
                        quickActionCommands: [],
                        modelSelectionEnabled: true,
                        disclaimerAcknowledged: ${MeetQSettings.getInstance().disclaimerAcknowledged},
                        pairProgrammingAcknowledged: ${MeetQSettings.getInstance().pairProgrammingAcknowledged}
                        },
                        hybridChatConnector,
                        ${CodeWhispererFeatureConfigService.getInstance().getFeatureConfigJsonString()}                     
                    );
                    
                    window.handleNativeDrop = function(filePath) {
                        const parsedFilePath = JSON.parse(filePath);
                        const contextArray = parsedFilePath.map(fullPath => {
                            const fileName = fullPath.split(/[\\/]/).pop();
                            return {
                                command: fileName,
                                label: 'image',
                                route: [fullPath],
                                description: fullPath
                            };
                        });
                        qChat.addCustomContextToPrompt(qChat.getSelectedTabId(), contextArray);
                    };
                      
                    window.handleNativeNotify = function(errorMessages) {
                        const messages = JSON.parse(errorMessages);
                        let message = messages.join('\n');
                        qChat.updateStore(qChat.getSelectedTabId(), {
                            promptInputStickyCard: {
                                messageId: 'image-verification-banner',
                                header: {
                                    icon: 'warning',
                                    iconStatus: 'warning',
                                    body: '### Invalid Image',
                                },
                                body: message,
                                canBeDismissed: true,
                            },
                        })
                    };
                    
                    window.setDragAndDropVisible = function(visibility) {
                        const parsedVisibility = JSON.parse(visibility);
                        qChat.setDragOverlayVisible(qChat.getSelectedTabId(), parsedVisibility)
                    };
                    
                    window.resetTopBarClicked = function() {
                        qChat.resetTopBarClicked(qChat.getSelectedTabId())
                    };
                }
            </script>        
        """.trimIndent()

        addQuickActionCommands(
            isCodeTransformAvailable,
            isFeatureDevAvailable,
            isDocAvailable,
            isCodeTestAvailable,
            isCodeScanAvailable,
            highlightCommand,
            activeProfile
        )
        return """
            <!DOCTYPE html>
            <style>
                    body,
                    html {
                        background-color: var(--mynah-color-bg);
                        color: var(--mynah-color-text-default);
                        height: 100vh;
                        width: 100%%;
                        overflow: hidden;
                        margin: 0;
                        padding: 0;
                    }
                    .mynah-ui-icon-plus,
                    .mynah-ui-icon-cancel {
                        -webkit-mask-size: 155% !important;
                        mask-size: 155% !important;
                        mask-position: center;
                        scale: 60%;
                    }
                    .code-snippet-close-button i.mynah-ui-icon-cancel,
                    .mynah-chat-item-card-related-content-show-more i.mynah-ui-icon-down-open {
                        -webkit-mask-size: 195.5% !important;
                        mask-size: 195.5% !important;
                        mask-position: center;
                        aspect-ratio: 1/1;
                        width: 15px;
                        height: 15px;
                        scale: 50%
                    }
                    .mynah-ui-icon-tabs {
                        -webkit-mask-size: 102% !important;
                        mask-size: 102% !important;
                        mask-position: center;
                    }
                    textarea:placeholder-shown {
                        line-height: 1.5rem;
                    }
                    .mynah-ui-spinner-container {
                        contain: layout !important;
                    }
                    .mynah-ui-spinner-container > span.mynah-ui-spinner-logo-part {
                        position: static !important;
                        will-change: transform !important;
                    }
                    .mynah-ui-spinner-container,
                    .mynah-ui-spinner-container > span.mynah-ui-spinner-logo-part,
                    .mynah-ui-spinner-container > span.mynah-ui-spinner-logo-part > .mynah-ui-spinner-logo-mask.text {
                        border: 0 !important;
                        outline: none !important;
                        box-shadow: none !important;
                        border-radius: 0 !important;
                    }
                    select.mynah-form-input {
                        -webkit-appearance: menulist !important;
                        appearance: menulist !important;
                        padding: 0 !important;
                    }
                    .mynah-select-handle {
                        visibility: hidden;
                    }
                    .mynah-ui-spinner-container > span.mynah-ui-spinner-logo-part > .mynah-ui-spinner-logo-mask.text {
                        will-change: transform !important;
                        transform: translateZ(0) !important;
                    }
                </style>
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

    private fun addQuickActionCommands(
        isCodeTransformAvailable: Boolean,
        isFeatureDevAvailable: Boolean,
        isDocAvailable: Boolean,
        isCodeTestAvailable: Boolean,
        isCodeScanAvailable: Boolean,
        highlightCommand: HighlightCommand?,
        activeProfile: QRegionProfile?,
    ) {
        // TODO:  Remove this once chat has been integrated with agents. This is added temporarily to keep detekt happy.
        isCodeScanAvailable
        isCodeTestAvailable
        isDocAvailable
        isFeatureDevAvailable
        isCodeTransformAvailable
        MAX_ONBOARDING_PAGE_COUNT
        OBJECT_MAPPER
        highlightCommand
        activeProfile
    }

    private fun generateQuickActionConfig() = AwsServerCapabilitiesProvider.getInstance(project).getChatOptions().quickActions.quickActionsCommandGroups
        .let { OBJECT_MAPPER.writeValueAsString(it) }
        ?: "[]"

    companion object {
        private const val MAX_ONBOARDING_PAGE_COUNT = 3
        private val OBJECT_MAPPER = jacksonObjectMapper()
    }
}
