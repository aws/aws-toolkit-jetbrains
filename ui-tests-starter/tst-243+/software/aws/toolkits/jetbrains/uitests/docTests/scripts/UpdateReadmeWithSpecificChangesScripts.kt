// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.scripts

// language=TS
val updateReadmeSpecificChangesMakeChangesFlowScript = """
    
     const puppeteer = require('puppeteer');
  
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: 'http://localhost:9222'
        });
    
        try {
    
            const pages = await browser.pages();
    
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
               
                const element = await page.${'$'}('.mynah-chat-prompt-input');
                if(element) {
                    
                    console.log('Typing /doc in the chat window');
    
                    await page.type('.mynah-chat-prompt-input', '/doc');
                    await page.keyboard.press('Enter');
    
                    console.log('Attempting to find and click Update an existing README button');
                    await findAndClickButton(page, 'Update an existing README', true, 10000);
                    console.log('Attempting to find and click Make a specific change button'); 
                    await findAndClickButton(page, 'Make a specific change', true, 10000);
                    console.log('Attempting to find and click Yes button to confirm option');
                    await findAndClickButton(page, 'Yes', true, 10000);
                    
                    console.log('Typing specific change instructions in the chat window');
                    await page.type('.mynah-chat-prompt-input', 'Add a section with Installation instructions for this repository. Title this new section \"### Installation\"');
                    await page.keyboard.press('Enter');
    
                    console.log('Waiting for updated README to be generated');
                    await new Promise(resolve => setTimeout(resolve, 90000));
                    console.log('Attempting to find and click Make changes button');
                    await findAndClickButton(page, 'Make changes', true, 10000);
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

// language=TS
val updateReadmeSpecificChangesScript = """
    
    const puppeteer = require('puppeteer');
    
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: 'http://localhost:9222'
        });

        try {

            const pages = await browser.pages();
            
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                
                const element = await page.${'$'}('.mynah-chat-prompt-input');

                if(element) {
                    
                    console.log('Typing /doc in the chat window');
                    
                    await page.type('.mynah-chat-prompt-input', '/doc');
                    await page.keyboard.press('Enter');

                    console.log('Attempting to find and click Update an existing README button'); 
                    await findAndClickButton(page, 'Update an existing README', true, 10000);
                    console.log('Attempting to find and click Make a specific change button');
                    await findAndClickButton(page, 'Make a specific change', true, 10000);
                    console.log('Attempting to find and click Yes button to confirm option');
                    await findAndClickButton(page, 'Yes', true, 10000);
                    
                    console.log('Typing specific change instructions in the chat window');
                    await page.type('.mynah-chat-prompt-input', 'Add a section with Installation instructions for this repository. Title this new section \"### Installation\"');
                    await page.keyboard.press('Enter');

                    console.log('Waiting for updated README to be generated');
                    await new Promise(resolve => setTimeout(resolve, 90000));
                    
                    console.log('Attempting to find and click Accept button');
                    await findAndClickButton(page, 'Accept', true, 10000);
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

val updateReadmeSpecificChangesMakeChangesFlowTestScript = updateReadmeSpecificChangesMakeChangesFlowScript.plus(findAndClickButtonScript)
val updateReadmeSpecificChangesTestScript = updateReadmeSpecificChangesScript.plus(findAndClickButtonScript)
