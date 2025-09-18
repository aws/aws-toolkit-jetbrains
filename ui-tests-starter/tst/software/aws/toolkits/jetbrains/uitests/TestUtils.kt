// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

import com.intellij.ide.starter.ide.IDETestContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

private const val TEST_RESOURCES_PATH = "src/test/tstData"

@OptIn(ExperimentalPathApi::class)
fun IDETestContext.copyExistingConfig(configPath: Path): IDETestContext {
    configPath.copyToRecursively(paths.configDir, followLinks = false, overwrite = true)
    return this
}
fun executePuppeteerScript(@Language("JS") scriptContent: String): String {
    val scriptFile = File("$TEST_RESOURCES_PATH/temp-script.js")
    scriptFile.parentFile.mkdirs()
    scriptFile.writeText(scriptContent)

    val process = ProcessBuilder()
        .command("node", scriptFile.absolutePath)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    scriptFile.delete()

    assertThat(exitCode).withFailMessage("Script execution failed with output: $output").isEqualTo(0)
    return output
}

fun useExistingConnectionForTest() {
    val testStartUrl = System.getenv("TEST_START_URL")
    val testRegion = System.getenv("TEST_REGION")
    val configContent =
        """
       <application>
        <component name="authManager">
            <option name="ssoProfiles">
                <list>
                    <ManagedSsoProfile>
                        <option name="scopes">
                            <list>
                                <option value="codewhisperer:conversations" />
                                <option value="codewhisperer:transformations" />
                                <option value="codewhisperer:taskassist" />
                                <option value="codewhisperer:completions" />
                                <option value="codewhisperer:analysis" />
                            </list>
                        </option>
                        <option name="ssoRegion" value="$testRegion" />
                        <option name="startUrl" value="$testStartUrl" />
                    </ManagedSsoProfile>
                </list>
            </option>
        </component>
        <component name="connectionPinningManager">
            <option name="pinnedConnections">
                <map>
                    <entry key="aws.codewhisperer" value="sso;$testRegion;$testStartUrl" />
                    <entry key="aws.q" value="sso;$testRegion;$testStartUrl" />
                </map>
            </option>
        </component>
      <component name="meetQPage">
        <option name="shouldDisplayPage" value="false" />
      </component>
    </application>
        """.trimIndent()
    writeToAwsXml(configContent)
}

fun setupMultipleProfilesForTest() {
    val testStartUrl = System.getenv("TEST_START_URL")
    val testRegion = System.getenv("TEST_REGION")
    val configContent = """
               <application>
                <component name="authManager">
                    <option name="ssoProfiles">
                        <list>
                            <ManagedSsoProfile>
                                <option name="scopes">
                                    <list>
                                        <option value="codewhisperer:conversations" />
                                        <option value="codewhisperer:transformations" />
                                        <option value="codewhisperer:taskassist" />
                                        <option value="codewhisperer:completions" />
                                        <option value="codewhisperer:analysis" />
                                    </list>
                                </option>
                                <option name="ssoRegion" value="$testRegion" />
                                <option name="startUrl" value="$testStartUrl" />
                            </ManagedSsoProfile>
                        </list>
                    </option>
                </component>
                <component name="connectionPinningManager">
                    <option name="pinnedConnections">
                        <map>
                            <entry key="aws.codewhisperer" value="sso;$testRegion;$testStartUrl" />
                            <entry key="aws.q" value="sso;$testRegion;$testStartUrl"  />
                        </map>
                    </option>
                </component>
              <component name="meetQPage">
                <option name="shouldDisplayPage" value="false" />
              </component>
            </application>
    """.trimIndent()
    writeToAwsXml(configContent)
}

