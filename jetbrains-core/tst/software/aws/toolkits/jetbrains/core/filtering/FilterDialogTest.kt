// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import java.util.concurrent.CompletableFuture

class FilterDialogTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockResourceCache = MockResourceCacheRule(projectRule)

    @Before
    fun clear() {
        filterManager().state.clear()
    }

    @Test
    fun `Replaces resource on rename`() = runInEdtAndWait {
        val filter = TagFilter(enabled = false)
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.Tag)
        filterManager().state["Name"] = TagFilter()
        dialog.load("Name", filter)
        // Edit what is loaded behind the back of the filter dialog
        dialog.content.load("Name2", filter)
        dialog.save()
        assertThat(filterManager().state).hasSize(1)
        assertThat(filterManager().state["Name2"]).isEqualTo(filter)
    }

    @Test
    fun `Saves tag state properly on OK`() = runInEdtAndWait {
        val filter = TagFilter(enabled = false)
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.Tag)
        dialog.load("Name", filter)
        dialog.save()
        assertThat(filterManager().state).hasSize(1)
        assertThat(filterManager().state["Name"]).isEqualTo(filter)
    }

    @Test
    fun `Saves stack state properly on OK`() = runInEdtAndWait {
        val filter = StackFilter(enabled = false)
        val filterManager = ResourceFilterManager.getInstance(projectRule.project)
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.CloudFormation)
        dialog.load("Name", filter)
        dialog.save()
        assertThat(filterManager.state).hasSize(1)
        assertThat(filterManager.state["Name"]).isEqualTo(filter)
    }

    @Test
    fun `Cloudformation filter loading wrong type of state fails`() = runInEdtAndWait {
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.CloudFormation)
        assertThatThrownBy { dialog.content.load("name", TagFilter()) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `Tag filter loading the wrong type of state fails`() = runInEdtAndWait {
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.Tag)
        assertThatThrownBy { dialog.content.load("name", StackFilter()) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `Validate correct CloudFormation filter works`() = runInEdtAndWait {
        fillResourceCacheWithCloudformationArns("arn")
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.CloudFormation)
        dialog.content.load("name", StackFilter(stackId = "arn"))
        assertThat(dialog.content.validate()).isNull()
    }

    @Test
    fun `Validates CloudFormation filter with no name fails`() = runInEdtAndWait {
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.CloudFormation)
        dialog.content.load(" ", StackFilter())
        assertThat(dialog.content.validate()).isInstanceOf(ValidationInfo::class.java)
    }

    @Test
    fun `Validates CloudFormation filter with no stack fails`() = runInEdtAndWait {
        fillResourceCacheWithCloudformationArns("wrong arn")
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.CloudFormation)
        dialog.content.load("name", StackFilter())
        assertThat(dialog.content.validate()).isInstanceOf(ValidationInfo::class.java)
    }

    @Test
    fun `Validate correct tag filter works`() = runInEdtAndWait {
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.Tag)
        dialog.content.load("name", TagFilter(tagKey = "Key"))
        assertThat(dialog.content.validate()).isNull()
    }

    @Test
    fun `Validates tag filter with no name fails`() = runInEdtAndWait {
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.Tag)
        dialog.content.load(" ", TagFilter(tagKey = "Key"))
        assertThat(dialog.content.validate()).isInstanceOf(ValidationInfo::class.java)
    }

    @Test
    fun `Validates tag filter with no tag key fails`() = runInEdtAndWait {
        val dialog = FilterDialog(projectRule.project, FilterDialog.FilterType.Tag)
        dialog.content.load("name", TagFilter(tagKey = " "))
        assertThat(dialog.content.validate()).isInstanceOf(ValidationInfo::class.java)
    }

    private fun filterManager() = ResourceFilterManager.getInstance(projectRule.project)
    private fun fillResourceCacheWithCloudformationArns(arn: String) {
        mockResourceCache.get().addEntry(
            CloudFormationResources.LIST_STACKS,
            CompletableFuture.completedFuture(
                listOf(
                    StackSummary.builder().stackId(arn).stackName(arn).stackStatus(StackStatus.CREATE_COMPLETE).build()
                )
            )
        )
    }
}
