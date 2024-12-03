// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.model

import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteProgressStatus

enum class BuildAndExecuteStatusIcon(val icon: String) {
    WAIT("<span>&#9744;</span>"),
    CURRENT("<span>&#9744;</span>"),
    DONE("<span style=\"color: green;\">&#10004;</span>"),
}

fun getBuildIcon(progressStatus: BuildAndExecuteProgressStatus) =
    if (progressStatus < BuildAndExecuteProgressStatus.RUN_BUILD) {
        BuildAndExecuteStatusIcon.WAIT.icon
    } else if (progressStatus == BuildAndExecuteProgressStatus.RUN_BUILD) {
        BuildAndExecuteStatusIcon.CURRENT.icon
    } else {
        BuildAndExecuteStatusIcon.DONE.icon
    }

fun getExecutionIcon(progressStatus: BuildAndExecuteProgressStatus) =
    if (progressStatus < BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) {
        BuildAndExecuteStatusIcon.WAIT.icon
    } else if (progressStatus == BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) {
        BuildAndExecuteStatusIcon.CURRENT.icon
    } else {
        BuildAndExecuteStatusIcon.DONE.icon
    }

fun getFixingTestCasesIcon(progressStatus: BuildAndExecuteProgressStatus) =
    if (progressStatus < BuildAndExecuteProgressStatus.FIXING_TEST_CASES) {
        BuildAndExecuteStatusIcon.WAIT.icon
    } else if (progressStatus == BuildAndExecuteProgressStatus.FIXING_TEST_CASES) {
        BuildAndExecuteStatusIcon.CURRENT.icon
    } else {
        BuildAndExecuteStatusIcon.DONE.icon
    }