fun setupMultipleProfilesWithSelectionForTest() {
    val testRegion = System.getenv("TEST_REGION")
    val testStartUrl = System.getenv("TEST_START_URL")
    val testArn = System.getenv("TEST_ARN")
    val testProfile = System.getenv("TEST_PROFILE")
    val testProfileName = testStartUrl.substringAfter("https://").substringBefore(".awsapps.com")
    val configContent = """
            <application>
              <component name="authManager">
                <option name="lastLoginIdcInfo">
                  <LastLoginIdcInfo>
                    <option name="profileName" value="$testProfileName" />
                    <option name="region" value="$testRegion" />
                    <option name="startUrl" value="$testStartUrl" />
                  </LastLoginIdcInfo>
                </option>
              </component>
              <component name="aws">
                <option name="autoUpdateFeatureNotificationShownOnce" value="true" />
                <option name="profilesNotification" value="Never" />
                <option name="promptedForTelemetry" value="true" />
              </component>
              <component name="codewhispererSettings">
                <option name="value">
                  <map>
                    <entry key="IsProjectContextEnabled" value="true" />
                  </map>
                </option>
              </component>
              <component name="codewhispererStates">
                <option name="value">
                  <map>
                    <entry key="IsMonthlyQuotaForCodeScansExceeded" value="false" />
                    <entry key="IsFirstRestartAfterQInstall" value="false" />
                  </map>
                </option>
              </component>
              <component name="connectionPinningManager">
                <option name="pinnedConnections">
                  <map>
                    <entry key="aws.q" value="sso-session:$testProfileName" />
                  </map>
                </option>
              </component>
              <component name="meetQPage">
                <option name="disclaimerAcknowledged" value="true" />
                <option name="shouldDisplayPage" value="false" />
              </component>
              <component name="qProfileStates">
                <option name="connectionIdToActiveProfile">
                  <map>
                    <entry key="sso-session:$testProfileName">
                      <value>
                        <QRegionProfile>
                          <option name="arn" value="$testArn" />
                          <option name="profileName" value="$testProfile" />
                        </QRegionProfile>
                      </value>
                    </entry>
                  </map>
                </option>
                <option name="connectionIdToProfileList">
                  <map>
                    <entry key="sso-session:$testProfileName" value="2" />
                  </map>
                </option>
              </component>
            </application>
    """.trimIndent()
    writeToAwsXml(configContent)
}

fun clearAwsXmlFile() {
    val configContent =
        """
       <application>
        
    </application>
        """.trimIndent()
    writeToAwsXml(configContent)
}

fun setupTestEnvironment() {
    // Ensure Puppeteer is installed
    val npmInstall = ProcessBuilder()
        .command("npm", "install", "puppeteer")
        .inheritIO()
        .start()
        .waitFor()

    assertThat(npmInstall).withFailMessage("Failed to install Puppeteer").isEqualTo(0)
}

fun writeToAwsXml(@Language("XML") configContent: String) {
    val path = Paths.get("tstData", "configAmazonQTests", "options", "aws.xml")

    Files.createDirectories(path.parent)
    Files.write(
        path,
        configContent.toByteArray(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    )
}

fun String.normalizeWhitespace(): String = this.replace(Regex("\\s+"), " ").trim()

// language=JS
val testScriptPrefix = """
const puppeteer = require('puppeteer');

async function retryIfRequired(page, request) {
    let retryCounts = 0;
    const maxRetries = 3;

    async function retry() {

        await page.waitForSelector('::-p-text(Retry)', {timeout: 600_000})

        const [retry] = await page.$$('.mynah-chat-item-followup-question-option');
        await retry.click();

        console.log(`Retrying ${'$'}{++retryCounts} time(s)`);
    }

    while (retryCounts < maxRetries) {
        const currRetryCounts = retryCounts;
        await Promise.race([
            retry(),
            request()
        ]);

        // This indicates request proceed successfully. There is no more need to retry.
        if (retryCounts === currRetryCounts) {
            return;
        }
    }
}

async function closeSelectedTab(page) {
    const selectedTabId = await page.${'$'}eval('.mynah-nav-tabs-wrapper', (el) => el.getAttribute('selected-tab'));

    const closeBtn = await page.${'$'}(`span[key=mynah-main-tabs-${'$'}{selectedTabId}] label button`);
    await closeBtn.click();
}

""".trimIndent()
