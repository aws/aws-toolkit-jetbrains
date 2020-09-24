// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

/**
 * [Official Docs](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference.html)
 */
enum class CfnIntrinsicFunction(val id: String) {
    FnBase64("Fn::Base64"),
    FnCidr("Fn::Cidr"),
    FnFindInMap("Fn::FindInMap"),
    FnGetAtt("Fn::GetAtt"),
    FnGetAZs("Fn::GetAZs"),
    FnImportValue("Fn::ImportValue"),
    FnJoin("Fn::Join"),
    FnSelect("Fn::Select"),
    FnSplit("Fn::Split"),
    FnSub("Fn::Sub"),
    FnTransform("Fn::Transform"),
    Ref("Ref"),

    // Conditions
    FnAnd("Fn::And"),
    FnEquals("Fn::Equals"),
    FnIf("Fn::If"),
    FnNot("Fn::Not"),
    FnOr("Fn::Or");

    val shortForm = id.removePrefix("Fn::")

    companion object {
        val FULL_NAMES: Map<String, CfnIntrinsicFunction> by lazy {
            values().associateBy { it.id }
        }
        val SHORT_NAMES: Map<String, CfnIntrinsicFunction> by lazy {
            values().associateBy { it.shortForm }
        }
    }
}
