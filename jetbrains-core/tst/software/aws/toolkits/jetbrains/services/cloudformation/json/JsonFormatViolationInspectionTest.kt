// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class JsonFormatViolationInspectionTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun problemsAreRendered() {
        val codeInsight = projectRule.fixture
        codeInsight.enableInspections(JsonFormatViolationInspection())

        runInEdtAndWait {
            codeInsight.configureByText(
                JsonCfnFileType.INSTANCE,
                """{ "AWSTemplateFormatVersion" : "1990-09-09" }"""
            )

            assertThat(codeInsight.doHighlighting())
                .extracting<String> { it.inspectionToolId }
                .anyMatch { it == "JsonCfnFormatInspection" }
        }
    }

    @Test
    fun noProblemsLeadsToNoRendering() {
        val codeInsight = projectRule.fixture
        codeInsight.enableInspections(JsonFormatViolationInspection())

        runInEdtAndWait {
            codeInsight.configureByText(
                JsonCfnFileType.INSTANCE,
                """{ "AWSTemplateFormatVersion" : "2010-09-09" }"""
            )

            assertThat(codeInsight.doHighlighting())
                .extracting<String> { it.inspectionToolId }
                .noneMatch { it == "JsonCfnFormatInspection" }
        }
    }
}
