// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.testFramework.ApplicationRule

internal class MockDynamicResourcesSettings : DynamicResourcesSettings {
    override var selected: Set<String> = emptySet()
}

class MockDynamicResourcesSettingsRule : ApplicationRule() {

    private val settings by lazy {
        DynamicResourcesSettings.getInstance()
    }

    fun selected(types: Set<String>) {
        settings.selected = types
    }

    override fun after() {
        settings.selected = emptySet()
    }
}
