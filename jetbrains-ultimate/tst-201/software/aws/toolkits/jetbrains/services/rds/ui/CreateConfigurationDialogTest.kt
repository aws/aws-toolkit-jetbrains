// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.ui

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.iam.model.User
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCache
import software.aws.toolkits.jetbrains.services.iam.IamResources
import software.aws.toolkits.jetbrains.services.iam.IamUser

class CreateConfigurationDialogTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()
    private fun resourceCache() = MockResourceCache.getInstance(projectRule.project)

    @Before
    fun setUp() {
        resourceCache().clear()
    }

    @Test
    fun `Combo options IAM Users`() {
        val username = RuleUtils.randomName()
        resourceCache().addEntry(
            IamResources.LIST_ALL_USERS,
            listOf(IamUser(User.builder().userId(RuleUtils.randomName()).userName(username).build()))
        )
        val user = ComboOption.User(projectRule.project)
        assertThat(user.label).isNotEmpty()
        assertThat(user.getUsername()).isEqualTo(username)
    }
}
