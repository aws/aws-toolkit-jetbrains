// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.profiles.ProfileCredentialsIdentifier
import software.aws.toolkits.jetbrains.core.explorer.showExplorerTree

class RefreshExplorerCredentials(val project: Project) : ChangeConnectionSettingIfValid {

    override fun changeConnection(profile: ProfileCredentialsIdentifier) {
        super.changeConnection(profile)
        println("RefreshExplorerCredentials:: ${profile.profileName}")
        AwsConnectionManager.getInstance(project).changeCredentialProvider(profile)
    }
}
