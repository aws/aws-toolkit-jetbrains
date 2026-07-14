// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import java.security.MessageDigest

internal object HashUtils {
    fun parseHashString(hashString: String): Pair<String, String>? {
        val parts = hashString.split(":", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    fun computeHash(data: ByteArray, algorithm: String): String {
        val digestAlgorithm = when (algorithm.lowercase()) {
            "sha256" -> "SHA-256"
            "sha384" -> "SHA-384"
            "sha512" -> "SHA-512"
            else -> algorithm.uppercase()
        }
        return MessageDigest.getInstance(digestAlgorithm)
            .digest(data)
            .joinToString("") { "%02x".format(it) }
    }
}
