// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import com.intellij.execution.configurations.GeneralCommandLine
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.core.lambda.LambdaRuntime
import software.aws.toolkits.jetbrains.services.lambda.deploy.CreateCapabilities
import software.aws.toolkits.jetbrains.services.lambda.wizard.AppBasedImageTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.AppBasedZipTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.LocationBasedTemplate

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
            samOptions = SamOptions(
                buildInContainer = false
            )
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "build",
                "--template",
                "$templatePath",
                "--build-dir",
                "$buildDir"
            ).joinToString(separator = " ")
        )

        assertThat(cmd.workDirectory).isEqualTo(tempFolder.root)

        assertThat(cmd.environment).containsEntry("Foo", "Bar")
    }

    @Test
    fun `sam build can take a logical ID`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            logicalId = "FooResource",
            buildDir = buildDir,
            samOptions = SamOptions(
                buildInContainer = false
            )
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "build",
                "FooResource",
                "--template",
                "$templatePath",
                "--build-dir",
                "$buildDir"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam build can skip image pull`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            logicalId = "FooResource",
            buildDir = buildDir,
            samOptions = SamOptions(
                skipImagePull = true
            )
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "build",
                "FooResource",
                "--template",
                "$templatePath",
                "--build-dir",
                "$buildDir",
                "--skip-pull-image"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam build can take a docker network`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            buildDir = buildDir,
            samOptions = SamOptions(
                dockerNetwork = "FooNetwork"
            )
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "build",
                "--template",
                "$templatePath",
                "--build-dir",
                "$buildDir",
                "--docker-network",
                "FooNetwork"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam build can append custom provided args`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            buildDir = buildDir,
            samOptions = SamOptions(
                additionalBuildArgs = "--foo bar"
            )
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "build",
                "--template",
                "$templatePath",
                "--build-dir",
                "$buildDir",
                "--foo",
                "bar"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam build command is correct with container`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val buildDir = tempFolder.newFolder("build").toPath()
        val cmd = GeneralCommandLine("sam").samBuildCommand(
            environmentVariables = emptyMap(),
            templatePath = templatePath,
            buildDir = buildDir,
            samOptions = SamOptions(
                buildInContainer = true
            )
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "build",
                "--template",
                "$templatePath",
                "--build-dir",
                "$buildDir",
                "--use-container"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam package command without repo is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val packagedTemplatePath = tempFolder.newFile("packagedTemplate.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samPackageCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            packagedTemplatePath = packagedTemplatePath,
            s3Bucket = "myBucket",
            ecrRepo = null
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "package",
                "--template-file",
                "$templatePath",
                "--output-template-file",
                "$packagedTemplatePath",
                "--s3-bucket",
                "myBucket"
            ).joinToString(separator = " ")
        )

        assertThat(cmd.workDirectory).isEqualTo(tempFolder.root)

        assertThat(cmd.environment).containsEntry("Foo", "Bar")
    }

    @Test
    fun `sam package command without bucket is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val packagedTemplatePath = tempFolder.newFile("packagedTemplate.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samPackageCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            packagedTemplatePath = packagedTemplatePath,
            s3Bucket = null,
            ecrRepo = "myRepo"
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "package",
                "--template-file",
                "$templatePath",
                "--output-template-file",
                "$packagedTemplatePath",
                "--image-repository",
                "myRepo"
            ).joinToString(separator = " ")
        )

        assertThat(cmd.workDirectory).isEqualTo(tempFolder.root)

        assertThat(cmd.environment).containsEntry("Foo", "Bar")
    }

    @Test
    fun `sam package command is correct`() {
        val templatePath = tempFolder.newFile("template.yaml").toPath()
        val packagedTemplatePath = tempFolder.newFile("packagedTemplate.yaml").toPath()
        val cmd = GeneralCommandLine("sam").samPackageCommand(
            environmentVariables = mapOf("Foo" to "Bar"),
            templatePath = templatePath,
            packagedTemplatePath = packagedTemplatePath,
            s3Bucket = "myBucket",
            ecrRepo = "myRepo"
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "package",
                "--template-file",
                "$templatePath",
                "--output-template-file",
                "$packagedTemplatePath",
                "--s3-bucket",
                "myBucket",
                "--image-repository",
                "myRepo"
            ).joinToString(separator = " ")
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

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
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
            ).joinToString(separator = " ")
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

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
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
                """ \"Hello1\"=\"World\" """.trim(),
                """ "\"Hello2\"=\"Wor ld\"" """.trim(),
                """ "\"Hello3\"='\"Wor ld\"'" """.trim(),
                """ \"Hello4\"=\"It's\" """.trim(),
                """ \"Hello5\"=\"2+2=22\" """.trim()
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam init with zip app template is correct`() {
        val outputDir = tempFolder.newFolder()
        val cmd = GeneralCommandLine("sam").samInitCommand(
            outputDir = outputDir.toPath(),
            parameters = AppBasedZipTemplate(
                name = "Hello",
                runtime = LambdaRuntime.JAVA11,
                appTemplate = "HelloWorldTemplate",
                dependencyManager = "maven"
            ),
            extraContext = emptyMap()
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "init",
                "--no-input",
                "--output-dir",
                "$outputDir",
                "--name",
                "Hello",
                "--runtime",
                "java11",
                "--dependency-manager",
                "maven",
                "--app-template",
                "HelloWorldTemplate"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam init with image app template is correct`() {
        val outputDir = tempFolder.newFolder()
        val cmd = GeneralCommandLine("sam").samInitCommand(
            outputDir = outputDir.toPath(),
            parameters = AppBasedImageTemplate(
                name = "Hello",
                baseImage = "amazon/runtime-base",
                dependencyManager = "maven"
            ),
            extraContext = emptyMap()
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "init",
                "--no-input",
                "--output-dir",
                "$outputDir",
                "--package-type",
                "Image",
                "--name",
                "Hello",
                "--base-image",
                "amazon/runtime-base",
                "--dependency-manager",
                "maven"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam init with location template is correct`() {
        val outputDir = tempFolder.newFolder()
        val cmd = GeneralCommandLine("sam").samInitCommand(
            outputDir = outputDir.toPath(),
            parameters = LocationBasedTemplate(
                location = "SomeUrl"
            ),
            extraContext = emptyMap()
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "init",
                "--no-input",
                "--output-dir",
                "$outputDir",
                "--location",
                "SomeUrl"
            ).joinToString(separator = " ")
        )
    }

    @Test
    fun `sam init with extra context is correct`() {
        val outputDir = tempFolder.newFolder()
        val cmd = GeneralCommandLine("sam").samInitCommand(
            outputDir = outputDir.toPath(),
            parameters = AppBasedZipTemplate(
                name = "Hello",
                runtime = LambdaRuntime.JAVA11,
                appTemplate = "HelloWorldTemplate",
                dependencyManager = "maven"
            ),
            extraContext = mapOf("Foo" to "Bar")
        )

        assertThat(cmd.commandLineString).isEqualTo(
            listOf(
                "sam",
                "init",
                "--no-input",
                "--output-dir",
                "$outputDir",
                "--name",
                "Hello",
                "--runtime",
                "java11",
                "--dependency-manager",
                "maven",
                "--app-template",
                "HelloWorldTemplate",
                "--extra-context",
                """{\"Foo\":\"Bar\"}"""
            ).joinToString(separator = " ")
        )
    }
}
