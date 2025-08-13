// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.intellij

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/** The subset of [org.jetbrains.intellij.platform.gradle.IntelliJPlatformType] that are relevant to us **/
enum class IdeFlavor {
    GW,
    /* free */
    AI, IC, PC,
    /* paid */
    /* RR is 'non-commerical free' but treat as paid */
    DB, CL, GO, IU, PS, PY, RM, RR, WS,
    RD,
}

object IdeVersions {
    private val commonPlugins = listOf(
        "Git4Idea",
        "org.jetbrains.plugins.terminal",
        "org.jetbrains.plugins.yaml"
    )

    private val ideProfiles = listOf(
        Profile(
            name = "2024.2",
            community = ProductProfile(
                sdkVersion = "2024.2",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:242.20224.155",
                    "PythonCore:242.20224.300",
                    "Docker:242.20224.237"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2024.2",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database",
                    "com.jetbrains.codeWithMe",
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:242.20224.155",
                    "PythonCore:242.20224.300",
                    "org.jetbrains.plugins.go:242.20224.300",
                )
            ),
            rider = RiderProfile(
                sdkVersion = "2024.2",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2024.1.1",
                nugetVersion = " 2024.2.0"
            )
        ),
        Profile(
            name = "2024.3",
            community = ProductProfile(
                sdkVersion = "2024.3",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:243.21565.122",
                    "PythonCore:243.21565.211",
                    "Docker:243.21565.204",
                    "com.intellij.modules.json:243.26574.91"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2024.3",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database",
                    "com.jetbrains.codeWithMe",
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:243.21565.122",
                    "Pythonid:243.21565.211",
                    "org.jetbrains.plugins.go:243.21565.211",
                    "com.intellij.modules.json:243.26574.91"
                )
            ),
            rider = RiderProfile(
                sdkVersion = "2024.3",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2024.3.0",
                nugetVersion = " 2024.3.0"
            )
        ),
        Profile(
            name = "2025.1",
            gateway = ProductProfile(
                sdkVersion = "2025.1",
                bundledPlugins = listOf("org.jetbrains.plugins.terminal")
            ),
            community = ProductProfile(
                sdkVersion = "2025.1",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:251.26927.47",
                    "PythonCore:251.23774.460",
                    "Docker:251.23774.466",
                    "com.intellij.modules.json:251.27812.12"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2025.1",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database",
                    "com.jetbrains.codeWithMe",
                    "intellij.grid.plugin",
                    "intellij.charts"
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:251.26927.47",
                    "Pythonid:251.23774.460",
                    "PythonCore:251.23774.460",
                    "org.jetbrains.plugins.go:251.23774.435",
                    "com.intellij.modules.json:251.27812.12"
                )
            ),
            rider = RiderProfile(
                sdkVersion = "2025.1",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2025.1.1",
                nugetVersion = " 2025.1.0"
            )
        ),
        Profile(
            name = "2025.2",
            gateway = ProductProfile(
                sdkVersion = "2025.2",
                bundledPlugins = listOf("org.jetbrains.plugins.terminal")
            ),
            community = ProductProfile(
                sdkVersion = "2025.2",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                    "com.intellij.properties"
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:252.23892.464",
                    "PythonCore:252.23892.458",
                    "Docker:252.23892.464",
                    "com.intellij.modules.json:252.23892.360"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2025.2",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database",
                    "com.jetbrains.codeWithMe",
                ),
                marketplacePlugins = listOf(
                    "Pythonid:252.23892.458",
                    "org.jetbrains.plugins.go:252.23892.360",
                    "com.intellij.modules.json:252.23892.360"
                )
            ),
            rider = RiderProfile(
                sdkVersion = "2025.2-SNAPSHOT",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2025.2.2",
                nugetVersion = "2025.2.0-rc02"
            )
        )
    ).associateBy { it.name }

    fun ideProfile(project: Project): Profile = ideProfile(project.providers).get()

    fun ideProfile(providers: ProviderFactory): Provider<Profile> = resolveIdeProfileName(providers).map {
        ideProfiles[it] ?: throw IllegalStateException("Can't find profile for $it")
    }

    private fun resolveIdeProfileName(providers: ProviderFactory): Provider<String> = providers.gradleProperty("ideProfileName")
}

open class ProductProfile(
    val sdkVersion: String,
    val bundledPlugins: List<String> = emptyList(),
    val marketplacePlugins: List<String> = emptyList()
)

class RiderProfile(
    sdkVersion: String,
    val netFrameworkTarget: String,
    val rdGenVersion: String, // https://central.sonatype.com/artifact/com.jetbrains.rd/rd-gen/2023.2.3/versions
    val nugetVersion: String, // https://www.nuget.org/packages/JetBrains.Rider.SDK/
    bundledPlugins: List<String> = emptyList(),
    marketplacePlugins: List<String> = emptyList(),
) : ProductProfile(sdkVersion, bundledPlugins, marketplacePlugins)

class Profile(
    val name: String,
    val shortName: String = shortenedIdeProfileName(name),
    val sinceVersion: String = shortName,
    val untilVersion: String = "$sinceVersion.*",
    val gateway: ProductProfile? = null,
    val community: ProductProfile,
    val ultimate: ProductProfile,
    val rider: RiderProfile,
)

private fun shortenedIdeProfileName(sdkName: String): String {
    val parts = sdkName.trim().split(".")
    return parts[0].substring(2) + parts[1]
}
