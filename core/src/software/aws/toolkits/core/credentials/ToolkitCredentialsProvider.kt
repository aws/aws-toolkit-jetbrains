// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

abstract class ToolkitCredentialsIdentifier {
    /**
     * The ID must be unique across all [ToolkitCredentialsIdentifier].
     */
    abstract val id: String

    /**
     * A user friendly display name shown in the UI.
     */
    abstract val displayName: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ToolkitCredentialsProvider

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "${this::class.simpleName}(id='$id')"
}

class ToolkitCredentialsProvider(id: ToolkitCredentialsIdentifier, delegate: AwsCredentialsProvider) : AwsCredentialsProvider by delegate {
    val id: String = id.id
    val displayName = id.displayName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ToolkitCredentialsProvider

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "${this::class.simpleName}(id='$id')"
}
