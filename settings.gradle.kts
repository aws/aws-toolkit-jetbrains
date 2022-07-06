// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
enableFeaturePreview("VERSION_CATALOGS")

val codeArtifactUrl: Provider<String> = providers.environmentVariable("CODEARTIFACT_URL")
val codeArtifactToken: Provider<String> = providers.environmentVariable("CODEARTIFACT_AUTH_TOKEN")

dependencyResolutionManagement {
    // only allow repositories to be declared in settings.gradle.kts
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "aws-toolkit-jetbrains"

include("resources")
include("sdk-codegen")
include("core")
include("jetbrains-core")
include("jetbrains-ultimate")
include("jetbrains-rider")
include("intellij")
include("ui-tests")
include("detekt-rules")

plugins {
    id("com.gradle.enterprise").version("3.4.1")
    id("com.github.burrunan.s3-build-cache").version("1.2")
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

val regionEnv: Provider<String> = providers.environmentVariable("AWS_REGION").forUseAtConfigurationTime()
val bucketEnv: Provider<String> = providers.environmentVariable("S3_BUILD_CACHE_BUCKET").forUseAtConfigurationTime()
val prefixEnv: Provider<String> = providers.environmentVariable("S3_BUILD_CACHE_PREFIX").forUseAtConfigurationTime()
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
