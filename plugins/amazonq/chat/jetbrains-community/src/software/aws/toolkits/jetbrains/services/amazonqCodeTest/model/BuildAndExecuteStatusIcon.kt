// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.model

enum class BuildAndExecuteStatusIcon(val icon: String) {
    WAIT("<span>&#9203;</span>"),
    CURRENT("<span>&#9203;</span>"),
    DONE("<span style=\"color: green;\">&#10004;</span>"),
    FAILED("<span style=\"color: red;\">&#10060;</span>"),
}
