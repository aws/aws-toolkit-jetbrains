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

class AmazonQChatTest {

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



//        Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache", "ee1d2538cb8d358377d7661466c866af747a8a3f.json")
//            .createParentDirectories()
//            .writeText(
//                """
//                  paste your client reg here
//                """.trimIndent()
//            )
//
//        Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache", "d3b447f809607422aac1470dd17fbb32e358cdb3.json")
//            .writeText(
//                """
//               paste your access token here
//                """.trimIndent()
//            )

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
                assertTrue(result.contains("/doc"))
                assertTrue(result.contains("/dev"))
                assertTrue(result.contains("/transform"))
                assertTrue(result.contains("/help"))
                assertTrue(result.contains("/clear"))
                assertTrue(result.contains("/review"))
                assertTrue(result.contains("/test"))
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
import puppeteer from "puppeteer";

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
            const element = await page.$('.mynah-chat-prompt-input')
            if(element) {
                console.log("found")

                await page.type('.mynah-chat-prompt-input', '/')
                const elements = await page.$$(".mynah-chat-command-selector-command");
                const attr = await Promise.all(
                    elements.map(async element => {
                        return element.evaluate(el => el.getAttribute("command"));
                    })
                );

               

                console.log("found commands")
                console.log(JSON.stringify(attr, null, 2))

            }
        }


    } finally {
        await browser.close();
    }
}

testNavigation().catch(console.error);

""".trimIndent()
