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
    private val idcProfileName = "testProfile-123456789-TestRole"
    private val ssoSessionProfileName = "testProfile"
    private val ssoRegion = "us-west-1"
    private val ssoStartUrl = "https://example.com"
    private val scopesList = listOf("scope1", "scope2")
    private val accountId = "123456789"
    private val roleName = "TestRole"

    @BeforeEach
    fun setUp() {
        ssoSessionConfigurationManager = SsoSessionConfigurationManager()
    }

    @Test
    fun `write sso-session profile to config file`() {
        ssoSessionConfigurationManager.writeSsoSessionProfileToConfigFile(
            idcProfileName,
            ssoSessionProfileName,
            ssoRegion,
            ssoStartUrl,
            scopesList,
            accountId,
            roleName
        )

        val fileContents = ssoSessionConfigurationManager.profileFile.readText()
        val expectedContents = """
            [$SSO_SESSION_PROFILE_NAME $idcProfileName]
            $PROFILE_SSO_SESSION_PROPERTY=$ssoSessionProfileName
            $SSO_ACCOUNT_ID=$accountId
            $SSO_ROLE_NAME=$roleName
            
            [$SSO_SESSION_SECTION_NAME $ssoSessionProfileName]
            $SSO_REGION=$ssoRegion
            $SSO_START_URL=$ssoStartUrl
            ${SsoSessionConstants.SSO_REGISTRATION_SCOPES}=${scopesList.joinToString(",")}
        """.trimIndent()

        assert(fileContents.trim().contains(expectedContents.trim()))
    }
}
