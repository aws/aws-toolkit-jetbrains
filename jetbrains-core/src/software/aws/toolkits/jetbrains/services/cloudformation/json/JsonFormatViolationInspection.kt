// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.psi.PsiFile
import software.aws.toolkits.jetbrains.services.cloudformation.inspections.FormatViolationInspection

class JsonFormatViolationInspection : FormatViolationInspection() {
    override fun isCloudFormationFile(file: PsiFile): Boolean = JsonCfnService.getInstance(file.project)?.isCloudFormation(file) == true

    override fun getShortName(): String = "JsonCfnFormatInspection"
}
