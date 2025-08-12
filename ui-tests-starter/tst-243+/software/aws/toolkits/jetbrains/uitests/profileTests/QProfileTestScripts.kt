// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.profileTests

import software.aws.toolkits.jetbrains.uitests.testScriptPrefix

// language=JS
val testProfileSelectorShown = """
$testScriptPrefix

async function testProfileSelector() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            // Check if the page contains the profile selector webview
            const profileSelector = await page.$('.profile-list')
            if(profileSelector) {
                console.log("Profile selector is shown")
                return
            }
            
            // Check if the page contains the chat input (which would mean profile selector was skipped)
            const chatInput = await page.$('.mynah-chat-prompt-input')
            if(chatInput) {
                console.log("Chat is shown instead of profile selector")
                return
            }
        }
        console.log("Neither profile selector nor chat found")
    } finally {
        await browser.close()
    }
}
testProfileSelector().catch(console.error);
""".trimIndent()

// language=JS
val testChatShownDirectly = """
$testScriptPrefix

async function testChatShownDirectly() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            // Check if the page contains the chat input (which would mean profile selector was skipped)
            const chatInput = await page.$('.mynah-chat-prompt-input')
            if(chatInput) {
                console.log("Chat is shown directly")
                
                // Check if profile UI is hidden (except in menu)
                const profileUI = await page.$('.q-profile-selector-webview')
                if(!profileUI) {
                    console.log("Profile UI is not visible")
                }
                return
            }
            
            // Check if the page contains the profile selector webview (which shouldn't be shown)
            const profileSelector = await page.$('.q-profile-selector-webview')
            if(profileSelector) {
                console.log("Profile selector is shown when it should be skipped")
                return
            }
        }
        console.log("Neither profile selector nor chat found")
    } finally {
        await browser.close()
    }
}
testChatShownDirectly().catch(console.error);
""".trimIndent()

// language=JS
val testProfileSwitching = """
$testScriptPrefix

async function testProfileSwitching() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            // First check if we have the Q menu
            const qMenu = await page.$('.q-menu-button')
            if(qMenu) {
                // Click on the Q menu
                await qMenu.click()
                await page.waitForSelector('.q-menu-item')
                
                // Find and click on "Change profile" option
                const menuItems = await page.$$('.q-menu-item')
                for(const item of menuItems) {
                    const text = await item.evaluate(el => el.textContent)
                    if(text && text.includes('Change profile')) {
                        await item.click()
                        break
                    }
                }
                
                // Wait for profile dialog
                await page.waitForSelector('.q-profile-dialog')
                console.log("Profile dialog opened")
                
                // Select a different profile
                const profileOptions = await page.$$('.q-profile-option')
                if(profileOptions.length > 1) {
                    // Click on the second profile option
                    await profileOptions[1].click()
                    
                    // Click OK
                    const okButton = await page.$('.q-profile-dialog-ok-button')
                    await okButton.click()
                    
                    // Wait for confirmation dialog
                    await page.waitForSelector('.q-profile-switch-confirmation-dialog')
                    console.log("Profile switch confirmation shown")
                    
                    // Confirm switch
                    const confirmButton = await page.$('.q-profile-switch-confirm-button')
                    await confirmButton.click()
                    
                    // Wait for chat to reload
                    await page.waitForSelector('.mynah-chat-prompt-input')
                    
                    // Check if chat history is cleared
                    const chatHistory = await page.$$('.mynah-chat-item')
                    if(chatHistory.length <= 1) { // Only the profile switch notification card
                        console.log("Chat history cleared")
                    } else {
                        console.log("Chat history not cleared")
                    }
                    
                    // Check for profile switch notification card
                    const notificationText = await page.evaluate(() => {
                        const elements = document.querySelectorAll('.mynah-chat-item-card')
                        for(const el of elements) {
                            if(el.textContent.includes('You are using')) {
                                return el.textContent
                            }
                        }
                        return null
                    })
                    
                    if(notificationText) {
                        console.log("Profile switch notification shown: " + notificationText)
                    }
                }
            }
            return
        }
        console.log("Q menu not found")
    } finally {
        await browser.close()
    }
}
testProfileSwitching().catch(console.error);
""".trimIndent()

