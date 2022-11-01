// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import software.aws.toolkits.jetbrains.core.experiments.ToolkitExperiment

object SyncServerlessApplicationExperiment: ToolkitExperiment (
    "syncServerlessApplication",
    { "Sync Serverless Application" },
    {"Enables Sync applications instead of deploy"}
    )

object SyncServerlessApplicationCodeExperiment: ToolkitExperiment (
    "syncServerlessApplicationCode",
    { "Sync Serverless Application Code(skip infra)" },
    {"Enables Sync applications instead of deploy"}
)
