// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class TransformChatTest {

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { TestCIServer }
        }
    }

    private val testResourcesPath = "src/test/tstData"

    @BeforeEach
    fun setUp() {
        // Setup test environment
        setupTestEnvironment()
    }
    private fun setupTestEnvironment() {
        // Ensure Puppeteer is installed
        val npmInstall = ProcessBuilder()
            .command("npm", "install", "puppeteer")
            .inheritIO()
            .start()
            .waitFor()

        assertEquals(0, npmInstall, "Failed to install Puppeteer")

    }

    @Test
    fun `can open up IDE`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).useRelease("2024.3")


        Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache", "ee1d2538cb8d358377d7661466c866af747a8a3f.json")
            .createParentDirectories()
            .writeText(
                """
                    auth goes here
                """.trimIndent()
            )

        Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache", "d3b447f809607422aac1470dd17fbb32e358cdb3.json")
            .writeText(
                """
                    auth goes here
                """.trimIndent()
            )

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "config"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                Thread.sleep(20000)


                val result = executeScript(scr)
                assertTrue(result.contains("Choose a module to transform"))
                assertTrue(result.contains("Choose the target code version"))
                assertTrue(result.contains("Skip tests form appeared: true"))
                assertTrue(result.contains("One or multiple diffs form appeared: true"))
                assertTrue(result.contains("couldn't run the Maven clean install command"))
            }
    }

    private fun executeScript(scriptContent: String): String {
        val scriptFile = File("$testResourcesPath/temp-script.js")
        scriptFile.parentFile.mkdirs()
        scriptFile.writeText(scriptContent)

        val process = ProcessBuilder()
            .command("node", scriptFile.absolutePath)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        scriptFile.delete()

        assertEquals(0, exitCode, "Script execution failed with output: $output")
        return output
    }

}

private val scr = """
const puppeteer = require('puppeteer');
async function testNavigation() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for (const page of pages) {
            const contents = await page.evaluate(el => el.innerHTML, await page.$(':root'))
            const element = await page.$('.mynah-chat-prompt-input')
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
