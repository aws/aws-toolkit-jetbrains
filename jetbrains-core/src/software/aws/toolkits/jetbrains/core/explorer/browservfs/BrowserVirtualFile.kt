// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.browservfs

import com.intellij.testFramework.LightVirtualFile
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion

class BrowserVirtualFile(val creds: ToolkitCredentialsProvider, val region: AwsRegion) : LightVirtualFile(creds.displayName) {
    override fun equals(other: Any?): Boolean {
        if (other !is BrowserVirtualFile) {
            return false
        }
        return creds == (other as? BrowserVirtualFile)?.creds
    }
}
