// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.rds.model.DBInstance
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.core.utils.test.hasOnlyElementsOfType
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.explorer.nodes.RdsExplorerRootNode
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.services.rds.resources.LIST_SUPPORTED_INSTANCES

class RdsExplorerNodeTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val resourceCache = MockResourceCacheRule()

    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()

    @Rule
    @JvmField
    val credentialManager = MockCredentialManagerRule()

    @Rule
    @JvmField
    val regionProvider = MockRegionProviderRule()

    @Before
    fun setUp() {
        credentialManager.addCredentials(credentialId)
        regionProvider.addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test
    fun `database resources are listed`() {
        val databases = RdsEngine.values().flatMap { it.engines }.associateWith { RuleUtils.randomName(prefix = "$it-") }

        resourceCache.addEntry(
            projectRule.project,
            LIST_SUPPORTED_INSTANCES,
            databases.map { dbInstance(it.key, it.value) }
        )
        val serviceRootNode = sut.buildServiceRootNode(projectRule.project)
        assertThat(serviceRootNode.children).hasSize(databases.size).hasOnlyElementsOfType<RdsNode>().allSatisfy {
            assertThat(it.resourceType()).isEqualTo("instance")
        }.extracting<String> {
            it.dbInstance.dbInstanceIdentifier()
        }.containsOnly(*databases.values.toTypedArray())
    }

    private companion object {
        val sut = RdsExplorerRootNode()
        fun dbInstance(engine: String, name: String): DBInstance =
            DBInstance.builder().engine(engine).dbName(name).dbInstanceIdentifier(name).dbInstanceArn("").build()
    }
}
