// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.profiles.ProfileProperty.SSO_ACCOUNT_ID
import software.amazon.awssdk.profiles.ProfileProperty.SSO_REGION
import software.amazon.awssdk.profiles.ProfileProperty.SSO_ROLE_NAME
import software.amazon.awssdk.profiles.ProfileProperty.SSO_START_URL
import software.aws.toolkits.jetbrains.core.credentials.SsoProfileConstants.SSO_SESSION_PROFILE_NAME
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants.PROFILE_SSO_SESSION_PROPERTY
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants.SSO_SESSION_SECTION_NAME

class SsoSessionConfigurationManagerTest {
    private lateinit var ssoSessionConfigurationManager: SsoSessionConfigurationManager
    private val testProfileName = "testProfile"
    private val testRegion = "us-west-1"
    private val testStartUrl = "https://example.com"
    private val testScopesList = listOf("scope1", "scope2")
    private val testAccountId = "123456789"
    private val testRoleName = "TestRole"

    @BeforeEach
    fun setUp() {
        ssoSessionConfigurationManager = SsoSessionConfigurationManager()
    }

    @Test
    fun `write sso-session profile to config file`() {
        ssoSessionConfigurationManager.writeSsoSessionProfileToConfigFile(
            testProfileName,
            testRegion,
            testStartUrl,
            testScopesList,
            testAccountId,
            testRoleName
        )

        val fileContents = ssoSessionConfigurationManager.profileFile.readText()
        val expectedContents = """
            [$SSO_SESSION_PROFILE_NAME $testProfileName]
            $PROFILE_SSO_SESSION_PROPERTY=$testProfileName
            $SSO_ACCOUNT_ID=$testAccountId
            $SSO_ROLE_NAME=$testRoleName
            
            [$SSO_SESSION_SECTION_NAME $testProfileName]
            $SSO_REGION=$testRegion
            $SSO_START_URL=$testStartUrl
            ${SsoSessionConstants.SSO_REGISTRATION_SCOPES}=${testScopesList.joinToString(",")}
        """.trimIndent()

        assert(fileContents.trim().contains(expectedContents.trim()))
    }
}
