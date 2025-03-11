// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.featureDevTests

import software.aws.toolkits.jetbrains.uitests.testScriptPrefix

val testAcceptInitalCode = """
$testScriptPrefix

async function testAcceptInitalCode() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222",
        protocolTimeout: 750_000,
    });

    const page = (await browser.pages()).find((p) => p.url().startsWith('file:'));

    try {
        const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
        await page.waitForSelector('.mynah-chat-prompt-input');
        await page.type('.mynah-chat-prompt-input', '/dev implement a debounce function in operation.js')
        await page.keyboard.press('Enter');

        await retryIfRequired(page, async () => {
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept all changes)', {timeout: 600_000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await acceptCode.click();

            await page.waitForSelector('.mynah-chat-item-followup-question-option');
            const [newTask, noThanks, generateDevFile] = await page.$$('.mynah-chat-item-followup-question-option');
            await noThanks.click();
        });

        // Validate if /dev ends the conversation successfully.
        await page.waitForSelector('p ::-p-text(Okay, I\'ve ended this chat session. You can open a new tab to chat or start another workflow.)', {timeout: 3000});
        console.log('Success: /dev ends the conversation successfully.');

    } finally {
        await closeSelectedTab(page);
        await browser.close();
    }
}

testAcceptInitalCode().catch(console.error);

""".trimIndent()

val testIterateCodeGen = """
$testScriptPrefix

async function testIterateCodeGen() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222",
        protocolTimeout: 750_000,
    });

    const page = (await browser.pages()).find((p) => p.url().startsWith('file:'));

    try {
        await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
        await page.waitForSelector('.mynah-chat-prompt-input');
        await page.type('.mynah-chat-prompt-input', '/dev')
        await page.keyboard.press('Enter');

        await page.type('.mynah-chat-prompt-input', 'Add debounce function in operation.js.')
        await page.keyboard.press('Enter');

        // First iteration
        await retryIfRequired(page, async () => {
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept all changes)', {timeout: 600_000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await iterateCode.click();
        });

        // Second iteration
        await page.type('.mynah-chat-prompt-input', 'Also add throttle function in operation.js.');
        await page.keyboard.press('Enter');

        await retryIfRequired(page, async () => {
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept all changes)', {timeout: 600_000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await acceptCode.click();

            await page.waitForSelector('.mynah-chat-item-followup-question-option');
            const [newTask, noThanks, generateDevFile] = await page.$$('.mynah-chat-item-followup-question-option');
            await noThanks.click();
        });

        // Validate if /dev ends the conversation successfully.
        await page.waitForSelector('p ::-p-text(Okay, I\'ve ended this chat session. You can open a new tab to chat or start another workflow.)', {timeout: 3000});
        console.log('Success: /dev ends the conversation successfully.');

    } finally {
        await closeSelectedTab(page);
        await browser.close();
    }
}

testIterateCodeGen().catch(console.error);
""".trimIndent()

val testNewCodeGen = """
$testScriptPrefix

async function testNewCodeGen() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222",
        protocolTimeout: 750_000,
    });

    const page = (await browser.pages()).find((p) => p.url().startsWith('file:'));

    try {
        await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
        await page.waitForSelector('.mynah-chat-prompt-input');
        await page.type('.mynah-chat-prompt-input', '/dev')
        await page.keyboard.press('Enter');

        // Initial task
        await page.type('.mynah-chat-prompt-input', 'Add debounce function in operation.js.')
        await page.keyboard.press('Enter');

        await retryIfRequired(page, async () => {
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept all changes)', {timeout: 600_000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await acceptCode.click();
            await page.waitForSelector('.mynah-chat-item-followup-question-option');
            const [newTask, noThanks, generateDevFile] = await page.$$('.mynah-chat-item-followup-question-option');
            await newTask.click();
        });

        // New task
        await page.type('.mynah-chat-prompt-input', 'Add throttle function in operation.js.')
        await page.keyboard.press('Enter');

        await retryIfRequired(page, async () => {
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept all changes)', {timeout: 600_000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await acceptCode.click();
            await page.waitForSelector('.mynah-chat-item-followup-question-option');
            const [newTask, noThanks, generateDevFile] = await page.$$('.mynah-chat-item-followup-question-option');
            await noThanks.click();
        });

        // Validate if /dev ends the conversation successfully.
        await page.waitForSelector('p ::-p-text(Okay, I\'ve ended this chat session. You can open a new tab to chat or start another workflow.)', {timeout: 3000});
        console.log('Success: /dev ends the conversation successfully.');
    }
    finally {
        await closeSelectedTab(page);
        await browser.close();
    }
}
testNewCodeGen().catch(console.error);
""".trimIndent()

