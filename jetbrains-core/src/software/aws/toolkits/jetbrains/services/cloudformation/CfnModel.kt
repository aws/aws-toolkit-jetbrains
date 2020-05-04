// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

sealed class CfnNode

abstract class CfnExpressionNode : CfnNode()
abstract class CfnNamedNode(val name: CfnScalarValueNode?) : CfnNode()

open class CfnNameValueNode(name: CfnScalarValueNode?, val value: CfnExpressionNode?) : CfnNamedNode(name)
class CfnArrayValueNode(val items: List<CfnExpressionNode>) : CfnExpressionNode()
class CfnScalarValueNode(val value: String) : CfnExpressionNode()

class CfnObjectValueNode(val properties: List<CfnNameValueNode>) : CfnExpressionNode()

class CfnMappingsNode(name: CfnScalarValueNode?, val mappings: List<CfnFirstLevelMappingNode>) : CfnNamedNode(name)
class CfnMappingValue(name: CfnScalarValueNode?, value: CfnExpressionNode?) : CfnNameValueNode(name, value)
class CfnSecondLevelMappingNode(name: CfnScalarValueNode?, val secondLevelMapping: List<CfnMappingValue>) : CfnNamedNode(name)
class CfnFirstLevelMappingNode(name: CfnScalarValueNode?, val firstLevelMapping: List<CfnSecondLevelMappingNode>) : CfnNamedNode(name)

class CfnFunctionNode(
    val name: CfnScalarValueNode,
    val function: CfnIntrinsicFunction,
    val args: List<CfnExpressionNode>
) : CfnExpressionNode()

class CfnRootNode(
    val metadataNode: CfnMetadataNode?,
    val transformNode: CfnTransformNode?,
    val parametersNode: CfnParametersNode?,
    val mappingsNode: CfnMappingsNode?,
    val conditionsNode: CfnConditionsNode?,
    val resourcesNode: CfnResourcesNode?,
    val globalsNode: CfnGlobalsNode?,
    val outputsNode: CfnOutputsNode?
) : CfnNode()

class CfnMetadataNode(name: CfnScalarValueNode?, val value: CfnObjectValueNode?) : CfnNamedNode(name)

class CfnTransformNode(name: CfnScalarValueNode?, val transforms: List<CfnScalarValueNode>) : CfnNamedNode(name)

class CfnGlobalsNode(name: CfnScalarValueNode?, val globals: List<CfnServerlessEntityDefaultsNode>) : CfnNamedNode(name)

class CfnServerlessEntityDefaultsNode(name: CfnScalarValueNode?, val properties: List<CfnNameValueNode>) : CfnNamedNode(name)

class CfnOutputsNode(name: CfnScalarValueNode?, val properties: List<CfnOutputNode>) : CfnNamedNode(name)
class CfnOutputNode(name: CfnScalarValueNode?, value: CfnExpressionNode?) : CfnNameValueNode(name, value)

class CfnConditionsNode(name: CfnScalarValueNode?, val conditions: List<CfnConditionNode>) : CfnNamedNode(name)
class CfnConditionNode(name: CfnScalarValueNode?, value: CfnExpressionNode?) : CfnNameValueNode(name, value)

class CfnParametersNode(name: CfnScalarValueNode?, val parameters: List<CfnParameterNode>) : CfnNamedNode(name)
class CfnParameterNode(name: CfnScalarValueNode?, val properties: List<CfnNameValueNode>) : CfnNamedNode(name)

class CfnResourcesNode(name: CfnScalarValueNode?, val resources: List<CfnResourceNode>) : CfnNamedNode(name)
class CfnResourceNode(
    name: CfnScalarValueNode,
    val type: CfnResourceTypeNode?,
    val properties: CfnResourcePropertiesNode?,
    val condition: CfnResourceConditionNode?,
    val dependsOn: CfnResourceDependsOnNode?,
    val allTopLevelProperties: Map<String, CfnNamedNode>
) : CfnNamedNode(name)

class CfnResourcePropertiesNode(name: CfnScalarValueNode?, val properties: List<CfnResourcePropertyNode>) : CfnNamedNode(name)
class CfnResourceDependsOnNode(name: CfnScalarValueNode?, val dependsOn: List<CfnScalarValueNode>) : CfnNamedNode(name)
class CfnResourceConditionNode(name: CfnScalarValueNode?, val condition: CfnScalarValueNode?) : CfnNamedNode(name)
class CfnResourceTypeNode(name: CfnScalarValueNode?, value: CfnScalarValueNode?) : CfnNameValueNode(name, value)
class CfnResourcePropertyNode(name: CfnScalarValueNode?, value: CfnExpressionNode?) : CfnNameValueNode(name, value)
