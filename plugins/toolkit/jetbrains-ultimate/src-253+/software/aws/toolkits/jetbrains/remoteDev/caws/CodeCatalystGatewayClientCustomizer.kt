// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.remoteDev.caws

// TODO: GatewayClientCustomizationProvider removed in 2025.3 - investigate new Gateway customization APIs
/*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.jetbrains.rdserver.unattendedHost.customization.controlCenter.GatewayClientCustomizationProvider
import icons.AwsIcons
import software.aws.toolkits.jetbrains.utils.isCodeCatalystDevEnv
import software.aws.toolkits.resources.message

class CodeCatalystGatewayClientCustomizer : GatewayClientCustomizationProvider {
    init {
        if (!isCodeCatalystDevEnv()) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override fun getIcon() = AwsIcons.Logos.AWS_SMILE_SMALL

    override fun getTitle() = message("caws.gateway.title")
}
*/
