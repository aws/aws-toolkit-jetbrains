// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.commands

enum class CodeTransformCommand {
    Start,
    StopClicked,
    TransformStopped,
    MavenBuildComplete,
    TransformComplete,
    TransformResuming,
    AuthRestored,
}
