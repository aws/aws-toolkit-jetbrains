// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamo.editor

import com.intellij.testFramework.LightVirtualFile
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
 * Light virtual file to represent a dynamo table, used to open the custom editor
 */
class DynamoVirtualFile(private val tableArm: String, val dynamoDbClient: DynamoDbClient) : LightVirtualFile(tableArm) {
    init {
        isWritable = false
    }

    val tableName = tableArm.substringAfterLast('/')

    /**
     * Override the presentable name so editor tabs only use table name
     */
    override fun getPresentableName(): String = tableName

    /**
     * Use the ARN as the path so editor tool tips can be differentiated
     */
    override fun getPath(): String = tableArm

    /**
     * We use the ARN as the equality, so that we can show 2 tables from different accounts/regions with same name
     */
    override fun equals(other: Any?): Boolean {
        if (other !is DynamoVirtualFile) {
            return false
        }
        return this.tableArm == other.tableArm
    }

    override fun hashCode(): Int = tableArm.hashCode()
}
