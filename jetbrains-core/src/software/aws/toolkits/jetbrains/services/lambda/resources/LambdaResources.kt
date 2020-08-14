// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.resources

import software.amazon.awssdk.services.lambda.LambdaClient
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.core.find

object LambdaResources {
    @JvmField
    val LIST_FUNCTIONS = ClientBackedCachedResource(LambdaClient::class, "lambda.list_functions") {
        listFunctionsPaginator().functions().filterNotNull().toList()
    }

    @JvmField
    val LIST_FUNCTION_NAMES: Resource<List<String>> = Resource.View(LIST_FUNCTIONS) {
        map { it.functionName() }.toList()
    }

    fun function(name: String) = LIST_FUNCTIONS.find { it.functionName() == name }
}
