// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("toolkit-generate-sdks")
}

sdkGenerator {
    c2jFolder.set(file("$projectDir/codegen-resources"))
    outputDir.set(layout.buildDirectory.dir("generated-sources").get().asFile)
}

dependencies {
    implementation(libs.aws.services)
    implementation(libs.aws.jsonProtocol)
    implementation(libs.aws.queryProtocol)
}
