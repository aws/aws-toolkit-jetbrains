// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import java.util.concurrent.CompletableFuture

fun <T> CompletableFuture<T>.failedFuture(ex: Throwable): CompletableFuture<T> = CompletableFuture<T>().also {
    it.completeExceptionally(ex)
}