// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.transformTests

import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.clearAwsXmlFile
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

// language=JS
val transformHappyPathScript = """
const puppeteer = require('puppeteer');
async function testNavigation() {
    const browser = await puppeteer.connect({
        browserURL: "http://localhost:9222"
    })
    try {
        const pages = await browser.pages()
        for (const page of pages) {
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

            const selectCustomVersionsForm = await page.waitForSelector('button[action-id="codetransform-input-confirm-custom-dependency-versions"]', {
                timeout: 5000
            })
            console.log('Custom dependency versions file form appeared:', selectCustomVersionsForm !== null)

            await page.evaluate(() => {
                const button = document.querySelector('button[action-id="codetransform-input-continue"]')
                button.click()
            })

            await page.type('.mynah-chat-prompt-input', 'dummy-target-jdk-name-here')
            await page.keyboard.press('Enter')

            const errorMessage = await page.waitForSelector('text/I could not find "dummy-target-jdk-name-here" in File > Project Structure > Platform Settings > SDKs.', {
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

class TransformChatTest {

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { TestCIServer }
            val defaults = ConfigurationStorage.instance().defaults.toMutableMap().apply {
                put("LOG_ENVIRONMENT_VARIABLES", (!System.getenv("CI").toBoolean()).toString())
            }

            bindSingleton<ConfigurationStorage>(overrides = true) {
                ConfigurationStorage(this, defaults)
            }
        }
    }

    @BeforeEach
    fun setUp() {
        setupTestEnvironment()
    }

    @Test
    fun `can run a transformation from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // inject connection
        useExistingConnectionForTest()

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "configAmazonQTests"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                // required wait time for the system to be fully ready
                Thread.sleep(30000)
                val result = executePuppeteerScript(transformHappyPathScript)
                assertThat(result).contains(
                    "Choose a module to transform",
                    "Choose the target code version",
                    "Skip tests form appeared: true",
                    "Custom dependency versions file form appeared: true",
                    "I could not find \"dummy-target-jdk-name-here\""
                )
            }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun clearAwsXml() {
            clearAwsXmlFile()
        }
    }
}
