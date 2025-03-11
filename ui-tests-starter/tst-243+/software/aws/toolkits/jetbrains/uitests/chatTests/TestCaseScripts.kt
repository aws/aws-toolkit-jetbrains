// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.chatTests

// language=JS
val testFeatureAvailabilityOnSlash = """
const puppeteer = require('puppeteer');

async function testNavigation() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
            const element = await page.$('.mynah-chat-prompt-input')
            if(element) {
                await page.type('.mynah-chat-prompt-input', '/')
                const elements = await page.$$(".mynah-chat-command-selector-command");
                const attr = await Promise.all(
                    elements.map(async element => {
                        return element.evaluate(el => el.getAttribute("command"));
                    })
                );
                console.log(JSON.stringify(attr, null, 2))
            }
        }
    } finally {
        await browser.close();
    }
}
testNavigation().catch(console.error);

""".trimIndent()

// language=JS
val transformHappyPathScript = """
const puppeteer = require('puppeteer');
async function testNavigation() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for (const page of pages) {
            await page.type('.mynah-chat-prompt-input', '/transform')
            await page.keyboard.press('Enter')
            
            await page.waitForSelector('.mynah-chat-item-form-items-container', {
                timeout: 5000
            })
            const formInputs = await page.$$('.mynah-form-input-wrapper')
            
            const moduleLabel = await formInputs[0].evaluate(
                element => element.querySelector('.mynah-ui-form-item-mandatory-title').textContent
            )
            console.log('Module selection label:', moduleLabel)
            
            const versionLabel = await formInputs[1].evaluate(
                element => element.querySelector('.mynah-ui-form-item-mandatory-title').textContent
            )
            console.log('Version selection label:', versionLabel)
            
            await page.evaluate(() => {
                const button = document.querySelector('button[action-id="codetransform-input-confirm"]')
                button.click()
            })
            
            const skipTestsForm = await page.waitForSelector('button[action-id="codetransform-input-confirm-skip-tests"]', {
                timeout: 5000
            })
            console.log('Skip tests form appeared:', skipTestsForm !== null)
            
            await page.evaluate(() => {
                const button = document.querySelector('button[action-id="codetransform-input-confirm-skip-tests"]')
                button.click()
            })
            
            const oneOrMultipleDiffsForm = await page.waitForSelector('button[action-id="codetransform-input-confirm-one-or-multiple-diffs"]', {
                timeout: 5000
            })
            console.log('One or multiple diffs form appeared:', oneOrMultipleDiffsForm !== null)
            
            await page.evaluate(() => {
                const button = document.querySelector('button[action-id="codetransform-input-confirm-one-or-multiple-diffs"]')
                button.click()
            })
              
            const errorMessage = await page.waitForSelector('text/Sorry, I couldn\'t run the Maven clean install command', {
                timeout: 5000
            })
            console.log('Error message:', await errorMessage.evaluate(el => el.textContent))
        }
    } finally {
        await browser.close()
    }
}
testNavigation().catch(console.error)

""".trimIndent()
