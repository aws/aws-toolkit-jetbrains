// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Rule
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule

open class CodeWhispererFileCrawlerTest(projectRule: CodeInsightTestFixtureRule) {
    @JvmField
    @Rule
    val projectRule: CodeInsightTestFixtureRule = projectRule

    lateinit var fixture: CodeInsightTestFixture
    lateinit var project: Project

    open fun setup() {
        fixture = projectRule.fixture
        project = projectRule.project
    }
}
