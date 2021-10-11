// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic.explorer

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceStateChangedNotificationHandler

class DynamicResourceStateChangedNotificationHandlerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Test
    fun `Html tags are removed`() {
        val identifier = DynamicResourceStateChangedNotificationHandler(projectRule.project).removeHtml("sampleIdentifier<html>p</html>", "sampleType")
        assertThat(identifier).isEqualTo("sampleIdentifierp")
    }
}
