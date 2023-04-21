// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants

class CodeWhispererConstantsTest {
    @Test
    fun `is codewishperer help uri has correct doc link`() {
        assertThat(CodeWhispererConstants.CODEWHISPERER_LOGIN_HELP_URI)
            .isEqualTo("https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/setup-credentials.html")
    }
}
