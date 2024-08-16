// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

data class BuildProgressStepTreeItem(
    val text: String,
    var status: BuildStepStatus,
    val id: ProgressStepId,
    var runtime: String? = null,
    var finishedTime: String? = null,
    val transformationStepId: Int? = null
)

enum class ProgressStepId(val order: Int) {
    UPLOADING(1),
    BUILDING(2),
    PLANNING(3),
    TRANSFORMING(4),
    PLAN_STEP(5),
    PAUSED(6),
    RESUMED(7),
    ROOT_STEP(99)
}
