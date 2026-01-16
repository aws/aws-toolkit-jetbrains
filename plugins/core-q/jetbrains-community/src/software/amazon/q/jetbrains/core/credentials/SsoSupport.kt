// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials

import software.amazon.q.jetbrains.core.credentials.sso.DiskCache

/**
 * Shared disk cache for SSO for the IDE
 */
val diskCache by lazy { DiskCache() }
