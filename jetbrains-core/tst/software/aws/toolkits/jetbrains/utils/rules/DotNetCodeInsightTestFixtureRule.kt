// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.rules

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory

class DotNetCodeInsightTestFixtureRule : CodeInsightTestFixtureRule() {

    override fun createTestFixture(): CodeInsightTestFixture {

        val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
        val fixtureBuilder = fixtureFactory.createFixtureBuilder(testName)
        val newFixture = fixtureFactory.createCodeInsightFixture(fixtureBuilder.fixture)
        newFixture.testDataPath = testDataPath
        newFixture.setUp()

        return newFixture
    }

    override val fixture: CodeInsightTestFixture
        get() = lazyFixture.value
}
