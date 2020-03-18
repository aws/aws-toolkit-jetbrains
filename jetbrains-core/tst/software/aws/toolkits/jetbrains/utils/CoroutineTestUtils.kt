// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.delay

suspend fun ListTableModel<*>.waitForModelToBeAtLeast(size: Int) {
    waitForTrue { items.size < size }
}

suspend fun waitForTrue(block: () -> Boolean) {
    while (block()) {
        delay(10)
    }
}
