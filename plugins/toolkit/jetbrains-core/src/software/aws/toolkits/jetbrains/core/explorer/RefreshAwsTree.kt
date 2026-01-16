// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.ConnectionSettings
import software.aws.toolkit.jetbrains.core.AwsResourceCache
import software.aws.toolkit.jetbrains.core.Resource
import software.aws.toolkit.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.DevToolsToolWindow

fun Project.refreshAwsTree(resource: Resource<*>? = null, connectionSettings: ConnectionSettings = getConnectionSettingsOrThrow()) {
    if (resource == null) {
        AwsResourceCache.getInstance().clear(connectionSettings)
    } else {
        AwsResourceCache.getInstance().clear(resource, connectionSettings)
    }

    runInEdt {
        // redraw explorer
        ExplorerToolWindow.getInstance(this).invalidateTree()
    }
}

fun Project.refreshDevToolTree() {
    runInEdt {
        if (this.isDisposed) return@runInEdt
        DevToolsToolWindow.getInstance(this).redrawContent()
    }
}
