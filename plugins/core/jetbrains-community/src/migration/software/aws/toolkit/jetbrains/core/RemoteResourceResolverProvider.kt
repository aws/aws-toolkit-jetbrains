// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.aws.toolkit.jetbrains.core

import com.intellij.openapi.components.service
import software.aws.toolkit.core.utils.RemoteResourceResolver

interface RemoteResourceResolverProvider {
    fun get(): RemoteResourceResolver

    companion object {
        fun getInstance(): RemoteResourceResolverProvider = service()
    }
}
