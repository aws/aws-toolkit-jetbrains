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
        "org.jetbrains.plugins.yaml",
    )

    private val ideProfiles = listOf(
        Profile(
            name = "2025.3",
            gateway = ProductProfile(
                sdkVersion = "2025.3",
                bundledPlugins = listOf("org.jetbrains.plugins.terminal")
            ),
            community = ProductProfile(
                sdkVersion = "2025.3",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                    "com.intellij.properties"
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:253.28294.334",
                    "PythonCore:253.29346.138",
                    "Docker:253.29346.125",
                    "com.intellij.modules.json:253.28294.251"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2025.3",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database",
                    "com.jetbrains.codeWithMe"
                ),
                marketplacePlugins = listOf(
                    "Pythonid:253.29346.138",
                    "org.jetbrains.plugins.go:253.29346.50",
                    "com.intellij.modules.json:253.28294.251"
                )
            ),
            rider = RiderProfile(
                sdkVersion = "2025.3",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2025.3.1",
                nugetVersion = "2025.3.0"
            )
        ),
        Profile(
            name = "2026.1",
            gateway = ProductProfile(
                sdkVersion = "2026.1",
                bundledPlugins = listOf("org.jetbrains.plugins.terminal")
            ),
            community = ProductProfile(
                sdkVersion = "2026.1",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                    "com.intellij.properties"
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:261.22158.185",
                    "PythonCore:261.22158.277",
                    "Docker:261.22158.299",
                    "com.intellij.modules.json:261.22158.182"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2026.1",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database"
                ),
                marketplacePlugins = listOf(
                    "Pythonid:261.22158.277",
                    "org.jetbrains.plugins.go:261.22158.277",
                    "com.intellij.modules.json:261.22158.182"
                )
            ),
            rider = RiderProfile(
                sdkVersion = "2026.1",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2026.1.3",
                nugetVersion = "2026.1.0"
            )
        ),
        Profile(
            name = "2026.2",
            gateway = ProductProfile(
                sdkVersion = "2026.2",
                bundledPlugins = listOf("org.jetbrains.plugins.terminal")
            ),
            community = ProductProfile(
                sdkVersion = "2026.2",
                bundledPlugins = commonPlugins + listOf(
                    "com.intellij.java",
                    "com.intellij.gradle",
                    "org.jetbrains.idea.maven",
                    "com.intellij.properties",
                    // JCEF split into its own bundled plugin ("com.intellij.modules.jcef") in 2026.2.
                    // Needed on the compile classpath; runtime edge declared via <depends> in plugin.xml.
                    "com.intellij.modules.jcef"
                ),
                marketplacePlugins = listOf(
                    "org.toml.lang:262.8665.176",
                    "PythonCore:262.8665.258",
                    "Docker:262.8665.185",
                    "com.intellij.modules.json:262.8665.176"
                )
            ),
            ultimate = ProductProfile(
                sdkVersion = "2026.2",
                bundledPlugins = commonPlugins + listOf(
                    "JavaScript",
                    "JavaScriptDebugger",
                    "com.intellij.database",
                    // JCEF split into its own bundled plugin ("com.intellij.modules.jcef") in 2026.2.
                    // Needed on the compile classpath; runtime edge declared via <depends> in plugin.xml.
                    "com.intellij.modules.jcef"
                ),
                marketplacePlugins = listOf(
                    "Pythonid:262.8665.258",
                    "org.jetbrains.plugins.go:262.8665.258",
                    "com.intellij.modules.json:262.8665.176"
                )
            ),
            rider = RiderProfile(
                // Rider 2026.2 is still pre-release (RC1) with a closed-source backend API that shifted; the
                // jetbrains-rider module is excluded from the 2026.2 build in settings.gradle.kts, so this entry
                // is currently unused. Re-enable the module and switch to "2026.2" / "2026.2.0" once Rider GAs.
                sdkVersion = "2026.2-RC1-SNAPSHOT",
                bundledPlugins = commonPlugins,
                netFrameworkTarget = "net472",
                rdGenVersion = "2026.2.4",
                nugetVersion = "2026.2.0-eap06"
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
