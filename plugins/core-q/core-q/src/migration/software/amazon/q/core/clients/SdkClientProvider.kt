// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.amazon.q.core.clients

import software.amazon.awssdk.http.SdkHttpClient

interface SdkClientProvider {
    fun sharedSdkClient(): SdkHttpClient
}
