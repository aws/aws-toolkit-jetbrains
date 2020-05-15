// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.utils

/**
 * Invokes the [block] if the receiving type is null
 */
fun <T : Any?> T.onNull(block: () -> Unit): T {
    if (this == null) {
        block()
    }
    return this
}
