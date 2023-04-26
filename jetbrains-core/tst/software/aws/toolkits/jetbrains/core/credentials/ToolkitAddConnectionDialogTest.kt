// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.help.impl.HelpManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.help.HelpIds

class ToolkitAddConnectionDialogTest {

    @Rule
    @JvmField
    val application = ApplicationRule()

    @Test
    fun `is aws builder id has correct doc`() {
        assertThat((HelpManager.getInstance() as HelpManagerImpl).getHelpUrl(HelpIds.SETUP_AWS_BUILDER_ID_DOC.id))
            .isEqualTo("https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/builder-id.html")
    }
}
