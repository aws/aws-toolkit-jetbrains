// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import com.intellij.execution.Platform
import com.intellij.execution.configurations.GeneralCommandLine
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import software.aws.toolkits.jetbrains.services.lambda.deploy.CreateCapabilities

@RunWith(Parameterized::class)
class SamExecutableTest(private val platform: Platform) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun parameters(): Collection<Array<*>> = Platform.values().map { arrayOf(it) }
    }

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

        assertThat(cmd.getPreparedCommandLine(platform).lines()).containsExactly(
            "sam",
            "build",
            "--template",
            "$templatePath",
            "--build-dir",
            "$buildDir"
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

        assertThat(cmd.getPreparedCommandLine(platform).lines()).containsExactly(
            "sam",
            "build",
            "--template",
            "$templatePath",
            "--build-dir",
            "$buildDir",
            "--use-container"
        )
    }

    @Test
    fun `sam package command is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val packagedTemplatePath = tempFolder.newFile("packagedTemplate.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samPackageCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            packagedTemplatePath = packagedTemplatePath,
            s3Bucket = "myBucket"
        )

        assertThat(cmd.getPreparedCommandLine(platform).lines()).containsExactly(
            "sam",
            "package",
            "--template-file",
            "$templatePath",
            "--output-template-file",
            "$packagedTemplatePath",
            "--s3-bucket",
            "myBucket"
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

        assertThat(cmd.getPreparedCommandLine(platform).lines()).containsExactly(
            "sam",
            "deploy",
            "--template-file",
            "$templatePath",
            "--stack-name",
            "MyStack",
            "--s3-bucket",
            "myBucket",
            "--capabilities",
            "CAPABILITY_IAM",
            "CAPABILITY_NAMED_IAM",
            "--no-execute-changeset"
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
                "Hello1" to "World",
                "Hello2" to "Wor ld",
                "Hello3" to "\"Wor ld\"",
                "Hello4" to "It's",
                "Hello5" to "2+2=22"
            ),
            capabilities = emptyList(),
            s3Bucket = "myBucket"
        )

        if(platform == Platform.WINDOWS) {
            assertThat(cmd.getPreparedCommandLine(platform).lines()).containsExactly(
                "sam",
                "deploy",
                "--template-file",
                "$templatePath",
                "--stack-name",
                "MyStack",
                "--s3-bucket",
                "myBucket",
                "--no-execute-changeset",
                "--parameter-overrides",
                """ "\"Hello1\"=\"World\"" """.trim(),
                """ "\"Hello2\"=\"Wor ld\"" """.trim(),
                """ "\"Hello3\"='\"Wor ld\"'" """.trim(),
                """ "\"Hello4\"=\"It's\"" """.trim(),
                """ "\"Hello5\"=\"2+2=22\"" """.trim()
            )
        } else {
            --parameter-overrides "\"Helo\"=\"Wor ld\"" \"Hello\"=\"World\" \"me\"=\"It's\" "\"Hell\"='\"Wor ld\"'"

            assertThat(cmd.getPreparedCommandLine(platform).lines()).containsExactly(
                "sam",
                "deploy",
                "--template-file",
                "$templatePath",
                "--stack-name",
                "MyStack",
                "--s3-bucket",
                "myBucket",
                "--no-execute-changeset",
                "--parameter-overrides",
                """ "Hello1"="World"" """.trim(),
                """ "Hello2"="Wor ld"" """.trim(),
                """ "Hello3"='"Wor ld"'" """.trim(),
                """ "Hello4"="It's"" """.trim(),
                """ "Hello5"="2+2=22"" """.trim()
            )
        }

    }
}
