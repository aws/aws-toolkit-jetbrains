// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.icons

import com.intellij.openapi.components.BaseComponent

internal class RiderAwsIconsPatcherRegistrar : BaseComponent {

    companion object {
        private const val NAME = "RiderAwsIconsPatcherRegistrar"
    }

    override fun initComponent() {
        RiderAwsIconsPatcher.install()
    }

    override fun getComponentName(): String = NAME
}