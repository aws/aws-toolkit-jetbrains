// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.codemodernizer.model.api

import software.amazon.awssdk.services.codewhispererruntime.model.DownloadArtifact
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStep

fun getArtifactIdentifiers(transformationStep: TransformationStep): DownloadArtifact {
    println("In getArtifactIdentifiers $transformationStep")
    return transformationStep.downloadArtifacts().first()
}

fun findDownloadArtifactStep(transformationSteps: List<TransformationStep>): TransformationStep? {
    println("In findDownloadArtifactStep $transformationSteps")
    for (step in transformationSteps) {
        val artifactType = step.downloadArtifacts()?.get(0)?.downloadArtifactType()
        val artifactId = step.downloadArtifacts()?.get(0)?.downloadArtifactId()
        if (artifactType != null || artifactId != null) {
            return step
        }
    }

    return null
}
