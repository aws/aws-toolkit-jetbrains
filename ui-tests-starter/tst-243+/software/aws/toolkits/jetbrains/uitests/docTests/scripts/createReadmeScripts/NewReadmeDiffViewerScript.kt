 // Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 // SPDX-License-Identifier: Apache-2.0

 package software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts

 import software.aws.toolkits.jetbrains.uitests.findAndClickButtonScript

 // language=JS
 val newReadmeDiffViewerScript = """
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
                    await new Promise(resolve => setTimeout(resolve, 75000));

                    console.log('Attempting to find Accept button to confirm Readme has been generated');
                    await findAndClickButton(page, 'Accept', false, 10000)
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

 val newReadmeDiffViewerTestScript = newReadmeDiffViewerScript.plus(findAndClickButtonScript)
