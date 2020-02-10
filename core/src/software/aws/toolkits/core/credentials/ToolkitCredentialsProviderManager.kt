// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

interface ToolkitCredentialsChangeListener {
    fun providerAdded(identifier: ToolkitCredentialsIdentifier) {}
    fun providerModified(identifier: ToolkitCredentialsIdentifier) {}
    fun providerRemoved(identifier: ToolkitCredentialsIdentifier) {}
}

class CredentialProviderNotFound : RuntimeException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, exception: Exception) : super(msg, exception)
}
