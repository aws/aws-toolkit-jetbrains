// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.iam

import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.Role
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.services.lambda.upload.LAMBDA_PRINCIPAL
import java.util.function.Function
import kotlin.streams.asSequence

fun IamClient.listRolesFilter(predicate: (Role) -> Boolean): Sequence<Role> = this.listRolesPaginator().roles().stream().asSequence().filter(predicate)

data class IamRole(val arn: String) {
    override fun toString(): String = name ?: arn

    val name: String? by lazy {
        ARN_REGEX.matchEntire(arn)?.groups?.elementAtOrNull(1)?.value
    }

    companion object {
        private val ARN_REGEX = "arn:.+:iam::.+:role/(.+)".toRegex()
    }
}

object IamResources {

    private val LIST_RAW_ROLES = ClientBackedCachedResource(IamClient::class, "iam.list_roles") {
        listRolesPaginator().roles().toList()
    }

    @JvmField
    val LIST_LAMBDA_ROLES: Resource<List<IamRole>> = Resource.View(LIST_RAW_ROLES) {
        filter { it.assumeRolePolicyDocument().contains(LAMBDA_PRINCIPAL) }
            .map { IamRole(it.arn()) }
            .sortedWith(Comparator.comparing<IamRole, String>(Function { t -> t.toString() }, String.CASE_INSENSITIVE_ORDER))
            .toList()
    }
}