// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

interface ToolkitCredentialsChangeListener {
    fun providerAdded(provider: ToolkitCredentialsProvider) {}
    fun providerModified(provider: ToolkitCredentialsProvider) {}
    fun providerRemoved(providerId: String) {}
}