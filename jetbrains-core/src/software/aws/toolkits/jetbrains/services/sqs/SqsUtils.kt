// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

class queueProperties(queueUrl: String) {
    val regionId = queueUrl.substringAfter("sqs.").substringBefore('.')
    val accountId = queueUrl.substringAfter("amazonaws.com/").substringBefore('/')
    val queueName = queueUrl.substringAfter("${accountId}/")
    val arn = "arn:aws:sqs:${regionId}:${accountId}:$queueName"
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
