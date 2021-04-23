// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.ecs.model.Service
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import java.util.concurrent.CompletableFuture
class EcsExecUtilsTest {
    @JvmField
    @Rule
    val resourceCache = MockResourceCacheRule()

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun `enableExecuteCommand flag value is returned correctly`() {
        val clusterArn = "arn:aws:ecs:us-east-1:123456789012:cluster/cluster-name"
        val serviceArn = "arn:aws:ecs:us-east-1:123456789012:service/service-name"

        val ecsService = Service.builder()
            .clusterArn(clusterArn)
            .serviceArn(serviceArn)
            .enableExecuteCommand(true)
            .serviceName("service-name")
            .build()

        resourceCache.addEntry(
            projectRule.project, EcsResources.describeService(clusterArn, serviceArn),
            CompletableFuture.completedFuture(ecsService)
        )
        val executeCommandFlag = EcsExecUtils(projectRule.project).executeCommandFlagStatus(ecsService)
        assertThat(executeCommandFlag).isTrue
    }
}
