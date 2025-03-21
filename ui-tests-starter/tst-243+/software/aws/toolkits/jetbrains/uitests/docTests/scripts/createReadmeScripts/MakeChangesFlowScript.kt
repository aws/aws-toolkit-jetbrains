// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts

import software.aws.toolkits.jetbrains.uitests.findAndClickButtonScript

// language=JS
val makeChangesFlowScript = """
    const puppeteer = require('puppeteer');
    
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: 'http://localhost:9222'
        })
    
        try {
    
            const pages = await browser.pages()
    
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
    
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
    
                    console.log('Typing /doc in the chat window')
    
                    await page.type('.mynah-chat-prompt-input', '/doc')
                    await page.keyboard.press('Enter')
    
                    console.log('Attempting to find and click Create a README button')
                    await findAndClickButton(page, 'Create a README', true, 10000)
                    console.log('Attempting to find and click Yes button to confirm option')
                    await findAndClickButton(page, 'Yes', true, 10000)
    
                    console.log('Waiting for README to be generated')
                    await new Promise(resolve => setTimeout(resolve, 90000))
    
                    console.log('Attempting to find and click Make Changes button');
                    await findAndClickButton(page, 'Make changes', true, 10000)
    
                    const makeChangeText = await page.waitForSelector('[placeholder="Describe documentation changes"]');
                        if (!makeChangeText) {
                              console.log('Error: Test Failed');
                              console.log('Unable to find placeholder description text in Make Changes flow');
                            } else {
                                console.log('Found expected placeholder text for Make Changes flow');
                                console.log('Test Successful');
                            }
                }
          }
    
        } finally {
            await browser.close();
        }
    }
    
    testNavigation().catch((error) => {
      console.log('Error: Test Failed');
      console.error(error);
    });
""".trimIndent()

val makeChangesFlowTestScript = makeChangesFlowScript.plus(findAndClickButtonScript)
