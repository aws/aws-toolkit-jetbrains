// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package toolkits.gradle

import org.gradle.api.Project

data class ProductProfile(
    val sdkVersion: String,
    val plugins: List<String>
)

data class Profile(
    val sinceVersion: String,
    val untilVersion: String,
    val products: Map<String, ProductProfile>
)

open class IdeVersions(private val project: Project) {
    val ideProfiles = mapOf(
        "2019.3" to Profile(
            sinceVersion = "193",
            untilVersion = "193.*",
            products = mapOf(
                "IC" to ProductProfile(
                    "IC-2019.3",
                    plugins = listOf(
                        "org.jetbrains.plugins.terminal",
                        "org.jetbrains.plugins.yaml",
                        "PythonCore:193.5233.139",
                        "java",
                        "com.intellij.gradle",
                        "org.jetbrains.idea.maven",
                        "Docker:193.5233.140"
                    )
                )
            )
        )
    )

    fun resolveIdeProfileName(): String = if (System.getenv()["ALTERNATIVE_IDE_PROFILE_NAME"] != null) {
        System.getenv("ALTERNATIVE_IDE_PROFILE_NAME")
    } else {
        project.properties["ideProfileName"]?.toString() ?: throw IllegalStateException("No ideProfileName property set")
    }
}
