// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

import com.google.gson.annotations.SerializedName

internal data class Identifiable(val id: String)

internal data class Parameter(
    @SerializedName("ParameterKey")
    val parameterKey: String,

    @SerializedName("ParameterValue")
    val parameterValue: String,
)

internal data class Tag(
    @SerializedName("Key")
    val key: String,

    @SerializedName("Value")
    val value: String,
)

internal data class ResourceToImport(
    @SerializedName("ResourceType")
    val resourceType: String,

    @SerializedName("LogicalResourceId")
    val logicalResourceId: String,

    @SerializedName("ResourceIdentifier")
    val resourceIdentifier: Map<String, String>,
)

internal enum class StackActionPhase {
    @SerializedName("VALIDATION_COMPLETE") VALIDATION_COMPLETE,

    @SerializedName("VALIDATION_FAILED") VALIDATION_FAILED,
}

internal enum class StackActionState {
    @SerializedName("IN_PROGRESS") IN_PROGRESS,

    @SerializedName("SUCCESSFUL") SUCCESSFUL,

    @SerializedName("FAILED") FAILED,
}

internal enum class DeploymentMode {
    @SerializedName("REVERT_DRIFT") REVERT_DRIFT,
}

internal data class CreateValidationParams(
    val id: String,
    val uri: String,
    val stackName: String,
    val parameters: List<Parameter>? = null,
    val capabilities: List<String>? = null,
    val resourcesToImport: List<ResourceToImport>? = null,
    val keepChangeSet: Boolean? = null,
    val onStackFailure: String? = null,
    val includeNestedStacks: Boolean? = null,
    val tags: List<Tag>? = null,
    val importExistingResources: Boolean? = null,
    val deploymentMode: DeploymentMode? = null,
    val s3Bucket: String? = null,
    val s3Key: String? = null,
)

internal data class CreateStackActionResult(
    val id: String,
    val changeSetName: String,
    val stackName: String,
)

internal data class GetStackActionStatusResult(
    val id: String,
    val phase: StackActionPhase,
    val state: StackActionState,
    val changes: List<StackChange>? = null,
)

internal data class ValidationDetail(
    @SerializedName("ValidationName")
    val validationName: String,
    @SerializedName("LogicalId")
    val logicalId: String? = null,
    @SerializedName("ResourcePropertyPath")
    val resourcePropertyPath: String? = null,
    @SerializedName("Severity")
    val severity: String,
    @SerializedName("Message")
    val message: String,
)

internal data class DescribeValidationStatusResult(
    val id: String,
    val phase: StackActionPhase,
    val state: StackActionState,
    val changes: List<StackChange>? = null,
    @SerializedName("ValidationDetails")
    val validationDetails: List<ValidationDetail>? = null,
    @SerializedName("FailureReason")
    val failureReason: String? = null,
    val deploymentMode: DeploymentMode? = null,
)

internal data class StackChange(
    val type: String? = null,
    val resourceChange: ResourceChange? = null,
)

internal data class ResourceChange(
    val action: String? = null,
    val logicalResourceId: String? = null,
    val physicalResourceId: String? = null,
    val resourceType: String? = null,
    val replacement: String? = null,
    val scope: List<String>? = null,
    val beforeContext: String? = null,
    val afterContext: String? = null,
    val resourceDriftStatus: String? = null,
    val details: List<ResourceChangeDetail>? = null,
)

internal data class ResourceChangeDetail(
    @SerializedName("Target")
    val target: ResourceTargetDefinition? = null,
    @SerializedName("Evaluation")
    val evaluation: String? = null,
    @SerializedName("ChangeSource")
    val changeSource: String? = null,
    @SerializedName("CausingEntity")
    val causingEntity: String? = null,
)

internal data class ResourceTargetDefinition(
    @SerializedName("Attribute")
    val attribute: String? = null,
    @SerializedName("Name")
    val name: String? = null,
    @SerializedName("RequiresRecreation")
    val requiresRecreation: String? = null,
    @SerializedName("Path")
    val path: String? = null,
    @SerializedName("BeforeValue")
    val beforeValue: String? = null,
    @SerializedName("AfterValue")
    val afterValue: String? = null,
    @SerializedName("AttributeChangeType")
    val attributeChangeType: String? = null,
)
