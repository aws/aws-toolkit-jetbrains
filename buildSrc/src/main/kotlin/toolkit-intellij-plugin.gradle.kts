// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
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
        jetbrainsRuntime()
    }
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Platform.JUnit4)
        testFramework(TestFrameworkType.Platform.JUnit5)
    }
}
