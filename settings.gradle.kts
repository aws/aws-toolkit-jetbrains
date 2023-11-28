// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
pluginManagement {
    repositories {
        val codeArtifactUrl: Provider<String> = providers.environmentVariable("CODEARTIFACT_URL")
        val codeArtifactToken: Provider<String> = providers.environmentVariable("CODEARTIFACT_AUTH_TOKEN")
        if (codeArtifactUrl.isPresent && codeArtifactToken.isPresent) {
            println("Using CodeArtifact proxy: ${codeArtifactUrl.get()}")
            maven {
                url = uri(codeArtifactUrl.get())
                credentials {
                    username = "aws"
                    password = codeArtifactToken.get()
                }
            }
        }
        gradlePluginPortal()
    }
}

buildscript {
    // match with version catalog, s3-build-cache has silent classpath conflict with codegen task
    // also since this is a settings plugin, we can't use a version catalog
    // TODO: can we serve a remote cache out of CloudFront instead? https://docs.gradle.org/8.1/userguide/build_cache.html#sec:build_cache_configure_remote
    dependencies {
        classpath(platform("software.amazon.awssdk:bom:2.20.111"))
    }
}

rootProject.name = "aws-toolkit-jetbrains"

include("resources")
include("sdk-codegen")
include("core")
include("jetbrains-core")

when (providers.gradleProperty("ideProfileName").get()) {
    // buildSrc is evaluated after settings so we can't key off of IdeVersions.kt
    "2022.3", "2023.1" -> {}
    else -> {
        include("jetbrains-gateway")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // pull value from IJ library list: https://github.com/JetBrains/intellij-community/blob/<mv>/.idea/libraries/kotlinx_coroutines_jdk8.xml
            //                              or: https://github.com/JetBrains/intellij-community/blob/<mv>/.idea/libraries/kotlinx_coroutines_core.xml
            val version = when (providers.gradleProperty("ideProfileName").get()) {
                "2022.3" -> {
                    // binary compat issue in tests
                    version("kotlin", "1.7.21")
                    "1.6.4"
                }
                "2023.1" -> {
                    "1.6.4"
                }

                "2023.2" -> {
                    "1.7.1"
                }

                "2023.3" -> {
                    "1.7.3"
                }
                else -> { error("not set") }
            }

            version("kotlinxCoroutines", version)
        }
    }
}

include("jetbrains-ultimate")
include("jetbrains-rider")
include("intellij")
include("ui-tests")
include("detekt-rules")
include("mynah-ui")

plugins {
    id("com.gradle.enterprise").version("3.4.1")
    id("com.github.burrunan.s3-build-cache").version("1.5")
}

gradleEnterprise {
    buildScan {
        obfuscation {
            username { "<username>" }
            hostname { "<hostname>" }
            ipAddresses { it.map { "0.0.0.0" } }
        }
    }
}

val regionEnv: Provider<String> = providers.environmentVariable("AWS_REGION")
val bucketEnv: Provider<String> = providers.environmentVariable("S3_BUILD_CACHE_BUCKET")
val prefixEnv: Provider<String> = providers.environmentVariable("S3_BUILD_CACHE_PREFIX")
if (regionEnv.isPresent && bucketEnv.isPresent && prefixEnv.isPresent) {
    buildCache {
        local {
            isEnabled = false
        }

        remote<com.github.burrunan.s3cache.AwsS3BuildCache> {
            region = regionEnv.get()
            bucket = bucketEnv.get()
            prefix = prefixEnv.get()
            isPush = true
            lookupDefaultAwsCredentials = true
        }
    }
}
