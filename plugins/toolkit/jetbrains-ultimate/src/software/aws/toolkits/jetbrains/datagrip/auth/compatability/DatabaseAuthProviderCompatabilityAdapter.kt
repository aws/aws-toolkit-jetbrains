// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.auth.compatability

import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseAuthProvider.ApplicabilityLevel
import com.intellij.database.dataSource.DatabaseConnectionConfig
import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project

@Suppress("UnstableApiUsage")
interface DatabaseAuthProviderCompatabilityAdapter : DatabaseAuthProvider {
    override fun getApplicability(
        point: DatabaseConnectionPoint,
        level: ApplicabilityLevel
    ): ApplicabilityLevel.Result {
        if (!isApplicable(point.dataSource)) return ApplicabilityLevel.Result.NOT_APPLICABLE
        return super.getApplicability(point, level)
    }

    override fun createWidget(
        project: Project?,
        credentials: DatabaseCredentials,
        config: DatabaseConnectionConfig
    ): DatabaseAuthProvider.AuthWidget? {
        return createWidget()
    }

    fun isApplicable(dataSource: LocalDataSource): Boolean
    fun createWidget(): DatabaseAuthProvider.AuthWidget?
}

fun DatabaseConnectionInterceptor.ProtoConnection.project() = project
