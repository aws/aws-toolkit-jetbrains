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
import software.aws.toolkits.core.utils.inputStream
import software.aws.toolkits.jetbrains.core.webview.LocalAssetJBCefRequestHandler
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.util.HighlightCommand
import software.aws.toolkits.jetbrains.services.amazonq.util.createBrowser
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.EditorThemeAdapter
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.ThemeBrowserAdapter
import software.aws.toolkits.jetbrains.settings.MeetQSettings
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Displays the web view for the Amazon Q tool window
 */
class Browser(parent: Disposable, private val mynahAsset: Path, val project: Project) : Disposable {

    val jcefBrowser = createBrowser(parent)

    val receiveMessageQuery = JBCefJSQuery.create(jcefBrowser)

    private val assetRequestHandler = LocalAssetJBCefRequestHandler(jcefBrowser)

    init {
        assetRequestHandler.addWildcardHandler("mynah") { path ->
            val asset = path.replaceFirst("mynah/", "/mynah-ui/assets/")
            Paths.get(asset).normalize().toString().replace("\\", "/").let {
                this::class.java.getResourceAsStream(it)
            }
        }
    }

    fun init(
        isCodeTransformAvailable: Boolean,
        isCodeScanAvailable: Boolean,
        highlightCommand: HighlightCommand?,
        activeProfile: QRegionProfile?,
    ) {
        loadWebView(
            isCodeTransformAvailable,
            isCodeScanAvailable,
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
        isCodeScanAvailable: Boolean,
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
        jcefBrowser.loadURL(
            assetRequestHandler.createResource(
                "webview/chat.html",
                getWebviewHTML(
                    isCodeTransformAvailable,
                    isCodeScanAvailable,
                    highlightCommand,
                    activeProfile,
                )
            )
        )
    }

    /**
     * Generate index.html for the web view
     * @return HTML source
     */
    private fun getWebviewHTML(
        isCodeTransformAvailable: Boolean,
        isCodeScanAvailable: Boolean,
        highlightCommand: HighlightCommand?,
        activeProfile: QRegionProfile?,
    ): String {
        val postMessageToJavaJsCode = receiveMessageQuery.inject("JSON.stringify(message)")
        val connectorAdapterPath = "${LocalAssetJBCefRequestHandler.PROTOCOL}://${LocalAssetJBCefRequestHandler.AUTHORITY}/mynah/js/connectorAdapter.js"
        val mynahResource = assetRequestHandler.createResource(mynahAsset.fileName.toString(), mynahAsset.inputStream())

        // https://github.com/highlightjs/highlight.js/issues/1387
        // language=HTML
        val jsScripts = """
            <script type="text/javascript" charset="UTF-8" src="$connectorAdapterPath" defer></script>
            <script type="text/javascript" charset="UTF-8" src="$mynahResource" defer onload="init()"></script>
            <script type="text/javascript">
                
                const init = () => {
                    const hybridChatConnector = connectorAdapter.initiateAdapter(
                        ${MeetQSettings.getInstance().reinvent2024OnboardingCount < MAX_ONBOARDING_PAGE_COUNT},
                        ${MeetQSettings.getInstance().disclaimerAcknowledged},
                        $isCodeTransformAvailable,
                        $isCodeScanAvailable,
                        {
                            postMessage: message => { $postMessageToJavaJsCode }
                        },
                        ${OBJECT_MAPPER.writeValueAsString(highlightCommand)},
                        "${activeProfile?.profileName.orEmpty()}"
                    )
                    
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

        // language=HTML
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
                    /* Hide native select - will be replaced with custom dropdown */
                    select.mynah-form-input {
                        -webkit-appearance: menulist !important;
                        appearance: menulist !important;
                        padding: 0 !important;
                    }
                    select.mynah-form-input[data-jb-replaced="true"] {
                        position: absolute !important;
                        width: 1px !important;
                        height: 1px !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                    .mynah-select-handle {
                        visibility: hidden;
                    }

                    /* Custom dropdown styles with modal overlay */
                    .jb-custom-select {
                        position: absolute !important;
                        width: 100%% !important;
                        top: 0 !important;
                        left: 0 !important;
                        right: 0 !important;
                        font-family: var(--mynah-font-family) !important;
                    }

                    /* Ensure the parent wrapper has fixed width and relative positioning */
                    .mynah-form-input-wrapper {
                        position: relative !important;
                        width: 100%% !important;
                        min-height: 40px !important;
                    }

                    .jb-custom-select-trigger {
                        display: flex !important;
                        align-items: center !important;
                        justify-content: space-between !important;
                        padding: 8px 12px !important;
                        background: var(--mynah-color-bg) !important;
                        border: 1px solid var(--mynah-color-border-default) !important;
                        border-radius: 4px !important;
                        cursor: pointer !important;
                        color: var(--mynah-color-text-default) !important;
                        width: 100%% !important;
                        box-sizing: border-box !important;
                    }

                    .jb-custom-select-trigger > span:first-child {
                        flex: 1 !important;
                        white-space: nowrap !important;
                        overflow: hidden !important;
                        text-overflow: ellipsis !important;
                    }

                    .jb-custom-select-trigger:hover {
                        border-color: var(--mynah-color-border-hover, var(--mynah-color-border-default)) !important;
                    }

                    .jb-custom-select-arrow {
                        width: 0 !important;
                        height: 0 !important;
                        border-left: 4px solid transparent !important;
                        border-right: 4px solid transparent !important;
                        border-top: 4px solid var(--mynah-color-text-default) !important;
                        margin-left: 8px !important;
                        transition: transform 0.2s !important;
                    }

                    .jb-custom-select-arrow.open {
                        transform: rotate(180deg) !important;
                    }

                    /* Dropdown menu - floats with high z-index */
                    .jb-custom-select-dropdown {
                        position: fixed !important;
                        background: var(--mynah-card-bg) !important;
                        border: 1px solid var(--mynah-color-border-default) !important;
                        border-radius: 6px !important;
                        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4) !important;
                        min-width: 200px !important;
                        max-width: 300px !important;
                        overflow: hidden !important;
                        z-index: 2147483647 !important;
                        display: none !important;
                    }

                    .jb-custom-select-dropdown.open {
                        display: block !important;
                    }

                    .jb-custom-select-option {
                        padding: 6px 14px !important;
                        cursor: pointer !important;
                        font-size: 13px !important;
                        color: var(--mynah-color-text-default) !important;
                        background: var(--mynah-card-bg) !important;
                        border-bottom: 1px solid var(--mynah-color-border-default) !important;
                        transition: background 0.15s !important;
                    }

                    .jb-custom-select-option:last-child {
                        border-bottom: none !important;
                    }

                    .jb-custom-select-option:hover {
                        background: var(--mynah-card-bg-alternate) !important;
                        color: var(--mynah-color-text-strong) !important;
                    }

                    .jb-custom-select-option.selected {
                        background: var(--mynah-color-button) !important;
                        color: var(--mynah-color-button-reverse) !important;
                        font-weight: 500 !important;
                    }

                    .jb-custom-select-option.selected:hover {
                        background: var(--mynah-color-button) !important;
                        filter: brightness(1.1) !important;
                    }
                    .mynah-ui-spinner-container > span.mynah-ui-spinner-logo-part > .mynah-ui-spinner-logo-mask.text {
                        will-change: transform !important;
                        transform: translateZ(0) !important;
                    }
                </style>
            <html lang="en">
                <head>
                    <title>AWS Q</title>
                    $jsScripts
                </head>
                <body>
                    <div style="text-align: center;">Amazon Q is loading...</div>
                    <script type="text/javascript">
                        ${ThemeBrowserAdapter().buildJsCodeToUpdateTheme(EditorThemeAdapter.getThemeFromIde())}
                    </script>
                    <script type="text/javascript">
                        // Custom dropdown for JetBrains
                        (function() {
                            let currentOpenDropdown = null;

                            function replaceSelectWithCustomDropdown() {
                                const selectElements = document.querySelectorAll('select.mynah-form-input');
                                console.log('[JB Modal Dropdown] Found', selectElements.length, 'select elements');

                                selectElements.forEach(function(select) {
                                    // Skip if already replaced
                                    if (select.hasAttribute('data-jb-replaced')) {
                                        return;
                                    }

                                    select.setAttribute('data-jb-replaced', 'true');
                                    console.log('[JB Modal Dropdown] Replacing select with', select.options.length, 'options');

                                    // Create custom dropdown container
                                    const container = document.createElement('div');
                                    container.className = 'jb-custom-select';

                                    // Create trigger button
                                    const trigger = document.createElement('div');
                                    trigger.className = 'jb-custom-select-trigger';

                                    const selectedText = document.createElement('span');
                                    selectedText.textContent = select.options[select.selectedIndex]?.text || '';

                                    // Function to adjust font size based on text length
                                    function adjustFontSize(text, element) {
                                        const length = text.length;
                                        let fontSize;
                                        if (length <= 12) {
                                            fontSize = '12px';  // Short text
                                        } else if (length <= 18) {
                                            fontSize = '11px';  // Medium text
                                        } else if (length <= 25) {
                                            fontSize = '10px';  // Long text
                                        } else {
                                            fontSize = '9px';   // Very long text
                                        }
                                        element.style.fontSize = fontSize;
                                    }

                                    // Set initial font size
                                    adjustFontSize(selectedText.textContent, trigger);

                                    const arrow = document.createElement('span');
                                    arrow.className = 'jb-custom-select-arrow';

                                    trigger.appendChild(selectedText);
                                    trigger.appendChild(arrow);

                                    // Create dropdown menu
                                    const dropdown = document.createElement('div');
                                    dropdown.className = 'jb-custom-select-dropdown';

                                    // Add options
                                    console.log('[JB Modal Dropdown] Creating options, total:', select.options.length);

                                    if (select.options.length === 0) {
                                        const debugOption = document.createElement('div');
                                        debugOption.className = 'jb-custom-select-option';
                                        debugOption.textContent = 'No models available';
                                        debugOption.style.color = '#ff6b6b';
                                        dropdown.appendChild(debugOption);
                                    }

                                    for (let i = 0; i < select.options.length; i++) {
                                        const option = document.createElement('div');
                                        option.className = 'jb-custom-select-option';
                                        option.textContent = select.options[i].text || ('Option ' + i);
                                        option.setAttribute('data-value', select.options[i].value);
                                        console.log('[JB Modal Dropdown] Option', i, ':', select.options[i].text);

                                        if (i === select.selectedIndex) {
                                            option.classList.add('selected');
                                        }

                                        option.addEventListener('click', function(e) {
                                            e.stopPropagation();

                                            // Update selected value
                                            select.selectedIndex = i;

                                            // Trigger change event on original select
                                            const event = new Event('change', { bubbles: true });
                                            select.dispatchEvent(event);

                                            // Update UI
                                            selectedText.textContent = this.textContent;

                                            // Adjust font size for new text
                                            adjustFontSize(this.textContent, trigger);

                                            // Update selected class
                                            dropdown.querySelectorAll('.jb-custom-select-option').forEach(function(opt) {
                                                opt.classList.remove('selected');
                                            });
                                            this.classList.add('selected');

                                            // Close dropdown
                                            dropdown.classList.remove('open');
                                            arrow.classList.remove('open');
                                            currentOpenDropdown = null;
                                        });

                                        dropdown.appendChild(option);
                                    }
                                    console.log('[JB Modal Dropdown] Dropdown has', dropdown.children.length, 'option elements');

                                    // Toggle dropdown on trigger click
                                    trigger.addEventListener('click', function(e) {
                                        console.log('[JB Dropdown] Trigger clicked, toggling dropdown');
                                        e.stopPropagation();

                                        // Close any other open dropdown
                                        if (currentOpenDropdown && currentOpenDropdown !== dropdown) {
                                            currentOpenDropdown.classList.remove('open');
                                        }

                                        // Toggle this dropdown
                                        const isOpening = !dropdown.classList.contains('open');

                                        if (isOpening) {
                                            // Position dropdown near trigger (smart positioning)
                                            const rect = trigger.getBoundingClientRect();
                                            dropdown.style.left = rect.left + 'px';

                                            // Check if more space above or below
                                            const spaceAbove = rect.top;
                                            const spaceBelow = window.innerHeight - rect.bottom;

                                            if (spaceAbove > spaceBelow) {
                                                // More space above - position above trigger
                                                dropdown.style.bottom = (window.innerHeight - rect.top) + 'px';
                                                dropdown.style.top = 'auto';
                                            } else {
                                                // More space below - position below trigger
                                                dropdown.style.top = rect.bottom + 'px';
                                                dropdown.style.bottom = 'auto';
                                            }

                                            // Show dropdown
                                            dropdown.classList.add('open');
                                            arrow.classList.add('open');
                                            currentOpenDropdown = dropdown;
                                        } else {
                                            // Close dropdown
                                            dropdown.classList.remove('open');
                                            arrow.classList.remove('open');
                                            currentOpenDropdown = null;
                                        }
                                    });

                                    // Build custom dropdown
                                    container.appendChild(trigger);

                                    // Append dropdown to body for proper positioning
                                    document.body.appendChild(dropdown);

                                    // Hide original select and insert custom dropdown trigger
                                    select.style.display = 'none';
                                    select.parentElement.insertBefore(container, select);

                                    console.log('[JB Dropdown] Setup complete');
                                });
                            }

                            // Close dropdown when clicking outside
                            document.addEventListener('click', function(e) {
                                if (currentOpenDropdown) {
                                    currentOpenDropdown.classList.remove('open');
                                    // Also update arrow state
                                    const arrows = document.querySelectorAll('.jb-custom-select-arrow.open');
                                    arrows.forEach(function(arrow) {
                                        arrow.classList.remove('open');
                                    });
                                    currentOpenDropdown = null;
                                }
                            });

                            // Try to replace immediately
                            replaceSelectWithCustomDropdown();

                            // Also watch for new selects being added (mynah-ui might add them dynamically)
                            const observer = new MutationObserver(function() {
                                replaceSelectWithCustomDropdown();
                            });

                            // Start observing once DOM is ready
                            if (document.body) {
                                observer.observe(document.body, {
                                    childList: true,
                                    subtree: true
                                });
                            } else {
                                document.addEventListener('DOMContentLoaded', function() {
                                    observer.observe(document.body, {
                                        childList: true,
                                        subtree: true
                                    });
                                });
                            }
                        })();
                    </script>
                </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val MAX_ONBOARDING_PAGE_COUNT = 3
        private val OBJECT_MAPPER = jacksonObjectMapper()
    }
}