// language=JS
val testProfileRemembered = """
$testScriptPrefix

async function testProfileRemembered() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            // Check if the page contains the chat input (which would mean profile was remembered)
            const chatInput = await page.$('.mynah-chat-prompt-input')
            if(chatInput) {
                console.log("Chat is shown directly")
                
                // Check if there's a profile indicator in the UI
                const profileIndicator = await page.evaluate(() => {
                    const elements = document.querySelectorAll('.q-profile-indicator')
                    return elements.length > 0
                })
                
                if(profileIndicator) {
                    console.log("Profile selection remembered")
                }
                return
            }
            
            // Check if the page contains the profile selector webview (which shouldn't be shown)
            const profileSelector = await page.$('.q-profile-selector-webview')
            if(profileSelector) {
                console.log("Profile selector is shown when it should remember previous selection")
                return
            }
        }
        console.log("Neither profile selector nor chat found")
    } finally {
        await browser.close()
    }
}
testProfileRemembered().catch(console.error);
""".trimIndent()

// language=JS
val testQFeaturesWithProfile = """
$testScriptPrefix

async function testQFeaturesWithProfile() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            // Check if we have the chat input
            const chatInput = await page.$('.mynah-chat-prompt-input')
            if(chatInput) {
                // Test /dev command
                await chatInput.type('/dev')
                await page.waitForSelector('.mynah-chat-command-selector-command')
                await page.keyboard.press('Enter')
                await page.waitForSelector('.mynah-chat-item-user-message')
                console.log("/dev command works")
                
                // Test /transform command
                await chatInput.type('/transform')
                await page.waitForSelector('.mynah-chat-command-selector-command')
                await page.keyboard.press('Enter')
                await page.waitForSelector('.mynah-chat-item-user-message')
                console.log("/transform command works")
                
                // Test /test command
                await chatInput.type('/test')
                await page.waitForSelector('.mynah-chat-command-selector-command')
                await page.keyboard.press('Enter')
                await page.waitForSelector('.mynah-chat-item-user-message')
                console.log("/test command works")
                
                // Test /review command
                await chatInput.type('/review')
                await page.waitForSelector('.mynah-chat-command-selector-command')
                await page.keyboard.press('Enter')
                await page.waitForSelector('.mynah-chat-item-user-message')
                console.log("/review command works")
                
                // Test /doc command
                await chatInput.type('/doc')
                await page.waitForSelector('.mynah-chat-command-selector-command')
                await page.keyboard.press('Enter')
                await page.waitForSelector('.mynah-chat-item-user-message')
                console.log("/doc command works")
                
                return
            }
        }
        console.log("Chat input not found")
    } finally {
        await browser.close()
    }
}
testQFeaturesWithProfile().catch(console.error);
""".trimIndent()

// language=JS
val testNoProfileNoFeatures = """
$testScriptPrefix

async function testNoProfileNoFeatures() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            // Check if we have the profile selector
            const profileSelector = await page.$('.q-profile-selector-webview')
            if(profileSelector) {
                // Check if inline suggestion is disabled
                const inlineSuggestion = await page.evaluate(() => {
                    // Simulate Option+C keyboard shortcut
                    const event = new KeyboardEvent('keydown', {
                        key: 'c',
                        code: 'KeyC',
                        altKey: true
                    })
                    document.dispatchEvent(event)
                    
                    // Check if any suggestion UI appears
                    return document.querySelector('.q-inline-suggestion') === null
                })
                
                if(inlineSuggestion) {
                    console.log("Inline suggestion is disabled")
                }
                
                // Check if context menu Q section is disabled
                const editor = await page.$('.editor-component')
                if(editor) {
                    await editor.click({button: 'right'})
                    await page.waitForSelector('.context-menu')
                    
                    const qMenuDisabled = await page.evaluate(() => {
                        const qMenuItems = document.querySelectorAll('.context-menu-q-item')
                        return qMenuItems.length === 0 || 
                               Array.from(qMenuItems).every(item => item.classList.contains('disabled'))
                    })
                    
                    if(qMenuDisabled) {
                        console.log("Context menu Q section is disabled")
                    }
                }
                
                // Check if Q menu doesn't show service options
                const qMenu = await page.$('.q-menu-button')
                if(qMenu) {
                    await qMenu.click()
                    await page.waitForSelector('.q-menu-item')
                    
                    const serviceOptionsHidden = await page.evaluate(() => {
                        const menuItems = document.querySelectorAll('.q-menu-item')
                        return Array.from(menuItems)
                            .every(item => !item.textContent.includes('/dev') && 
                                          !item.textContent.includes('/transform') &&
                                          !item.textContent.includes('/test'))
                    })
                    
                    if(serviceOptionsHidden) {
                        console.log("Q menu service options are hidden")
                    }
                }
                
                return
            }
        }
        console.log("Profile selector not found")
    } finally {
        await browser.close()
    }
}
testNoProfileNoFeatures().catch(console.error);
""".trimIndent()
