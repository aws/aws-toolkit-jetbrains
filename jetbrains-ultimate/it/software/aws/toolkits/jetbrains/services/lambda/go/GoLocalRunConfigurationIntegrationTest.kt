// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.services.lambda.execution.local.createHandlerBasedRunConfiguration
import software.aws.toolkits.jetbrains.utils.WebStormTestUtils
import software.aws.toolkits.jetbrains.utils.executeRunConfiguration
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addPackageJsonFile
import software.aws.toolkits.jetbrains.utils.setSamExecutableFromEnvironment

@RunWith(Parameterized::class)
class GoLocalRunConfigurationIntegrationTest(private val runtime: Runtime) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): Collection<Array<Runtime>> = listOf(
            arrayOf(Runtime.GO1_X)
        )
    }

    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val credentialManager = MockCredentialManagerRule()

    private val input = RuleUtils.randomName()
    private val mockId = "MockCredsId"
    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    private val goMod = """
    require github.com/aws/aws-lambda-go v1.13.3

    module hello-world

    go 1.14
    """.trimIndent()

    private val fileContents = """
    package main

    import (
	    "github.com/aws/aws-lambda-go/lambda"
	    "strings"
    )

    func handler(request string) (string, error) {
	    return strings.ToUpper(request), nil
    }

    func main() {
	    lambda.Start(handler)
    }
    """.trimIndent()

    @Before
    fun setUp() {
        setSamExecutableFromEnvironment()

        val fixture = projectRule.fixture

        fixture.addFileToProject("hello-world/go.mod", goMod)
        val psiFile = fixture.addFileToProject("hello-world/main.go", fileContents)

        runInEdtAndWait {
            fixture.openFileInEditor(psiFile.virtualFile)
        }

        credentialManager.addCredentials(mockId, mockCreds)
        WebStormTestUtils.ensureBuiltInServerStarted()
    }

    @Test
    fun samIsExecuted() {
        projectRule.fixture.addPackageJsonFile()

        val runConfiguration = createHandlerBasedRunConfiguration(
            project = projectRule.project,
            runtime = runtime,
            handler = "handler",
            input = "\"${input}\"",
            credentialsProviderId = mockId
        )

        assertThat(runConfiguration).isNotNull

        val executeLambda = executeRunConfiguration(runConfiguration)

        assertThat(executeLambda.exitCode).isEqualTo(0)
        assertThat(executeLambda.stdout).contains(input.toUpperCase())
    }
}
