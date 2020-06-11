// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.ui

import com.intellij.testFramework.ProjectRule
import com.intellij.ui.MutableCollectionComboBoxModel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.iam.model.User
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCache
import software.aws.toolkits.jetbrains.services.iam.IamResources
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.iam.IamUser
import software.aws.toolkits.jetbrains.services.sts.StsResources
import software.aws.toolkits.jetbrains.utils.waitForTrue

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
        val user = ComboOption.User(projectRule.project)
        val iamUser = IamUser(User.builder().userId(RuleUtils.randomName()).userName(username).build())
        user.component.forceLoaded()
        user.component.model = MutableCollectionComboBoxModel(listOf(iamUser))
        user.component.selectedItem = iamUser
        assertThat(user.label).isNotEmpty()
        assertThat(user.getUsername()).isEqualTo(username)
    }

    @Test
    fun `Combo options IAM Users non selected is null`() {
        val user = ComboOption.User(projectRule.project)
        assertThat(user.getUsername()).isNull()
    }

    @Test
    fun `Combo options IAM Roles`() {
        val role = RuleUtils.randomName()
        val user = ComboOption.Role(projectRule.project)
        val iamRole = IamRole("arn:aws:iam::123456789012:role/$role")
        user.component.forceLoaded()
        user.component.model = MutableCollectionComboBoxModel(listOf(iamRole))
        user.component.selectedItem = iamRole
        assertThat(user.label).isNotEmpty()
        assertThat(user.getUsername()).isEqualTo(role)
    }

    @Test
    fun `Combo options IAM Roles non selected is null`() {
        val user = ComboOption.Role(projectRule.project)
        assertThat(user.getUsername()).isNull()
    }

    @Test
    fun `Combo options current user`() {
        val username = RuleUtils.randomName()
        resourceCache().addEntry(
            StsResources.USER,
            username
        )
        val user = ComboOption.CurrentUser(projectRule.project)
        runBlocking { waitForTrue { (user.component as? CacheBackedJBTextField)?.valid == true } }
        assertThat(user.label).isNotEmpty()
        assertThat(user.getUsername()).isEqualTo(username)
    }

    @Test
    fun `Combo options current user is null`() {
        val user = ComboOption.CurrentUser(projectRule.project)
        assertThat(user.getUsername()).isNull()
    }

    @Test
    fun `Combo options custom`() {
        val username = RuleUtils.randomName()
        val user = ComboOption.Custom()
        user.component.text = username
        assertThat(user.label).isNotEmpty()
        assertThat(user.getUsername()).isEqualTo(username)
    }

    @Test
    fun `Combo options custom is empty`() {
        val user = ComboOption.Custom()
        assertThat(user.getUsername()).isNull()
    }
}
