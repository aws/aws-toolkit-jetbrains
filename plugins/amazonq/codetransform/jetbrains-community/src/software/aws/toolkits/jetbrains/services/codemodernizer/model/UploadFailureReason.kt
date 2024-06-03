// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

sealed class UploadFailureReason(val message: String) {
    data class HTTP_ERROR(val statusCode: Int) : UploadFailureReason("HTTP error")
    object PRESIGNED_URL_EXPIRED : UploadFailureReason("Presigned Upload Url Expired")
    object CONNECTION_REFUSED : UploadFailureReason("Connection Refused")
    object CREDENTIALS_EXPIRED : UploadFailureReason("Credentials Expired")
    data class OTHER(val errorMessage: String) : UploadFailureReason(errorMessage)
}