val testPartialCodeGen = """
$testScriptPrefix

async function testPartialCodeGen() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222",
        protocolTimeout: 750_000,
    });

    const page = (await browser.pages()).find((p) => p.url().startsWith('file:'));

    try {
        // Ensure page is ready to evaluate
        await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
        await page.waitForSelector('.mynah-chat-prompt-input');

        // Ensure prompt input is visible
        await page.waitForSelector('.mynah-chat-prompt-input');

        // Enter initial prompt
        await page.type('.mynah-chat-prompt-input', '/dev Add debounce function in operation.js and add explain what you did in CHANGELOG.md')
        await page.keyboard.press('Enter');

        await retryIfRequired(page, async () => {
            // Ensure tree view is visiable and de-select a modified file
            await page.waitForSelector('.mynah-chat-item-tree-view-file-item-actions .error', {timeout: 600_000});
            await page.locator('.mynah-chat-item-tree-view-file-item-actions .error').click();

            // Accept code change
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept remaining changes)', {timeout: 3000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await acceptCode.click();

            // End the conversation
            await page.waitForSelector('.mynah-chat-item-followup-question-option');
            const [newTask, noThanks, generateDevFile] = await page.$$('.mynah-chat-item-followup-question-option');
            await noThanks.click();
        });

        // Validate if /dev ends the conversation successfully
        await page.waitForSelector('p ::-p-text(Okay, I\'ve ended this chat session. You can open a new tab to chat or start another workflow.)', {timeout: 3000});
        console.log('Success: /dev ends the conversation successfully.');

    } finally {
        // Close current tab before disconnect browser
         await closeSelectedTab(page);
         await browser.close();
    }
}

testPartialCodeGen().catch(console.error);
""".trimIndent()

val testStopAndRestartCodeGen = """
$testScriptPrefix

async function testStopAndRestartCodeGen() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222",
        protocolTimeout: 750_000,
    });

    const page = (await browser.pages()).find((p) => p.url().startsWith('file:'));

    try {
        const contents = await page.evaluate(el => el.innerHTML, await page.${'$'}(':root'));
        await page.waitForSelector('.mynah-chat-prompt-input');
        await page.type('.mynah-chat-prompt-input', '/dev Add debounce function in operation.js.')
        await page.keyboard.press('Enter');

        const stopBtn = await page.${'$'}('.loading .mynah-chat-stop-chat-response-button', {visible: true});
        await stopBtn.click();

        await retryIfRequired(page, async () => {
            await page.waitForSelector('.mynah-chat-item-followup-question-option ::-p-text(Accept all changes)', {timeout: 600_000});
            const [acceptCode, iterateCode] = await page.$$('.mynah-chat-item-followup-question-option');
            await acceptCode.click();

            await page.waitForSelector('.mynah-chat-item-followup-question-option');
            const [newTask, noThanks, generateDevFile] = await page.$$('.mynah-chat-item-followup-question-option');
            await noThanks.click();
        });

        // Validate if /dev ends the conversation successfully.
        await page.waitForSelector('p ::-p-text(Okay, I\'ve ended this chat session. You can open a new tab to chat or start another workflow.)', {timeout: 3000});
        console.log('Success: /dev ends the conversation successfully.');

    } finally {
        await closeSelectedTab(page);
        await browser.close();
    }
}

testStopAndRestartCodeGen().catch(console.error);

""".trimIndent()
