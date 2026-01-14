// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core

import software.aws.toolkit.jetbrains.core.Resource

val Resource<*>.id: String
    get() = when (this) {
        is Resource.Cached -> this.id
        is Resource.View<*, *> -> this.underlying.id
    }
