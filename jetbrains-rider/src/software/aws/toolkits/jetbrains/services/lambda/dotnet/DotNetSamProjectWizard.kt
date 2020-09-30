// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectGenerator
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectWizard
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelector

class DotNetSamProjectWizard : SamProjectWizard {
    override fun listTemplates(): Collection<SamProjectTemplate> =
        listOf(DotNetSamProjectTemplate())

    override fun createSdkSelectionPanel(generator: SamProjectGenerator?): SdkSelector? = null
}
