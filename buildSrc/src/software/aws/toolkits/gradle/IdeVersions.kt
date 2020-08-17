// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.gradle.api.Project

enum class ProductCode {
    IC
}

data class ProductProfile(
    val sdkVersion: String,
    val plugins: List<String>
)

data class Profile(
    val sinceVersion: String,
    val untilVersion: String,
    val products: Map<ProductCode, ProductProfile>
)

open class IdeVersions(private val project: Project) {
    val ideProfiles = mapOf(
        "2019.3" to Profile(
            sinceVersion = "193",
            untilVersion = "193.*",
            products = mapOf(
                ProductCode.IC to ProductProfile(
                    sdkVersion = "IC-2019.3",
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

    // Convert (as an example) 2020.2 -> 202
    fun resolveShortenedIdeProfileName(): String {
        val profileName = resolveIdeProfileName().trim()
        val parts = profileName.split(".")
        return parts[0].substring(2) + parts[1]
    }

    fun ideSdkVersion(code: ProductCode): String = ideProfiles[resolveIdeProfileName()]
        ?.products
        ?.get(code)
        ?.sdkVersion
        ?: throw IllegalArgumentException("Product not in map of IDE versions: ${resolveIdeProfileName()}, $code")

    private fun resolveIdeProfileName(): String = if (System.getenv()["ALTERNATIVE_IDE_PROFILE_NAME"] != null) {
        System.getenv("ALTERNATIVE_IDE_PROFILE_NAME")
    } else {
        project.properties["ideProfileName"]?.toString() ?: throw IllegalStateException("No ideProfileName property set")
    }
}
