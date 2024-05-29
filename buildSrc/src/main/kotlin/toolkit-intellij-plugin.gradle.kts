// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension

private val toolkitIntelliJ = project.extensions.create<ToolkitIntelliJExtension>("intellijToolkit")

plugins {
    id("org.jetbrains.intellij.platform")
}

intellijPlatform {
    instrumentCode = false
}

// there is an issue if this is declared more than once in a project (either directly or through script plugins)
repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

// hack for fix in beta4
//   - In plugin 'org.jetbrains.intellij.platform.gradle.plugins.project.IntelliJPlatformBasePlugin$Inject' type 'org.jetbrains.intellij.platform.gradle.tasks.GenerateManifestTask' property 'kotlinStdlibBundled' doesn't have a configured value.
//
//    Reason: This property isn't marked as optional and no value has been configured.
//
//    Possible solutions:
//      1. Assign a value to 'kotlinStdlibBundled'.
//      2. Mark property 'kotlinStdlibBundled' as optional.
tasks.generateManifest {
    kotlinStdlibBundled = false
}
