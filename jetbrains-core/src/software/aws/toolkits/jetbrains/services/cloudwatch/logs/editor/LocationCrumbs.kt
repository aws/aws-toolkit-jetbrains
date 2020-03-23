// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.util.ui.JBUI
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion

// TODO add actions
class LocationCrumbs(project: Project, logGroup: String, logStream: String? = null) {
    val border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)

    val crumbs = listOfNotNull(
        Crumb.Impl(null, project.activeCredentialProvider().displayName, null, null),
        Crumb.Impl(null, project.activeRegion().displayName, null, null),
        Crumb.Impl(null, logGroup, null, null),
        logStream?.let { Crumb.Impl(null, it, null, null) }
    )
}
