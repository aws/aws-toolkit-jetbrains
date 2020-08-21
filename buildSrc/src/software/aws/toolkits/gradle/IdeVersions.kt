// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.gradle.api.Project

open class ProductProfile(
    val sdkVersion: String,
    val plugins: Array<String>
)

class RiderProfile(
    sdkVersion: String,
    plugins: Array<String>,
    val rdGenVersion: String,
    val nugetVersion: String
) : ProductProfile(sdkVersion, plugins)

class Profile(
    val name: String,
    val shortName: String = shortenedIdeProfileName(name),
    val sinceVersion: String = shortName,
    val untilVersion: String = "$sinceVersion.*",
    pythonVersion: String,
    communityPythonVersion: String = pythonVersion,
    dockerVersion: String,
    riderSdkOverride: String? = null,
    rdGenVersion: String,
    nugetVersion: String
) {
    private val commonPlugins = arrayOf(
        "org.jetbrains.plugins.terminal",
        "org.jetbrains.plugins.yaml"
    )

    private val ultimatePlugins = commonPlugins + arrayOf(
        "Pythonid:$pythonVersion",
        "JavaScript",
        "JavaScriptDebugger"
    )

    private val communityPlugins = commonPlugins + arrayOf(
        "PythonCore:$communityPythonVersion",
        "java",
        "com.intellij.gradle",
        "org.jetbrains.idea.maven",
        "Docker:$dockerVersion"
    )

    val community: ProductProfile = ProductProfile(sdkVersion = "IC-$name", plugins = communityPlugins)
    val ultimate: ProductProfile = ProductProfile(sdkVersion = "IU-$name", plugins = ultimatePlugins)
    val rider: RiderProfile = RiderProfile(
        sdkVersion = riderSdkOverride ?: "RD-$name",
        plugins = arrayOf("org.jetbrains.plugins.yaml"),
        rdGenVersion = rdGenVersion,
        nugetVersion = nugetVersion
    )
}

object IdeVersions {
    fun ideProfile(project: Project): Profile {
        val profileName = resolveIdeProfileName(project)
        return ideProfiles[profileName] ?: throw IllegalStateException("Can't find profile for $profileName")
    }

    private val ideProfiles = listOf(
        Profile(
            name = "2019.3",
            pythonVersion = "193.5233.109",
            communityPythonVersion = "193.5233.139",
            dockerVersion = "193.5233.140",
            riderSdkOverride = "RD-2019.3.4",
            rdGenVersion = "0.193.146",
            nugetVersion = "2019.3.4"
        ),
        Profile(
            name = "2020.1",
            pythonVersion = "201.6668.31",
            dockerVersion = "201.6668.30",
            riderSdkOverride = "RD-2020.1.0",
            rdGenVersion = "0.201.69",
            nugetVersion = "2020.1.0"
        ),
        Profile(
            name = "2020.2",
            pythonVersion = "202.6397.98",
            communityPythonVersion = "202.6397.124",
            dockerVersion = "202.6397.93",
            rdGenVersion = "0.202.113",
            nugetVersion = "2020.2.0"
        )
    ).map { it.name to it }.toMap()

    private fun resolveIdeProfileName(project: Project): String = if (System.getenv()["ALTERNATIVE_IDE_PROFILE_NAME"] != null) {
        System.getenv("ALTERNATIVE_IDE_PROFILE_NAME")
    } else {
        project.properties["ideProfileName"]?.toString() ?: throw IllegalStateException("No ideProfileName property set")
    }
}

private fun shortenedIdeProfileName(sdkName: String): String {
    val parts = sdkName.trim().split(".")
    return parts[0].substring(2) + parts[1]
}
