// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.region

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.core.utils.test.aString

class ToolkitRegionProviderTest {

    @Test
    fun unknownServicesAreConsideredNonGlobal() {
        val region = anAwsRegion()
        val sut = createTestSubject(region)
        assertThat(sut.isServiceGlobal(region, "non-existent-service")).isFalse
    }

    private fun createTestSubject(region: AwsRegion) = object : ToolkitRegionProvider() {
        override fun partitionData(): Map<String, PartitionData> = mapOf(
            region.partitionId to PartitionData(aString(), emptyMap(), mapOf(region.id to region))
        )

        override fun defaultRegion(): AwsRegion {
            TODO("Not yet implemented")
        }

        override fun defaultPartition(): AwsPartition {
            TODO("Not yet implemented")
        }
    }
}
