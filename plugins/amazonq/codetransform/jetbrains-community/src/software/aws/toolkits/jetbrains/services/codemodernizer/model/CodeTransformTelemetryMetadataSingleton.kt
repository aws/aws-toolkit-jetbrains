// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

object CodeTransformTelemetryMetadataSingleton {
    private val instance = CodeTransformTelemetryMetadata()

    fun getInstance() = instance

    fun setDependencyVersionSelected(version: String?) {
        instance.dependencyVersionSelected = version
    }

    fun setCancelledFromChat(cancelled: Boolean) {
        instance.cancelledFromChat = cancelled
    }
}
