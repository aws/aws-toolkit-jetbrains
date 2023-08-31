// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.util.io.FileUtil
import software.amazon.awssdk.profiles.ProfileFileLocation
import software.amazon.awssdk.profiles.ProfileProperty.SSO_ACCOUNT_ID
import software.amazon.awssdk.profiles.ProfileProperty.SSO_REGION
import software.amazon.awssdk.profiles.ProfileProperty.SSO_ROLE_NAME
import software.amazon.awssdk.profiles.ProfileProperty.SSO_START_URL
import software.aws.toolkits.core.credentials.extractOrgID
import software.aws.toolkits.jetbrains.core.credentials.SsoProfileConstants.SSO_SESSION_PROFILE_NAME
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants.PROFILE_SSO_SESSION_PROPERTY
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants.SSO_REGISTRATION_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants.SSO_SESSION_SECTION_NAME

class SsoSessionConfigurationManager {

    val profileFile = ProfileFileLocation.configurationFilePath().toFile()

    // writing sso-session format to the config file
    fun writeSsoSessionProfileToConfigFile(ssoRegion: String, startUrl: String, scopes: List<String>, accountId: String, roleName: String) {
        val ssoSessionName = "${extractOrgID(startUrl)}-$ssoRegion"

        val configContents = buildString {
            // Hardcoded the profile name for now, eventually profileName should populate from Idc dialogbox
            append("\n[$SSO_SESSION_PROFILE_NAME givenProfileName]\n")
            append("$PROFILE_SSO_SESSION_PROPERTY=$ssoSessionName\n")
            append("$SSO_ACCOUNT_ID=$accountId\n")
            append("$SSO_ROLE_NAME=${roleName}\n\n")
            append("[$SSO_SESSION_SECTION_NAME $ssoSessionName]\n")
            append("$SSO_REGION=$ssoRegion\n")
            append("$SSO_START_URL=$startUrl\n")
            append("$SSO_REGISTRATION_SCOPES=${scopes.joinToString(",")}\n")
        }
        writeProfileFile(configContents)
    }

    private fun writeProfileFile(content: String) {
        FileUtil.createIfDoesntExist(profileFile)
        FileUtil.writeToFile(profileFile, content, true)
    }
}

object SsoProfileConstants {
    const val SSO_SESSION_PROFILE_NAME: String = "profile"
}
