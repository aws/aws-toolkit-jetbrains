// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.testTests
import org.intellij.lang.annotations.Language

@Language("JavaScript")
val testHappyPathScript = """
        const puppeteer = require('puppeteer');
        async function testNavigation() {
            const browser = await puppeteer.connect({
                browserURL: "http://localhost:9222"
            })
            try {
                const pages = await browser.pages()
                for(const page of pages) {
                    const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                    const element = await page.${'$'}('.mynah-chat-prompt-input')
                    if(element) {
                        const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                        const attr = await Promise.all(
                            elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                        );
                        await page.type('.mynah-chat-prompt-input', '/test')
                        await page.keyboard.press('Enter');
                        await page.keyboard.press('Enter');
                        try {
                            await waitForElementWithText(page, "Q - Test")
                            console.log("new tab opened")
                            await page.waitForFunction(
                                () => {
                                    const button = document.querySelector('button[action-id="utg_view_diff"]');
                                    return button && button.isEnabled !== false && button.disabled !== true;
                                },
                                { timeout: 300000 }
                            );
                            await page.evaluate(() => {
                                const button = document.querySelector('button[action-id="utg_view_diff"]');
                                if (button) {
                                    button.click();
                                } else {
                                    throw new Error('Button not found after waiting');
                                }
                            });
                            console.log("View Diff opened")
                            await page.waitForFunction(
                                () => {
                                    const button = document.querySelector('button[action-id="utg_accept"]');
                                    return button && button.isEnabled !== false && button.disabled !== true;
                                },
                                { timeout: 300000 }
                            );
                            await page.evaluate(() => {
                                const button = document.querySelector('button[action-id="utg_accept"]');
                                if (button) {
                                    button.click();
                                } else {
                                    throw new Error('Accept button not found after waiting');
                                }
                            });
                            console.log("Result Accepted")
                            await waitForElementWithText(page, "Unit test generation completed.")
                            console.log("Unit test generation completed.")
                        } catch (e) {
                            console.log("Element with text not found")
                            console.log(e)
                            throw e
                        }

                    }
                }
            } finally {
                await browser.close();
            }
        }

        async function waitForElementWithText(page, text) {
            await page.waitForFunction(
                (expectedText) => {
                    const elements = document.querySelectorAll('*');
                    return Array.from(elements).find(element =>
                        element.textContent?.trim() === expectedText
                    );
                },
                {},
                text
            );
        }

        async function waitAndGetElementByText(page, text) {
            const element = await page.waitForFunction(
                (expectedText) => {
                    const elements = document.querySelectorAll('*');
                    return Array.from(elements).find(element =>
                        element.textContent?.trim() === expectedText
                    );
                },
                {},
                text
            );
            return element;
        }
        testNavigation().catch(console.error);
""".trimIndent()

@Language("JavaScript")
val testNoFilePathScript = """
const puppeteer = require('puppeteer');
async function testNavigation() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for(const page of pages) {
            const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
            const element = await page.${'$'}('.mynah-chat-prompt-input')
            if(element) {
                const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                const attr = await Promise.all(
                    elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                );
                await page.type('.mynah-chat-prompt-input', '/test')
                await page.keyboard.press('Enter');
                await page.keyboard.press('Enter');
                try {
                    await waitForElementWithText(page, "Q - Test")
                    console.log("new tab opened")
                    const errorMessage = await page.waitForSelector('text/Sorry, there isn\'t a source file open right now that I can generate a test for. Make sure you open a source file so I can generate tests.', {
                        timeout: 5000
                    })
                    console.log('Error message:', await errorMessage.evaluate(el => el.textContent))
                } catch (e) {
                    console.log("Element with text not found")
                    console.log(e)
                    throw e
                }
            }
        }
    } finally {
        await browser.close();
    }
}

async function waitForElementWithText(page, text) {
    await page.waitForFunction(
        (expectedText) => {
            const elements = document.querySelectorAll('*');
            return Array.from(elements).find(element =>
                element.textContent?.trim() === expectedText
            );
        },
        {},
        text
    );
}

async function waitAndGetElementByText(page, text) {
    const element = await page.waitForFunction(
        (expectedText) => {
            const elements = document.querySelectorAll('*');
            return Array.from(elements).find(element =>
                element.textContent?.trim() === expectedText
            );
        },
        {},
        text
    );
    return element;
}
testNavigation().catch(console.error);
""".trimIndent()

