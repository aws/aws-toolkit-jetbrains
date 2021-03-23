// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.sdk.GenerateSdk
import software.aws.toolkits.gradle.sdk.GenerateSdkExtension

val awsSdkVersion: String by project

val sdkSettings = project.extensions.create<GenerateSdkExtension>("sdkGenerator")

plugins {
    java
}

dependencies {
    implementation("software.amazon.awssdk:services:$awsSdkVersion")
    implementation("software.amazon.awssdk:aws-json-protocol:$awsSdkVersion")
    runtimeOnly("software.amazon.awssdk:core:$awsSdkVersion")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf(sdkSettings.srcDir()))
        }
    }

    test {
        java {
            setSrcDirs(listOf(sdkSettings.srcDir()))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val generateTask = tasks.register<GenerateSdk>("generateSdks")
tasks.named("compileJava") {
    dependsOn(generateTask)
}
