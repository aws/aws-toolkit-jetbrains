// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc

object ChatConstants {
    const val REQUEST_TIMEOUT_MS = 60_000 // 60 seconds

    // API Constraints
    const val FILE_PATH_SIZE_LIMIT = 4_000 // Maximum length of file paths in characters (actual API limit: 4096)
    const val CUSTOMER_MESSAGE_SIZE_LIMIT = 4_000 // Maximum size of the prompt message in characters (actual API limit: 4096)
    const val FQN_SIZE_MIN = 1 // Minimum length of fully qualified name in characters (inclusive)
    const val FQN_SIZE_LIMIT = 256 // Maximum length of fully qualified name in characters (exclusive, actual API limit: 256)
    const val INDEX_INPROGRESS_MSG = "By the way, I'm still indexing this project for full context from your workspace. I may have a better response in a few minutes when it's complete if you'd like to try again then."
}
