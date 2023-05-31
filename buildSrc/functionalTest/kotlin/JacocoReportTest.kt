// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation
import org.gradle.tooling.internal.consumer.DefaultModelBuilder
import org.gradle.tooling.model.GradleProject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class JacocoReportTest {
    @Test
    fun `test the jacoco`(@TempDir projectDir: Path) {
        projectDir.resolve("build.gradle.kts").writeText("""
            import java.lang.management.ManagementFactory
            import java.lang.ProcessHandle

            plugins {
                id("toolkit-jacoco-report")
            }
            
            println("wow")
        """.trimIndent())

//        val runner = GradleRunner.create()
//            .withPluginClasspath()
//            .withProjectDir(projectDir.toFile())
//            .withDebug(true)
//            .withArguments("ddependencies", "--debug")
//            .build()
//
//        println(runner)
        val model = GradleConnector.newConnector()
            .forProjectDirectory(projectDir.toFile())
            .connect().use {
                (it.model(GradleProject::class.java) as DefaultModelBuilder<GradleProject>).withInjectedClassPath(
                DefaultClassPath.of(
                    PluginUnderTestMetadataReading.readImplementationClasspath()
                )).get()
            }


        println(model.getTasks())
    }
}
