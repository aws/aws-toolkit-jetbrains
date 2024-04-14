// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.codemodernizer.model.api

import software.amazon.awssdk.services.codewhispererruntime.model.TransformationDownloadArtifact
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStep

fun getArtifactIdentifiers(transformationStep: TransformationStep): TransformationDownloadArtifact {
    println("In getArtifactIdentifiers $transformationStep")
    // TODO
    return transformationStep.progressUpdates().last().downloadArtifacts().first()
}

fun findDownloadArtifactStep(transformationSteps: List<TransformationStep>): TransformationStep? {
    println("In findDownloadArtifactStep $transformationSteps")
    for (step in transformationSteps) {
        val artifactType = step.progressUpdates().last().downloadArtifacts().first().downloadArtifactType()
        val artifactId = step.progressUpdates().last().downloadArtifacts().first().downloadArtifactId()
        if (artifactType != null || artifactId != null) {
            return step
        }
    }

    return null
}
