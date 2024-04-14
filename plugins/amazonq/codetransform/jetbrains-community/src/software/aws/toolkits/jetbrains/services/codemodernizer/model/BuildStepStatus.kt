// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import software.amazon.awssdk.services.codewhispererruntime.model.TransformationProgressUpdateStatus

enum class BuildStepStatus {
    DONE,
    ERROR,
    WARNING,
    WORKING,
    PAUSED
}

fun mapTransformationPlanApiStatus(apiStatus: TransformationProgressUpdateStatus): BuildStepStatus = when (apiStatus) {
    TransformationProgressUpdateStatus.COMPLETED -> BuildStepStatus.DONE

    // TODO
    TransformationProgressUpdateStatus.PAUSED -> BuildStepStatus.DONE

    TransformationProgressUpdateStatus.FAILED -> BuildStepStatus.WARNING
    TransformationProgressUpdateStatus.IN_PROGRESS -> BuildStepStatus.WORKING
    TransformationProgressUpdateStatus.UNKNOWN_TO_SDK_VERSION -> BuildStepStatus.ERROR
}