@Language("JavaScript")
val expectedErrorPath = """
    const puppeteer = require('puppeteer');
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: "http://localhost:9222"
        })
        try {
            const pages = await browser.pages()
            //console.log(pages)
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                //console.log(contents)
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
                    const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                    const attr = await Promise.all(
                        elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                    );
                    await page.type('.mynah-chat-prompt-input', '/test')

                    await page.keyboard.press('Enter');
                    await page.keyboard.press('Enter');

                    try {
                        await waitForElementWithText(page, "Q - Test")
                        console.log("new tab opened")
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 300000  // 5 minutes timeout
                            },
                            "I apologize, but the specified methods are private or protected. I can only generate tests for public methods. Try /test again and specify public methods to generate tests."
                        );
                        console.log("Test generation complete with expected error")

                    } catch (e) {
                        console.log("Element with text not found")
                        console.log(e)
                        throw e
                    }

                }
            }
        } finally {
            await browser.close();
        }
    }

    async function waitForElementWithText(page, text) {
        await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
    }

    async function waitAndGetElementByText(page, text) {
        const element = await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
        return element;
    }
    testNavigation().catch(console.error);
""".trimIndent()

@Language("JavaScript")
val unsupportedLanguagePath = """
    const puppeteer = require('puppeteer');
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: "http://localhost:9222"
        })
        try {
            const pages = await browser.pages()
            //console.log(pages)
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                //console.log(contents)
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
                    const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                    const attr = await Promise.all(
                        elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                    );
                    await page.type('.mynah-chat-prompt-input', '/test')

                    await page.keyboard.press('Enter');
                    await page.keyboard.press('Enter');

                    try {
                        await waitForElementWithText(page, "Q - Test")
                        console.log("new tab opened")
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 300000  // 5 minutes timeout
                            },
                            "is not a language I support specialized unit test generation for at the moment."
                        );
                        console.log("Test generation complete with expected error")

                    } catch (e) {
                        console.log("Element with text not found")
                        console.log(e)
                        throw e
                    }

                }
            }
        } finally {
            await browser.close();
        }
    }

    async function waitForElementWithText(page, text) {
        await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
    }

    async function waitAndGetElementByText(page, text) {
        const element = await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
        return element;
    }
    testNavigation().catch(console.error);
""".trimIndent()

@Language("JavaScript")
val testRejectPathScript = """
        const puppeteer = require('puppeteer');
        async function testNavigation() {
            const browser = await puppeteer.connect({
                browserURL: "http://localhost:9222"
            })
            try {
                const pages = await browser.pages()
                for(const page of pages) {
                    const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                    const element = await page.${'$'}('.mynah-chat-prompt-input')
                    if(element) {
                        const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                        const attr = await Promise.all(
                            elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                        );
                        await page.type('.mynah-chat-prompt-input', '/test')
                        await page.keyboard.press('Enter');
                        await page.keyboard.press('Enter');
                        try {
                            await waitForElementWithText(page, "Q - Test")
                            console.log("new tab opened")
                            await page.waitForFunction(
                                () => {
                                    const button = document.querySelector('button[action-id="utg_view_diff"]');
                                    return button && button.isEnabled !== false && button.disabled !== true;
                                },
                                { timeout: 300000 }
                            );
                            await page.evaluate(() => {
                                const button = document.querySelector('button[action-id="utg_view_diff"]');
                                if (button) {
                                    button.click();
                                } else {
                                    throw new Error('Button not found after waiting');
                                }
                            });
                            console.log("View Diff opened")
                            await page.waitForFunction(
                                () => {
                                    const button = document.querySelector('button[action-id="utg_reject"]');
                                    return button && button.isEnabled !== false && button.disabled !== true;
                                },
                                { timeout: 300000 }
                            );
                            await page.evaluate(() => {
                                const button = document.querySelector('button[action-id="utg_reject"]');
                                if (button) {
                                    button.click();
                                } else {
                                    throw new Error('Accept button not found after waiting');
                                }
                            });
                            console.log("Result Reject")
                            await waitForElementWithText(page, "Unit test generation completed.")
                            console.log("Unit test generation completed.")
                        } catch (e) {
                            console.log("Element with text not found")
                            console.log(e)
                            throw e
                        }

                    }
                }
            } finally {
                await browser.close();
            }
        }

        async function waitForElementWithText(page, text) {
            await page.waitForFunction(
                (expectedText) => {
                    const elements = document.querySelectorAll('*');
                    return Array.from(elements).find(element =>
                        element.textContent?.trim() === expectedText
                    );
                },
                {},
                text
            );
        }

        async function waitAndGetElementByText(page, text) {
            const element = await page.waitForFunction(
                (expectedText) => {
                    const elements = document.querySelectorAll('*');
                    return Array.from(elements).find(element =>
                        element.textContent?.trim() === expectedText
                    );
                },
                {},
                text
            );
            return element;
        }
        testNavigation().catch(console.error); 
""".trimIndent()

