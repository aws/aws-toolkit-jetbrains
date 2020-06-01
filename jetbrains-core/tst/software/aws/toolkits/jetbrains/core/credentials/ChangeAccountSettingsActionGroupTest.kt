// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.region.anAwsRegion
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider

class ChangeAccountSettingsActionGroupTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun `Can display both region and credentials selection`() {
        val group = ChangeAccountSettingsActionGroup(projectRule.project, ChangeAccountSettingsMode.BOTH)
        val actions = group.getChildren(null)

        assertThat(actions).hasAtLeastOneElementOfType(ChangeRegionAction::class.java)
        assertThat(actions).hasAtLeastOneElementOfType(ChangeCredentialsAction::class.java)
    }

    @Test
    fun `Can display only region selection`() {
        val group = ChangeAccountSettingsActionGroup(projectRule.project, ChangeAccountSettingsMode.REGIONS)
        val actions = group.getChildren(null)

        assertThat(actions).hasAtLeastOneElementOfType(ChangeRegionAction::class.java)
        assertThat(actions).doesNotHaveAnyElementsOfTypes(ChangeCredentialsAction::class.java)
    }

    @Test
    fun `Can display only credentials selection`() {
        val group = ChangeAccountSettingsActionGroup(projectRule.project, ChangeAccountSettingsMode.CREDENTIALS)
        val actions = group.getChildren(null)

        assertThat(actions).doesNotHaveAnyElementsOfTypes(ChangeRegionAction::class.java)
        assertThat(actions).hasAtLeastOneElementOfType(ChangeCredentialsAction::class.java)
    }

    @Test
    fun `Region group shows sub-regions for non-selected partitions`() {
        val mockRegionProvider = MockRegionProvider.getInstance()

        val selectedRegion = anAwsRegion(partitionId = "selected").also { mockRegionProvider.addRegion(it) }
        val otherPartitionRegion = anAwsRegion(partitionId = "nonSelected").also { mockRegionProvider.addRegion(it) }
        val anotherRegionInSamePartition = anAwsRegion(partitionId = otherPartitionRegion.partitionId).also { mockRegionProvider.addRegion(it) }

        val mockManager = MockProjectAccountSettingsManager.getInstance(projectRule.project)
        mockManager.changeRegionAndWait(selectedRegion)

        val group = ChangeAccountSettingsActionGroup(projectRule.project, ChangeAccountSettingsMode.REGIONS)
        val partitionActions = group.getChildren(null)
            .filterIsInstance<ChangeRegionActionGroup>().first().getChildren(null)
            .filterIsInstance<ChangePartitionActionGroup>().first().getChildren(null)

        val selectedAction = partitionActions.firstOrNull { it.templateText == selectedRegion.partitionId }
        assertThat(selectedAction).isNull()

        val nonSelectedAction = partitionActions.filterIsInstance<ChangeRegionActionGroup>().first { it.templateText == otherPartitionRegion.partitionId }
            .getChildren(null).filterIsInstance<ChangeRegionAction>()

        assertThat(nonSelectedAction).hasSize(2)
        assertThat(nonSelectedAction.map { it.templateText }).containsExactlyInAnyOrder(
            otherPartitionRegion.displayName,
            anotherRegionInSamePartition.displayName
        )
    }
}
