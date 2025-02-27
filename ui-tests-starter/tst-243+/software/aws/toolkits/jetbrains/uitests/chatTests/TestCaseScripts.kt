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
