// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.wizard

import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.lambda.model.Runtime
import javax.swing.JComponent

interface WizardFragment {
    fun title(): String?

    fun component(): JComponent

    fun validateFragment(): ValidationInfo?

    fun isApplicable(template: SamProjectTemplate?): Boolean

    fun update(runtime: Runtime?, template: SamProjectTemplate?)
}
