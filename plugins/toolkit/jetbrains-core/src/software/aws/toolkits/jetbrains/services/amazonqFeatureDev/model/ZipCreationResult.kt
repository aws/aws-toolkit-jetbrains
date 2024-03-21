// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.model

data class ZipCreationResult(val payload: ByteArray, val checksum: String, val contentLength: Long)
