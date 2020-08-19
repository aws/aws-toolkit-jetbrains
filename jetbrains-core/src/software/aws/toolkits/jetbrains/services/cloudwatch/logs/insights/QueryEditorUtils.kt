// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

const val default_query = "fields @timestamp, @message| sort @timestamp desc| limit 20"

val sampleQueries = mapOf(
    "Lambda: View Latency Statistics for 5-minute intervals" to mapOf(
        "query" to "filter @type = \"REPORT\"| stats avg(@duration), max(@duration), min(@duration) by bin(5m)",
        "logGroups" to listOf<String>()),
    "Lambda: Determine the amount of overprovisioned memory" to mapOf(
        "query" to "filter @type = \"REPORT\"| stats max(@memorySize / 1024 / 1024) as provisonedMemoryMB,\n" +
            "min(@maxMemoryUsed / 1024 / 1024) as smallestMemoryRequestMB," +
            "avg(@maxMemoryUsed / 1024 / 1024) as avgMemoryUsedMB," +
            "max(@maxMemoryUsed / 1024 / 1024) as maxMemoryUsedMB," +
            "provisonedMemoryMB - maxMemoryUsedMB as overProvisionedMB",
        "logGroups" to listOf<String>()),
    "Lambda: Find the most Expensive Requests" to mapOf(
        "query" to "filter @type = \"REPORT\" |" +
            "fields @requestId, @billedDuration |" +
            "sort by @billedDuration desc",
        "logGroups" to listOf<String>()),
    "VPC flow log: Average, min, and max byte transfers by source and destination IP addresses" to mapOf(
        "query" to "stats avg(bytes), min(bytes), max(bytes) by srcAddr, dstAddr", "logGroups" to listOf<String>()),
    "VPC flow log: IP addresses using UDP transfer protocol" to mapOf(
        "query" to "filter protocol=17| stats count(*) by srcAddr", "logGroups" to listOf<String>()),
    "VPC flow log: Top 10 byte transfers by source and destination IP addresses" to mapOf(
        "query" to "stats sum(bytes) as bytesTransferred by srcAddr, dstAddr" +
            "| sort bytesTransferred desc" +
            "| limit 10", "logGroups" to listOf<String>()),
    "VPC flow log: Top 20 source IP addresses with highest number of rejected requests" to mapOf(
        "query" to "filter action=\"REJECT\"" +
            "| stats count(*) as numRejections by srcAddr" +
            "| sort numRejections desc" +
            "| limit 20", "logGroups" to listOf<String>()),
    "CloudTrail: Number of log entries by service, event type, and region" to mapOf(
        "query" to "stats count(*) by eventSource, eventName, awsRegion", "logGroups" to listOf<String>()),
    "CloudTrail: Number of log entries by region and EC2 event type" to mapOf(
        "query" to "filter eventSource=\"ec2.amazonaws.com\"" +
            "| stats count(*) as eventCount by eventName, awsRegion" +
            "| sort eventCount desc", "logGroups" to listOf<String>()),
    "CloudTrail: Regions, usernames, and ARNs of newly created IAM users" to mapOf(
        "query" to "filter eventName=\"CreateUser\"" +
            "| fields awsRegion, requestParameters.userName, responseElements.user.arn", "logGroups" to listOf<String>()),
    "CloudTrail: Number of log entries by service, event type, and region" to mapOf(
        "query" to "stats count(*) by eventSource, eventName, awsRegion", "logGroups" to listOf<String>())
)
