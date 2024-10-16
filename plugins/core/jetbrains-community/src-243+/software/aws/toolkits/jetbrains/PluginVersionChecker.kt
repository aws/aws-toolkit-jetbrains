// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.ide.ApplicationInitializedListener

class PluginVersionChecker : ApplicationInitializedListener {
    override suspend fun execute() {
        PluginVersionCheckerImpl.execute()
    }
}
