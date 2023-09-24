// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic.explorer

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.core.explorer.nodes.OtherResourcesRootNode
import software.aws.toolkits.jetbrains.services.dynamic.CloudControlApiResources
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceSupportedTypesRule
import software.aws.toolkits.jetbrains.settings.MockDynamicResourcesSettingsRule
import software.aws.toolkits.jetbrains.utils.isInstanceOf

class OtherResourcesNodeTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val resourceCache = MockResourceCacheRule()

    @Rule
    @JvmField
    val supportedTypesRule = DynamicResourceSupportedTypesRule()

    @Rule
    @JvmField
    val dynamicResourceSettingsRule = MockDynamicResourcesSettingsRule()

    @Test
    fun `add resource node added at the top`() {
        val sut = OtherResourcesNode(projectRule.project, OtherResourcesRootNode())

        resourceCache.addEntry(projectRule.project, CloudControlApiResources.listTypes(), emptyList())

        assertThat(sut.children).hasSize(1).first().isInstanceOf<DynamicResourceSelectorNode>()
    }

    @Test
    fun `different nodes added depending on whether the type is supported in this region`() {
        val sut = OtherResourcesNode(projectRule.project, OtherResourcesRootNode())

        val availableInRegionAndSelected = "availableInRegionAndSelected"
        val unavailableInRegionAndSelected = "unavailableInRegionAndSelected"
        val availableInRegionButNotSelected = "availableInRegionButNotSelected"
        resourceCache.addEntry(projectRule.project, CloudControlApiResources.listTypes(), listOf(availableInRegionAndSelected, availableInRegionButNotSelected))
        supportedTypesRule.addTypes(setOf(availableInRegionAndSelected, unavailableInRegionAndSelected, availableInRegionButNotSelected))
        dynamicResourceSettingsRule.selected(setOf(availableInRegionAndSelected, unavailableInRegionAndSelected))

        assertThat(sut.children).satisfiesExactly(
            { assertThat(it).isInstanceOf<DynamicResourceSelectorNode>() },
            { assertThat(it).isInstanceOf<DynamicResourceResourceTypeNode>() },
            { assertThat(it).isInstanceOf<UnavailableDynamicResourceTypeNode>() }
        )
    }
}
