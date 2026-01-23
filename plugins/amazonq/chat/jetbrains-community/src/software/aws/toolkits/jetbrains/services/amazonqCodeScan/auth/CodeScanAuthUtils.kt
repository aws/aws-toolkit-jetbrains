// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan.auth

import com.intellij.openapi.project.Project
import software.amazon.q.jetbrains.core.gettingstarted.editor.ActiveConnection
import software.amazon.q.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import software.amazon.q.jetbrains.core.gettingstarted.editor.checkBearerConnectionValidity

fun isCodeScanAvailable(project: Project): Boolean = checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q) is ActiveConnection.ValidBearer
