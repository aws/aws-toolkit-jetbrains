// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr

import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest
import software.amazon.awssdk.services.ecr.model.CreateRepositoryResponse
import software.amazon.awssdk.services.ecr.model.RepositoryAlreadyExistsException
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyRequest
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyResponse
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.MockClientManagerRule

class CreateRepoActionDialogTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val mockClientManagerRule = MockClientManagerRule()

    @Test
    fun `Empty repo fails validation`() {
        val client: EcrClient = mockClientManagerRule.create()
        runInEdtAndWait {
            val dialog = CreateEcrRepoDialog(project = projectRule.project, ecrClient = client)
            dialog.repoName = "  "

            val validationInfo = dialog.validateForTest()
            assertThat(validationInfo).isNotNull()
        }
    }

    @Test
    fun `Create repo succeeds`() {
        val createRepoCaptor = argumentCaptor<CreateRepositoryRequest>()
        val repoName = RuleUtils.randomName()
        val client: EcrClient = mockClientManagerRule.create()

        client.stub {
            on { createRepository(createRepoCaptor.capture()) } doReturn CreateRepositoryResponse.builder().build()
            on { setRepositoryPolicy(any<SetRepositoryPolicyRequest>()) } doReturn SetRepositoryPolicyResponse.builder().build()
        }

        runInEdtAndWait {
            val dialog = CreateEcrRepoDialog(project = projectRule.project, ecrClient = client)

            dialog.repoName = repoName

            dialog.createRepo()
        }

        assertThat(createRepoCaptor.firstValue.repositoryName()).isEqualTo(repoName)
        verify(client, times(0)).setRepositoryPolicy(any<SetRepositoryPolicyRequest>())
    }

    @Test
    fun `Create repo with policy succeeds`() {
        val createRepoCaptor = argumentCaptor<CreateRepositoryRequest>()
        val setPolicyCaptor = argumentCaptor<SetRepositoryPolicyRequest>()
        val repoName = RuleUtils.randomName()
        val repoPolicy = aString()
        val client: EcrClient = mockClientManagerRule.create()

        client.stub {
            on { createRepository(createRepoCaptor.capture()) } doReturn CreateRepositoryResponse.builder().build()
            on { setRepositoryPolicy(setPolicyCaptor.capture()) } doReturn SetRepositoryPolicyResponse.builder().build()
        }

        runInEdtAndWait {
            val dialog = CreateEcrRepoDialog(project = projectRule.project, ecrClient = client)

            dialog.repoName = repoName
            dialog.repoPolicy = repoPolicy

            dialog.createRepo()
        }

        assertThat(createRepoCaptor.firstValue.repositoryName()).isEqualTo(repoName)
        assertThat(setPolicyCaptor.firstValue.repositoryName()).isEqualTo(repoName)
        assertThat(setPolicyCaptor.firstValue.policyText()).isEqualTo(repoPolicy)
    }

    @Test
    fun `Create repo trims and succeeds`() {
        val createRepoCaptor = argumentCaptor<CreateRepositoryRequest>()
        val repoName = "  " + RuleUtils.randomName() + "       "
        val client: EcrClient = mockClientManagerRule.create()

        client.stub {
            on { createRepository(createRepoCaptor.capture()) } doReturn CreateRepositoryResponse.builder().build()
        }

        runInEdtAndWait {
            val dialog = CreateEcrRepoDialog(project = projectRule.project, ecrClient = client)

            dialog.repoName = repoName

            dialog.createRepo()
        }

        assertThat(createRepoCaptor.firstValue.repositoryName()).isEqualTo(repoName.trim())
    }

    @Test
    fun `Create repo fails`() {
        val repoName = RuleUtils.randomName()
        val message = RuleUtils.randomName()
        val client: EcrClient = mockClientManagerRule.create()

        client.stub {
            on { createRepository(any<CreateRepositoryRequest>()) } doThrow RepositoryAlreadyExistsException.builder().message(message).build()
        }
        runInEdtAndWait {
            val dialog = CreateEcrRepoDialog(project = projectRule.project, ecrClient = client)

            dialog.repoName = repoName

            assertThatThrownBy { dialog.createRepo() }.hasMessage(message)
        }
    }
}