@Language("JavaScript")
val testNLErrorPathScript = """
    const puppeteer = require('puppeteer');
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: "http://localhost:9222"
        })
        try {
            const pages = await browser.pages()
            //console.log(pages)
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                //console.log(contents)
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
                    const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                    const attr = await Promise.all(
                        elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                    );
                    await page.type('.mynah-chat-prompt-input', '/test /something/')
                    await page.keyboard.press('Enter');    

                    try {
                        console.log("Command entered: /test /something/")
                        await waitForElementWithText(page, "Q - Test")
                        console.log("new tab opened")
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 300000  // 5 minutes timeout
                            },
                            "I apologize, but I couldn't process your /test instruction."
                        );
                        
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 300000  // 5 minutes timeout
                            },
                            "Try: /test and optionally specify a class, function or method."
                        );
                       console.log("Error message displayed correctly")
                       
                    } catch (e) {
                        console.log("Element with text not found")
                        console.log(e)
                        throw e
                    }

                }
            }
        } finally {
            await browser.close();
        }    
    }
    
    async function waitForElementWithText(page, text) {
        await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                        );
                },
                    {},
                    text
                );
    }

    async function waitAndGetElementByText(page, text) {
        const element = await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                       element.textContent?.trim() === expectedText
                   );
               },
               {},
               text
           );
        return element;
    }
    testNavigation().catch(console.error); 
""".trimIndent()

@Language("JavaScript")
val testProgressBarScript = """
    const puppeteer = require('puppeteer');
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: "http://localhost:9222"
        })
        try {
            const pages = await browser.pages()
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
                    const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                    const attr = await Promise.all(
                        elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                    );
                    await page.type('.mynah-chat-prompt-input', '/test')
                    await page.keyboard.press('Enter');
                    await page.keyboard.press('Enter');
                    
                    try {
                        await waitForElementWithText(page, "Q - Test")
                        console.log("new tab opened")
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 30000
                            },                        
                            "Generating unit tests"
                        );

                        console.log("Progress bar text displayed")

                        await page.waitForFunction(
                            () => {
                                const button = document.querySelector('button[action-id="utg_view_diff"]');
                                return button && button.isEnabled !== false && button.disabled !== true;
                            },
                            { timeout: 300000 }
                        );
                        
                        console.log("Test generation completed successfully")
                        
                    } catch (e) {
                        console.log("Test failed")
                        console.log(e)
                        throw e
                    }
                }
            }
        } finally {
            await browser.close();
        }
    }
    
    async function waitForElementWithText(page, text) {
        await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
    }

    async function waitAndGetElementByText(page, text) {
        const element = await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
        return element;
    }
    testNavigation().catch(console.error);
""".trimIndent()

val testCancelButtonScript = """
    const puppeteer = require('puppeteer');
    async function testNavigation() {
        const browser = await puppeteer.connect({
            browserURL: "http://localhost:9222"
        })
        try {
            const pages = await browser.pages()
            for(const page of pages) {
                const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
                const element = await page.${'$'}('.mynah-chat-prompt-input')
                if(element) {
                    const elements = await page.${'$'}${'$'}('.mynah-chat-command-selector-command');
                    const attr = await Promise.all(
                        elements.map(elem => elem.evaluate(el => el.getAttribute('command')))
                    );
                    await page.type('.mynah-chat-prompt-input', '/test')
                    await page.keyboard.press('Enter');
                    await page.keyboard.press('Enter');
                    
                    try {
                        await waitForElementWithText(page, "Q - Test")
                        console.log("new tab opened")
                        
                       
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 30000
                            },                        
                            "Generating unit tests"
                        );

                        console.log("Progress bar text displayed")
                        
                      
                        const cancelButton = await waitAndGetElementByText(page, "Cancel");
                        console.log("Cancel button found")
                        
                      
                        await cancelButton.evaluate(button => button.click())
                        console.log("Cancel button clicked")
                        
                      
                        await page.waitForFunction(
                            (expectedText) => {
                                const pageContent = document.body.textContent || '';
                                return pageContent.includes(expectedText);
                            },
                            {
                                timeout: 30000
                            },
                            "Unit test generation cancelled."
                        );
                        
                        console.log("Test generation cancelled successfully")
                        
                        await page.waitForFunction(
                            () => {
                                const inputElement = document.querySelector('.mynah-chat-prompt-input');
                                return inputElement && !inputElement.disabled;
                            },
                            { timeout: 10000 }
                        );
                        
                        console.log("Input field re-enabled after cancellation")
                        
                    } catch (e) {
                        console.log("Test failed")
                        console.log(e)
                        throw e
                    }
                }
            }
        } finally {
            await browser.close();
        }
    }

    async function waitForElementWithText(page, text) {
        await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
    }

    async function waitAndGetElementByText(page, text) {
        const element = await page.waitForFunction(
            (expectedText) => {
                const elements = document.querySelectorAll('*');
                return Array.from(elements).find(element =>
                    element.textContent?.trim() === expectedText
                );
            },
            {},
            text
        );
        return element;
    }
    testNavigation().catch(console.error);
""".trimIndent()
