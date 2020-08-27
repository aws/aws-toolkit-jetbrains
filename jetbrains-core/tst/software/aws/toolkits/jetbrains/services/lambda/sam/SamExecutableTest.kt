// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import com.intellij.execution.configurations.GeneralCommandLine
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.jetbrains.services.lambda.deploy.CreateCapabilities

class SamExecutableTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `sam build command is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            buildDir = buildDir,
            useContainer = false
        )

        assertThat(cmd.commandLineString).isEqualToIgnoringNewLines(
            """
                sam 
                build 
                --template $templatePath
                --build-dir $buildDir
            """.trimIndent()
        )

        assertThat(cmd.workDirectory).isEqualTo(tempFolder.root)

        assertThat(cmd.environment).containsEntry("Foo", "Bar")
    }

    @Test
    fun `sam build command is correct with container`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = emptyMap(),
            templatePath = templatePath,
            buildDir = buildDir,
            useContainer = true
        )

        assertThat(cmd.commandLineString).isEqualToIgnoringNewLines(
            """
                sam 
                build 
                --template $templatePath
                --build-dir $buildDir
                --use-container
            """.trimIndent()
        )
    }

    @Test
    fun `sam package command is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val packagedTemplatePath = tempFolder.newFile("packagedTemplate.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samPackageCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = tempFolder.newFile("template.yaml").toPath(),
            packagedTemplatePath = tempFolder.newFolder("packagedTemplate.yaml").toPath(),
            s3Bucket = "myBucket"
        )

        assertThat(cmd.commandLineString).isEqualToIgnoringNewLines(
            """
                sam 
                package 
                --template-file $templatePath
                --output-template-file $packagedTemplatePath
                --s3-bucket myBucket
            """.trimIndent()
        )

        assertThat(cmd.workDirectory).isEqualTo(tempFolder.root)

        assertThat(cmd.environment).containsEntry("Foo", "Bar")
    }

    @Test
    fun `sam deploy command is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samDeployCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            stackName = "MyStack",
            parameters = emptyMap(),
            capabilities = listOf(CreateCapabilities.IAM, CreateCapabilities.NAMED_IAM),
            s3Bucket = "myBucket"
        )

        assertThat(cmd.commandLineString).isEqualToIgnoringNewLines(
            """
                sam 
                deploy 
                --template-file $templatePath
                --stack-name MyStack 
                --s3-bucket myBucket 
                --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM 
                --no-execute-changeset
            """.trimIndent()
        )

        assertThat(cmd.workDirectory).isEqualTo(tempFolder.root)

        assertThat(cmd.environment).containsEntry("Foo", "Bar")
    }

    @Test
    fun `sam deploy parameters are correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samDeployCommand(
            environmentVariables = emptyMap(),
            templatePath = templatePath,
            stackName = "MyStack",
            parameters = mapOf(
                "Hello" to "World",
                "Hel lo" to "Wor ld"
            ),
            capabilities = emptyList(),
            s3Bucket = "myBucket"
        )

        assertThat(cmd.commandLineString).isEqualToIgnoringNewLines(
            """
                sam 
                deploy 
                --template-file $templatePath
                --stack-name MyStack 
                --s3-bucket myBucket 
                --no-execute-changeset 
                --parameter-overrides \"Hello\"=\"World\" "\"Hel lo\"=\"Wor ld\""
            """.trimIndent()
        )
    }
}
