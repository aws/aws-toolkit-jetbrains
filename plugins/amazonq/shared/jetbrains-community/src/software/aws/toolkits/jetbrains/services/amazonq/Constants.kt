// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.util.SystemInfo
import software.amazon.awssdk.services.codewhispererruntime.model.IdeCategory
import software.amazon.awssdk.services.codewhispererruntime.model.OperatingSystem
import software.amazon.awssdk.services.codewhispererruntime.model.UserContext
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata

const val APPLICATION_ZIP = "application/zip"
const val SERVER_SIDE_ENCRYPTION = "x-amz-server-side-encryption"
const val AWS_KMS = "aws:kms"
const val SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID = "x-amz-server-side-encryption-aws-kms-key-id"
const val CONTENT_SHA256 = "x-amz-checksum-sha256"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_PROJECT_SIZE =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#reduce-project-size"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_CONFIGURE_PROXY =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#configure-proxy"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_ALLOW_S3_ACCESS =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#allowlist-s3-bucket"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_MVN_FAILURE =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#maven-commands-failing"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_EXPIRED =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#download-24-hrs"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_REMOVE_WILDCARD =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#remove-wildcard"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_UPLOAD_ERROR_OVERVIEW =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#project-upload-fail"

const val CODE_TRANSFORM_TROUBLESHOOT_DOC_DOWNLOAD_ERROR_OVERVIEW =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/troubleshooting-code-transformation.html#download-code-fail"

const val CODE_TRANSFORM_PREREQUISITES =
    "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/code-transformation.html#prerequisites"

val codeWhispererUserContext = ClientMetadata.getDefault().let {
    val osForCodeWhisperer: OperatingSystem =
        when {
            SystemInfo.isWindows -> OperatingSystem.WINDOWS
            SystemInfo.isMac -> OperatingSystem.MAC
            // For now, categorize everything else as "Linux" (Linux/FreeBSD/Solaris/etc)
            else -> OperatingSystem.LINUX
        }

    UserContext.builder()
        .ideCategory(IdeCategory.JETBRAINS)
        .operatingSystem(osForCodeWhisperer)
        .product(FEATURE_EVALUATION_PRODUCT_NAME)
        .clientId(it.clientId)
        .ideVersion(it.awsVersion)
        .build()
}

const val FEATURE_EVALUATION_PRODUCT_NAME = "CodeWhisperer"
