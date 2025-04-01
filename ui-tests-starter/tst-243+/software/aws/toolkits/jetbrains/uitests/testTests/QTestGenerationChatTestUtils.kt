// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.testTests

/**
 * JavaScript function to wait for an element with specific text to appear on the page
 */
val waitForElementWithTextFunction = """
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
""".trimIndent()

/**
 * JavaScript function to wait for an element with specific text to appear and return it
 */
val waitAndGetElementByTextFunction = """
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
""".trimIndent()
