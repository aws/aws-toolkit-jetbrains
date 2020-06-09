// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.rds.model.DBInstance
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCache
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.RdsExplorerRootNode
import software.aws.toolkits.resources.message

class RdsExplorerNodeTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Before
    fun setUp() {
        resourceCache().clear()
    }

    @Test
    fun mySqlResourcesAreListed() {
        val name = RuleUtils.randomName()
        val name2 = RuleUtils.randomName()
        resourceCache().addEntry(
            RdsResources.LIST_INSTANCES_MYSQL, listOf(
                DBInstance.builder().engine(mysqlEngineType).dbName(name).dbInstanceArn("").build(),
                DBInstance.builder().engine(mysqlEngineType).dbName(name2).dbInstanceArn("").build()
            )
        )
        val serviceRootNode = rootNode.buildServiceRootNode(projectRule.project)
        assertThat(serviceRootNode.children).anyMatch { it.value == message("rds.mysql") }
        val mySqlNode = serviceRootNode.children.first { it.value == message("rds.mysql") }
        assertThat(mySqlNode.children).hasSize(2)
        assertThat(mySqlNode.children).anyMatch {
            (it as RdsNode).dbInstance.dbName() == name
        }
        assertThat(mySqlNode.children).anyMatch {
            (it as RdsNode).dbInstance.dbName() == name2
        }
    }

    @Test
    fun postgreSqlResourcesAreListed() {
        val name = RuleUtils.randomName()
        val name2 = RuleUtils.randomName()
        resourceCache().addEntry(
            RdsResources.LIST_INSTANCES_POSTGRES, listOf(
                DBInstance.builder().engine(postgresEngineType).dbName(name).dbInstanceArn("").build(),
                DBInstance.builder().engine(postgresEngineType).dbName(name2).dbInstanceArn("").build()
            )
        )
        val serviceRootNode = rootNode.buildServiceRootNode(projectRule.project)
        assertThat(serviceRootNode.children).anyMatch { it.value == message("rds.postgres") }
        val mySqlNode = serviceRootNode.children.first { it.value == message("rds.postgres") }
        assertThat(mySqlNode.children).hasSize(2)
        assertThat(mySqlNode.children).anyMatch {
            (it as RdsNode).dbInstance.dbName() == name
        }
        assertThat(mySqlNode.children).anyMatch {
            (it as RdsNode).dbInstance.dbName() == name2
        }
    }

    @Test
    fun noResourcesEmptyNodes() {
        resourceCache().addEntry(RdsResources.LIST_INSTANCES_MYSQL, listOf())
        resourceCache().addEntry(RdsResources.LIST_INSTANCES_POSTGRES, listOf())
        val serviceRootNode = rootNode.buildServiceRootNode(projectRule.project)
        assertThat(serviceRootNode.children).isNotEmpty
        serviceRootNode.children.forEach { node ->
            assertThat(node.children).hasSize(1)
            assertThat(node.children).allMatch { it is AwsExplorerEmptyNode }
        }
    }

    @Test
    fun exceptionMakesErrorNodes() {
    }

    private fun resourceCache() = MockResourceCache.getInstance(projectRule.project)

    private companion object {
        val rootNode = RdsExplorerRootNode()
    }
}
