// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.uitests

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.ui
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
import com.intellij.util.io.createParentDirectories
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.writeText

class OfflineAmazonQInlineCompletionTest {
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

    @Test
    fun `completion request with expired credentials does not freeze EDT`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))
        Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache", "ee1d2538cb8d358377d7661466c866af747a8a3f.json")
            .createParentDirectories()
            .writeText(
                """
                {
                  "clientId": "DummyId",
                  "clientSecret": "DummySecret",
                  "expiresAt": "3070-01-01T00:00:00Z",
                  "scopes": [
                    "scope1",
                    "scope2"
                  ],
                  "issuerUrl": "1",
                  "region": "2",
                  "clientType": "public",
                  "grantTypes": [
                    "authorization_code",
                    "refresh_token"
                  ],
                  "redirectUris": [
                    "http://127.0.0.1/oauth/callback"
                  ]
                }
                """.trimIndent()
            )
        Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache", "d3b447f809607422aac1470dd17fbb32e358cdb3.json")
            .writeText(
                """
                {
                  "issuerUrl": "https://example.awsapps.com/start",
                  "region": "us-east-1",
                  "accessToken": "DummyAccessToken",
                  "refreshToken": "RefreshToken",
                  "createdAt": "1970-01-01T00:00:00Z",
                  "expiresAt": "1970-01-01T00:00:00Z"
                }
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
                openFile("Example.java")
                ui.keyboard {
                    // left meta + c
                    repeat(5) { hotKey(18, 67) }
                }
            }
    }
}
