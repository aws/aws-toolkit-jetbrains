// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DriftInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceChange
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceChangeDetail
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceTargetDefinition

class ChangeSetDriftTest {

    @Test
    fun `no drift returns original json unchanged`() {
        val rc = ResourceChange(action = "Modify", logicalResourceId = "MyBucket")
        val json = """{ "BucketName": "test" }"""
        assertThat(annotateDriftInJson(rc, json)).isEqualTo(json)
    }

    @Test
    fun `deleted resource prepends comment`() {
        val rc = ResourceChange(resourceDriftStatus = "DELETED")
        val json = """{ "Key": "value" }"""
        val result = annotateDriftInJson(rc, json)
        assertThat(result).startsWith("// \u26A0\uFE0F Resource deleted out-of-band")
        assertThat(result).endsWith(json)
    }

    @Test
    fun `drift annotation appended to correct property line`() {
        val rc = ResourceChange(
            action = "Modify",
            logicalResourceId = "Fn",
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        name = "MemorySize",
                        path = "/Properties/MemorySize",
                        drift = DriftInfo(previousValue = "128", actualValue = "256"),
                    )
                )
            )
        )
        val json = """{
  "Properties": {
    "MemorySize": 128,
    "Runtime": "python3.12"
  }
}"""
        val result = annotateDriftInJson(rc, json)
        val lines = result.lines()
        val memoryLine = lines.first { it.contains("MemorySize") }
        assertThat(memoryLine).contains("\u2190 \u26A0\uFE0F Drifted (Live AWS: 256)")
        val runtimeLine = lines.first { it.contains("Runtime") }
        assertThat(runtimeLine).doesNotContain("Drifted")
    }

    @Test
    fun `drift with LiveResourceDrift fallback`() {
        val rc = ResourceChange(
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        name = "Timeout",
                        path = "/Properties/Timeout",
                        liveResourceDrift = DriftInfo(previousValue = "30", actualValue = "60"),
                    )
                )
            )
        )
        val json = """{
  "Properties": {
    "Timeout": 30
  }
}"""
        val result = annotateDriftInJson(rc, json)
        assertThat(result).contains("Drifted (Live AWS: 60)")
    }

    @Test
    fun `drift with null actualValue is ignored`() {
        val rc = ResourceChange(
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        path = "/Properties/MemorySize",
                        drift = DriftInfo(previousValue = "128", actualValue = null),
                    )
                )
            )
        )
        val json = """{ "Properties": { "MemorySize": 128 } }"""
        assertThat(annotateDriftInJson(rc, json)).isEqualTo(json)
    }

    @Test
    fun `drift with missing path is ignored`() {
        val rc = ResourceChange(
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        drift = DriftInfo(previousValue = "128", actualValue = "256"),
                    )
                )
            )
        )
        val json = """{ "MemorySize": 128 }"""
        assertThat(annotateDriftInJson(rc, json)).isEqualTo(json)
    }

    @Test
    fun `path with numeric index skips array indices`() {
        val rc = ResourceChange(
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        path = "/Tags/0/Value",
                        drift = DriftInfo(previousValue = "old", actualValue = "new"),
                    )
                )
            )
        )
        val json = """{
  "Tags": [
    {
      "Key": "env",
      "Value": "prod"
    }
  ]
}"""
        val result = annotateDriftInJson(rc, json)
        val valueLine = result.lines().first { it.contains("\"Value\"") }
        assertThat(valueLine).contains("Drifted (Live AWS: new)")
    }

    @Test
    fun `unresolvable path leaves json unchanged`() {
        val rc = ResourceChange(
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        path = "/NonExistent/Property",
                        drift = DriftInfo(previousValue = "a", actualValue = "b"),
                    )
                )
            )
        )
        val json = """{ "Other": "value" }"""
        assertThat(annotateDriftInJson(rc, json)).isEqualTo(json)
    }

    @Test
    fun `multiple drifted properties annotated independently`() {
        val rc = ResourceChange(
            details = listOf(
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        path = "/Properties/MemorySize",
                        drift = DriftInfo(previousValue = "128", actualValue = "256"),
                    )
                ),
                ResourceChangeDetail(
                    target = ResourceTargetDefinition(
                        path = "/Properties/Timeout",
                        drift = DriftInfo(previousValue = "30", actualValue = "60"),
                    )
                ),
            )
        )
        val json = """{
  "Properties": {
    "MemorySize": 128,
    "Timeout": 30
  }
}"""
        val result = annotateDriftInJson(rc, json)
        assertThat(result).contains("Drifted (Live AWS: 256)")
        assertThat(result).contains("Drifted (Live AWS: 60)")
    }
}
