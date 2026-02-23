import kotlin.io.extension

// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    base
    id("jacoco-report-aggregation")
}

dependencies {
    jacocoAggregation(project(":plugin-toolkit:intellij-standalone"))
    jacocoAggregation(project(":plugin-core"))
    jacocoAggregation(project(":plugin-amazonq"))

    project.findProject(":plugin-toolkit:jetbrains-gateway")?.let {
        jacocoAggregation(it)
    }

    jacocoAggregation(project(":ui-tests"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testType = TestSuiteType.UNIT_TEST
            // https://github.com/gradle/gradle/issues/20026
            reportTask.configure {
                classDirectories.setFrom(classDirectories.map {
                    fileTree(it).matching {
                        include("**/software/aws/toolkits/**")
                        exclude("**/software/aws/toolkits/telemetry/**")
                    }
                })
            }
        }
    }
}
