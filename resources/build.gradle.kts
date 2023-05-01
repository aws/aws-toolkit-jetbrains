// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import de.undercouch.gradle.tasks.download.Download
import software.aws.toolkits.gradle.resources.ValidateMessages

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("de.undercouch.download") version "5.2.1"
}

sourceSets {
    main {
        resources.srcDir("$buildDir/downloaded-resources")
        java.srcDir("${project.buildDir}/generated-src")
    }
}

dependencies {
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit5.jupiterVintage)
}

tasks.test {
    useJUnitPlatform()
}

val download = tasks.register<Download>("downloadResources") {
    dest("$buildDir/downloaded-resources/software/aws/toolkits/resources/")
    src(listOf("https://idetoolkits.amazonwebservices.com/endpoints.json"))
    onlyIfModified(true)
    useETag(true)
    doFirst {
        mkdir("$buildDir/downloaded-resources/software/aws/toolkits/resources/")
    }
}

val downloadCfnSpec = tasks.register<Download>("downloadCfnSpec") {
    dest(buildDir)
    onlyIfModified(true)
    useETag(true)
    src(listOf("https://d201a2mn26r7lk.cloudfront.net/latest/gzip/CloudFormationResourceSpecification.json"))
}

val generateCfnResourceTypes = tasks.register("generateCfnResourceTypes") {
    val inputFile = file("${project.buildDir}/CloudFormationResourceSpecification.json")
    val outputFile = file("${project.buildDir}/generated-src/software/aws/toolkits/resources/cloudformation/CloudFormationResourceTypes.kt")
    inputs.file(inputFile)
    outputs.file(outputFile)
    doLast {
        data class CfnResourceSpec(@JsonProperty("ResourceTypes") val resourceTypes: Map<String, Any>)
        val cfnSpec = ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(inputFile, CfnResourceSpec::class.java)

        val grouped = cfnSpec.resourceTypes.keys.groupBy { it.split("::")[0] }.mapValues { v -> v.value.groupBy { it.split("::")[1] } }

        outputFile.bufferedWriter().use {
            it.appendLine("// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.")
            it.appendLine("// SPDX-License-Identifier: Apache-2.0")
            it.appendLine()
            it.appendLine("@file:Suppress(\"MaximumLineLength\")")
            it.appendLine("package software.aws.toolkits.resources.cloudformation")
            it.appendLine()
            grouped.forEach { (domain, services) ->
                it.appendLine("object $domain {")
                services.toList().sortedBy { (key, _) -> key }.forEach { (service, resourceTypes) ->
                    it.appendLine("    object $service {")
                    resourceTypes.sorted().forEach { resourceType ->
                        val type = resourceType.split("::")[2]
                        it.appendLine("        val $type = CloudFormationResourceType(\"$resourceType\")")
                    }
                    it.appendLine("    }")
                }
                it.appendLine("}")
            }
        }
    }
    dependsOn(downloadCfnSpec)
}

tasks.processResources {
    dependsOn(download)
}

tasks.compileKotlin {
    dependsOn(generateCfnResourceTypes)
}

val validateLocalizedMessages = tasks.register<ValidateMessages>("validateLocalizedMessages") {
    paths.from("resources/software/aws/toolkits/resources/MessagesBundle.properties")
}

tasks.check {
    dependsOn(validateLocalizedMessages)
}
