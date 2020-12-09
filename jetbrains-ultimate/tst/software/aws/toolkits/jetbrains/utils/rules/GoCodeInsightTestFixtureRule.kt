// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.rules

import com.goide.sdk.GoSdkService
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.runInEdtAndWait

class GoCodeInsightTestFixtureRule : CodeInsightTestFixtureRule()

fun Project.setGoSdkVersion(version: String) {
    TestModeFlags.set(GoSdkService.TESTING_SDK_VERSION, version)
    runInEdtAndWait {
        GoSdkService.getInstance(this).apply {
            setSdk(getSdk(null))
        }
    }
}
