// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.amazon.q.jetbrains.core

import com.intellij.openapi.components.service
import software.amazon.q.core.utils.RemoteResourceResolver

interface RemoteResourceResolverProvider {
    fun get(): RemoteResourceResolver

    companion object {
        fun getInstance(): RemoteResourceResolverProvider = service()
    }
}
