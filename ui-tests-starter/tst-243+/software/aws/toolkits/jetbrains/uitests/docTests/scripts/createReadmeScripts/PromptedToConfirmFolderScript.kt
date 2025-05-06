// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts

import software.aws.toolkits.jetbrains.uitests.findAndClickButtonScript

// language=JS
val createReadmePromptedToConfirmFolderScript = """
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
                    console.log('Attempting to find all available buttons')
                    const yesButton = await findAndClickButton(page, 'Yes', false, 10000)
                    const changeFolderButton = await findAndClickButton(page, 'Change folder', false, 10000)
                    const cancelButton = await findAndClickButton(page, 'Cancel', false, 10000)

                    if (!yesButton || !changeFolderButton || !cancelButton) {
                      console.log('Error: Test Failed')
                      console.log('Unable to find buttons for Yes/ChangeFolder/Cancel')
                    } else {
                      console.log('Found all expected buttons')
                      console.log('Test Successful')
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

val createReadmePromptedToConfirmFolderTestScript = createReadmePromptedToConfirmFolderScript.plus(findAndClickButtonScript)
