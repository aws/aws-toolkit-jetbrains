// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.rules

import com.goide.GoConstants
import com.goide.sdk.GoSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.io.File

class GoCodeInsightTestFixtureRule : CodeInsightTestFixtureRule() {
    override fun createTestFixture(): CodeInsightTestFixture {
        val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
        val projectFixture = fixtureFactory.createLightFixtureBuilder(GoLightProjectDescriptor())
        val codeInsightFixture = fixtureFactory.createCodeInsightFixture(projectFixture.fixture)
        codeInsightFixture.testDataPath = testDataPath
        codeInsightFixture.setUp()
        PsiTestUtil.addContentRoot(codeInsightFixture.module, codeInsightFixture.tempDirFixture.getFile(".")!!)

        return codeInsightFixture
    }
}

class GoLightProjectDescriptor : LightProjectDescriptor() {
    override fun getSdk(): Sdk? = null
    override fun getModuleTypeId(): String = GoConstants.MODULE_TYPE_ID
}

fun createMockSdk(version: String): Sdk {
    val homePath = File("testData/mockSdk-$version/").absolutePath
    val sdkType = GoSdkType()
    val sdk = ProjectJdkImpl("Go $version", sdkType, homePath, version)
    sdkType.setupSdkPaths(sdk)
    sdk.versionString = version
    return sdk
}
