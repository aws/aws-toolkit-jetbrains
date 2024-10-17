// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.openapi.components.service


class CodeWhispererInvocationStatusOld: CodeWhispererInvocationStatus() {
    companion object {
        fun getInstance(): CodeWhispererInvocationStatusOld = service()
    }
}
