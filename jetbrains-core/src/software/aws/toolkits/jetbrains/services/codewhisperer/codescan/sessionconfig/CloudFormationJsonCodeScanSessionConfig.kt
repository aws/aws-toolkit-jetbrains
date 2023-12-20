// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants

internal class CloudFormationJsonCodeScanSessionConfig(
    private val selectedFile: VirtualFile,
    private val project: Project
) : CodeScanSessionConfig(selectedFile, project) {

    override val sourceExt: List<String> = listOf(".json")

    override fun overallJobTimeoutInSeconds(): Long = CodeWhispererConstants.CLOUDFORMATION_CODE_SCAN_TIMEOUT_IN_SECONDS

    override fun getPayloadLimitInBytes(): Int = CodeWhispererConstants.CLOUDFORMATION_PAYLOAD_LIMIT_IN_BYTES
}
