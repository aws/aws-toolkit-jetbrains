// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.amazonq.project.FeatureDevSessionContext

class DocSessionContext(project: Project, maxProjectSizeBytes: Long? = null) : FeatureDevSessionContext(project, maxProjectSizeBytes)
