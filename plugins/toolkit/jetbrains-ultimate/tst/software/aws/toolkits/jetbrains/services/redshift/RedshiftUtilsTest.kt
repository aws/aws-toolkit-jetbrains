// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift

import com.intellij.testFramework.HeavyPlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import software.amazon.awssdk.services.redshift.model.Cluster
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCacheExtension
import software.aws.toolkits.jetbrains.core.region.getDefaultRegion
import software.aws.toolkits.jetbrains.services.sts.StsResources

class RedshiftUtilsTest : HeavyPlatformTestCase() {
    private val resourceCache = MockResourceCacheExtension()
    private val clusterId = RuleUtils.randomName()
    private val accountId = RuleUtils.randomName()
    private val mockCluster = mock<Cluster> {
        on { clusterIdentifier() } doReturn clusterId
    }

    fun testAccountIdArn() {
        val region = getDefaultRegion()
        resourceCache.addEntry(project, StsResources.ACCOUNT, accountId)
        val arn = project.clusterArn(mockCluster, region)
        assertThat(arn).isEqualTo("arn:${region.partitionId}:redshift:${region.id}:$accountId:cluster:$clusterId")
    }

    fun testNoAccountIdArn() {
        val region = getDefaultRegion()
        val arn = project.clusterArn(mockCluster, region)
        assertThat(arn).isEqualTo("arn:${region.partitionId}:redshift:${region.id}::cluster:$clusterId")
    }
}
