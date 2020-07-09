// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import software.aws.toolkits.core.region.AwsRegion

class queueProperties(queueUrl: String, region: AwsRegion) {
    val accountId = queueUrl.substringAfter("amazonaws.com/").substringBefore('/')
    val queueName = queueUrl.substringAfter("${accountId}/")
    val arn = "arn:aws:sqs:${region.id}:${accountId}:$queueName"
}

/*
fun queueArn(queueUrl: String, region: AwsRegion) : String {
    val accountId = queueUrl.substringAfter("amazonaws.com/").substringBefore('/')
    val queueName = queueUrl.substringAfter("${accountId}/")
    return "arn:aws:sqs:${region.id}:${accountId}:$queueName"
}

fun queueName(queueUrl: String) : String {
    val accountId = queueUrl.substringAfter("amazonaws.com/").substringBefore('/')
    return queueUrl.substringAfter("${accountId}/")
}
*/
