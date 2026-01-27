// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider

class CloudFormationRegionManagerTest {

    @JvmField
    @Rule
    val applicationRule = ApplicationRule()

    private lateinit var regionManager: CloudFormationRegionManager

    @Before
    fun setUp() {
        regionManager = CloudFormationRegionManager()
        regionManager.loadState(CloudFormationRegionManager.State())
    }

    @Test
    fun `getSelectedRegion returns default region when none set`() {
        val region = regionManager.getSelectedRegion()

        assertThat(region.id).isEqualTo("us-east-1")
    }

    @Test
    fun `setSelectedRegion updates state`() {
        val regionProvider = AwsRegionProvider.getInstance()
        val euWest1 = regionProvider.allRegions()["eu-west-1"] ?: return

        regionManager.setSelectedRegion(euWest1)

        assertThat(regionManager.state.selectedRegionId).isEqualTo("eu-west-1")
    }

    @Test
    fun `clearSelectedRegion resets state to null`() {
        regionManager.loadState(CloudFormationRegionManager.State(selectedRegionId = "eu-west-1"))

        regionManager.clearSelectedRegion()

        assertThat(regionManager.state.selectedRegionId).isNull()
    }

    @Test
    fun `getSelectedRegion falls back to default for invalid region id`() {
        regionManager.loadState(CloudFormationRegionManager.State(selectedRegionId = "invalid-region"))

        val region = regionManager.getSelectedRegion()

        assertThat(region.id).isEqualTo("us-east-1")
    }

    @Test
    fun `state roundtrip preserves region id`() {
        val originalState = CloudFormationRegionManager.State(selectedRegionId = "ap-northeast-1")

        regionManager.loadState(originalState)
        val retrievedState = regionManager.state

        assertThat(retrievedState.selectedRegionId).isEqualTo("ap-northeast-1")
    }
}
