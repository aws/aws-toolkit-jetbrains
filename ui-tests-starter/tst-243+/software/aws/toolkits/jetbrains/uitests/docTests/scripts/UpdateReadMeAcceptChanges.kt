// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.scripts

// language=TS
val updateReadmeAcceptChangesScript = """

    const puppeteer = require('puppeteer');
    
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: 'http://localhost:9222'
        })

        try {

            const pages = await browser.pages()
            //console.log(pages)
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                //console.log(contents)
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
                    console.log('found')

                    await page.type('.mynah-chat-prompt-input', '/doc')
                    await page.keyboard.press('Enter')

                    console.log('entered /doc')
                    console.log('found commands')

                    await findAndClickButton(page, 'Update an existing README', true, 10000)
                    console.log('clicked update readme')
                    await findAndClickButton(page, 'Update README to reflect code', true, 10000)
                    console.log('clicked update readme to reflect code')
                    await findAndClickButton(page, 'Yes', true, 10000)
                    console.log('clicked yes')

                    await new Promise(resolve => setTimeout(resolve, 90000));

                    await findAndClickButton(page, 'Accept', true, 10000)
                    console.log('clicked Accept')

                }
            }


        } finally {
            await browser.close();
        }
    }

    testNavigation().catch(console.error);

""".trimIndent()

val updateReadmeAcceptChangesTestScript = updateReadmeAcceptChangesScript.plus(findAndClickButton)
