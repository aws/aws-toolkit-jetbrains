// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunAll.runAll
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.Role
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException
import software.amazon.awssdk.services.lambda.model.Runtime
import software.amazon.awssdk.services.s3.model.Bucket
import software.aws.toolkits.core.rules.S3TemporaryBucketRule
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.core.utils.createIntegrationTestCredentialProvider
import software.aws.toolkits.jetbrains.core.MockClientManager
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.MockAwsConnectionManager.ProjectAccountSettingsManagerRule
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.services.ecr.resources.EcrResources
import software.aws.toolkits.jetbrains.services.ecr.resources.Repository
import software.aws.toolkits.jetbrains.services.iam.Iam.createRoleWithPolicy
import software.aws.toolkits.jetbrains.services.iam.IamResources
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.s3.resources.S3Resources
import software.aws.toolkits.jetbrains.utils.assumeImageSupport
import software.aws.toolkits.jetbrains.utils.execution.steps.StepExecutor
import software.aws.toolkits.jetbrains.utils.readProject
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addModule
import software.aws.toolkits.jetbrains.utils.setSamExecutableFromEnvironment
import software.aws.toolkits.jetbrains.utils.setUpGradleProject
import software.aws.toolkits.jetbrains.utils.waitToLoad
import java.time.Duration

@Ignore
class CreateFunctionIntegrationTest {
    private val projectRule = HeavyJavaCodeInsightTestFixtureRule()
    private val resourceCache = MockResourceCacheRule()
    private val disposableRule = DisposableRule()
    private val regionProvider = MockRegionProviderRule()
    private val credentialManager = MockCredentialManagerRule()
    private val settingsManager = ProjectAccountSettingsManagerRule(projectRule)
    private val temporaryBucket = S3TemporaryBucketRule { projectRule.project.awsClient() }

    @Rule
    @JvmField
    // we need to control the life cycle of what gets started and shut down in a very specific way.
    // i.e. disposable needs to be near front of chain so it is first and last so that the ability to make real AWS calls lives for the life of full test
    val ruleChain = RuleChain(
        projectRule,
        disposableRule,
        credentialManager,
        regionProvider,
        settingsManager,
        temporaryBucket
    )

    private lateinit var lambdaClient: LambdaClient
    private lateinit var iamClient: IamClient
    private lateinit var ecrClient: EcrClient

    private lateinit var lambdaName: String
    private lateinit var iamRole: IamRole

    @Before
    fun setUp() {
        setSamExecutableFromEnvironment()
        projectRule.fixture.addModule("main")

        // TODO: Move this to us-west-2
        val region = regionProvider.addRegion(Region.SA_EAST_1)
        val credentials = credentialManager.addCredentials("ReadCreds", createIntegrationTestCredentialProvider(), region)

        settingsManager.settingsManager.changeRegion(regionProvider.addRegion(Region.SA_EAST_1))
        settingsManager.settingsManager.changeCredentialProviderAndWait(credentials)

        MockClientManager.useRealImplementations(disposableRule.disposable)

        lambdaClient = projectRule.project.awsClient()
        iamClient = projectRule.project.awsClient()
        ecrClient = projectRule.project.awsClient()

        lambdaName = RuleUtils.randomName()
        iamRole = iamClient.createRoleWithPolicy(RuleUtils.randomName(), DEFAULT_LAMBDA_ASSUME_ROLE_POLICY)

        resourceCache.addEntry(
            projectRule.project,
            IamResources.LIST_RAW_ROLES,
            listOf(Role.builder().arn(iamRole.arn).assumeRolePolicyDocument(DEFAULT_LAMBDA_ASSUME_ROLE_POLICY).build())
        )
    }

    @After
    fun tearDown() {
        runAll(
            ThrowableRunnable {
                try {
                    lambdaClient.deleteFunction { it.functionName(lambdaName) }
                } catch (e: Exception) {
                    if (e !is ResourceNotFoundException) {
                        throw e
                    }
                }
            },
            ThrowableRunnable {
                iamClient.deleteRole { it.roleName(iamRole.name) }
            }
        )
    }

    @Test
    fun `zip based lambda can be created`() {
        projectRule.setUpGradleProject()

        val s3Bucket = temporaryBucket.createBucket()
        resourceCache.addEntry(
            projectRule.project,
            S3Resources.LIST_REGIONALIZED_BUCKETS,
            listOf(S3Resources.RegionalizedBucket(Bucket.builder().name(s3Bucket).build(), projectRule.project.activeRegion()))
        )

        executeCreateFunction {
            val dialog = runInEdtAndGet {
                CreateFunctionDialog(projectRule.project, Runtime.JAVA8, "com.example.SomeClass").apply {
                    val view = getViewForTestAssertions()
                    view.name.text = lambdaName
                    view.codeStorage.sourceBucket.selectedItem = s3Bucket
                }
            }

            val view = dialog.getViewForTestAssertions()
            view.codeStorage.sourceBucket.waitToLoad()

            runInEdtAndGet {
                assertThat(view.validatePanel()?.message).isNull() // Validate we set everything up
                dialog.createWorkflow()
            }
        }
    }

    @Test
    fun `image based lambda can be created`() {
        assumeImageSupport()
        val (dockerfile, _) = readProject("samProjects/image/java8/maven", "Dockerfile", projectRule)

        val ecrRepo = ecrClient.createRepository {
            it.repositoryName(RuleUtils.randomName().toLowerCase())
        }.repository()

        val repository = Repository(ecrRepo.repositoryName(), ecrRepo.repositoryArn(), ecrRepo.repositoryUri())

        try {
            resourceCache.addEntry(
                projectRule.project,
                EcrResources.LIST_REPOS,
                listOf(repository)
            )

            executeCreateFunction {
                val dialog = runInEdtAndGet {
                    CreateFunctionDialog(projectRule.project, null, null).apply {
                        val view = getViewForTestAssertions()
                        view.name.text = lambdaName
                        view.configSettings.packageImage.isSelected = true
                        view.configSettings.dockerFile.textField.text = dockerfile.path
                        view.codeStorage.ecrRepo.selectedItem = repository
                    }
                }

                val view = dialog.getViewForTestAssertions()
                view.codeStorage.ecrRepo.waitToLoad()

                runInEdtAndGet {
                    assertThat(view.validatePanel()?.message).isNull() // Validate we set everything up
                    dialog.createWorkflow()
                }
            }
        } finally {
            ecrClient.deleteRepository {
                it.repositoryName(repository.repositoryName)
                it.force(true)
            }
        }
    }

    private fun executeCreateFunction(workflowBuilder: () -> StepExecutor) {
        val workflow = workflowBuilder()

        var passed = false
        workflow.onSuccess = {
            passed = true
        }

        workflow.startExecution().waitFor(Duration.ofMinutes(15).toMillis())

        assertThat(passed).isTrue()
        assertThat(lambdaClient.getFunction { it.functionName(lambdaName) }).isNotNull
    }
}
