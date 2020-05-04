// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonNumberLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonReferenceExpression
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonValue
import com.intellij.psi.PsiElement
import com.intellij.util.containers.BidirectionalMap
import software.aws.toolkits.jetbrains.services.cloudformation.CfnArrayValueNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnConditionNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnConditionsNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnExpressionNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnFirstLevelMappingNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnFunctionNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnGlobalsNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnIntrinsicFunction
import software.aws.toolkits.jetbrains.services.cloudformation.CfnMappingValue
import software.aws.toolkits.jetbrains.services.cloudformation.CfnMappingsNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnMetadataNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnNameValueNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnNamedNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnObjectValueNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnOutputNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnOutputsNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParameterNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParametersNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParsedFile
import software.aws.toolkits.jetbrains.services.cloudformation.CfnProblem
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourceConditionNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourceDependsOnNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourceNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourcePropertiesNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourcePropertyNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourceTypeNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnResourcesNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnRootNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnScalarValueNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnSecondLevelMappingNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnSection
import software.aws.toolkits.jetbrains.services.cloudformation.CfnTransformNode

class JsonCfnParser private constructor() {
    private val problems = mutableListOf<CfnProblem>()
    private val nodeMap = BidirectionalMap<CfnNode, PsiElement>()

    fun handleRoot(root: JsonObject): CfnRootNode {
        val translatedSections = root.propertyList.mapNotNull { translateSection(it) }

        return CfnRootNode(
            translatedSections.findSection<CfnMetadataNode>(),
            translatedSections.findSection<CfnTransformNode>(),
            translatedSections.findSection<CfnParametersNode>(),
            translatedSections.findSection<CfnMappingsNode>(),
            translatedSections.findSection<CfnConditionsNode>(),
            translatedSections.findSection<CfnResourcesNode>(),
            translatedSections.findSection<CfnGlobalsNode>(),
            translatedSections.findSection<CfnOutputsNode>()
        )
    }

    private inline fun <reified T> Collection<CfnNode>.findSection(): T? = this.firstOrNull { it is T } as T?

    private fun translateSection(property: JsonProperty): CfnNode? {
        val name = property.name
        val value = property.value

        if (name.isEmpty() || value == null) {
            return null
        }

        return when (CfnSection.fromName(name)) {
            CfnSection.FormatVersion -> handleFormatVersion(value)
            CfnSection.Transform -> handleTransform(property)
            CfnSection.Parameters -> handleParameters(property)
            CfnSection.Resources -> handleResources(property)
            CfnSection.Conditions -> handleConditions(property)
            CfnSection.Metadata -> handleMetadata(property)
            CfnSection.Outputs -> handleOutputs(property)
            CfnSection.Mappings -> handleMappings(property)
            else -> {
                null
            }
        }
    }

    private fun handleFormatVersion(value: JsonValue): CfnNode? {
        val version = checkAndGetStringElement(value)?.value ?: return null

        if (version !in SUPPORTED_VERSIONS) {
            // TODO
        }

        // We do not need the version ever again so don't return any node for it
        return null
    }

    private fun handleTransform(property: JsonProperty): CfnNode? {
        val values = checkAndGetStringOrStringArray(property)
        return CfnTransformNode(keyName(property), values)
    }

    private fun handleParameters(parameters: JsonProperty): CfnParametersNode = parseNameValues(
        parameters,
        { parameter -> handleParameter(parameter) },
        { nameNode, list -> CfnParametersNode(nameNode, list).registerNode(parameters) }
    )

    private fun handleParameter(parameter: JsonProperty): CfnParameterNode = parseNameValues(
        parameter,
        { node -> CfnNameValueNode(keyName(node), node.value?.let { handleExpression(it, allowFunctions = false) }) },
        { nameNode, list -> CfnParameterNode(nameNode, list).registerNode(parameter) }
    )

    private fun handleResources(resources: JsonProperty): CfnResourcesNode = parseNameValues(
        resources,
        { resource -> handleResource(resource) },
        { nameNode, list -> CfnResourcesNode(nameNode, list) }
    )

    private fun handleResource(resourceProperty: JsonProperty): CfnResourceNode {
        val key = keyName(resourceProperty)

        val obj = checkAndGetObject(resourceProperty.value) ?: return CfnResourceNode(key, null, null, null, null, emptyMap()).registerNode(resourceProperty)

        val topLevelProperties = mutableMapOf<String, CfnNamedNode>()

        for (property in obj.propertyList) {
            val propertyName = property.name

            if (!TOP_LEVEL_RESOURCE_PROPERTIES.contains(propertyName)) {
//                addProblemOnNameElement(property, message("format.unknown.resource.property", propertyName))
            }

            val node = when (propertyName) {
                DEPENDS_ON -> handleResourceDependsOn(property)
                TYPE -> handleResourceType(property)
                CONDITION -> handleResourceCondition(property)
                PROPERTIES -> handleResourceProperties(property)
                else -> {
                    CfnNameValueNode(keyName(property), property.value?.let { handleExpression(it, true) }).registerNode(property)
                }
            }

            topLevelProperties[propertyName] = node
        }

        val properties = topLevelProperties.values
        return CfnResourceNode(
            key,
            properties.findSection<CfnResourceTypeNode>(),
            properties.findSection<CfnResourcePropertiesNode>(),
            properties.findSection<CfnResourceConditionNode>(),
            properties.findSection<CfnResourceDependsOnNode>(),
            topLevelProperties
        ).registerNode(resourceProperty)
    }

    private fun handleResourceDependsOn(property: JsonProperty): CfnResourceDependsOnNode {
        val values = checkAndGetStringOrStringArray(property)
        return CfnResourceDependsOnNode(keyName(property), values).registerNode(property)
    }

    private fun handleResourceType(typeProperty: JsonProperty): CfnResourceTypeNode = CfnResourceTypeNode(
        keyName(typeProperty),
        checkAndGetStringElement(typeProperty.value)
    ).registerNode(typeProperty)

    private fun handleResourceCondition(property: JsonProperty): CfnResourceConditionNode =
        CfnResourceConditionNode(keyName(property), checkAndGetStringElement(property.value)).registerNode(property)

    private fun handleResourceProperties(propertiesProperty: JsonProperty): CfnResourcePropertiesNode {
        val value = checkAndGetObject(propertiesProperty.value)
            ?: return CfnResourcePropertiesNode(keyName(propertiesProperty), emptyList()).registerNode(propertiesProperty)

        val propertyNodes = value.propertyList.mapNotNull { property ->
            CfnResourcePropertyNode(keyName(property), property.value?.let { handleExpression(it, allowFunctions = true) }).registerNode(property)
        }

        return CfnResourcePropertiesNode(keyName(propertiesProperty), propertyNodes).registerNode(propertiesProperty)
    }

    private fun handleConditions(conditions: JsonProperty): CfnConditionsNode = parseNameValues(
        conditions,
        { node -> CfnConditionNode(keyName(node), handleExpression(node.value!!, allowFunctions = true)).registerNode(node) },
        { nameNode, list -> CfnConditionsNode(nameNode, list) }
    )

    private fun handleMetadata(metadata: JsonProperty): CfnMetadataNode {
        val valueNode = checkAndGetObject(metadata.value)
        return CfnMetadataNode(keyName(metadata), valueNode?.let { handleExpression(valueNode, allowFunctions = false) } as? CfnObjectValueNode).registerNode(
            metadata
        )
    }

    private fun handleOutputs(outputs: JsonProperty): CfnOutputsNode = parseNameValues(
        outputs,
        { output -> CfnOutputNode(keyName(output), handleExpression(output.value!!, allowFunctions = true)).registerNode(output) },
        { nameNode, list -> CfnOutputsNode(nameNode, list) }
    )

    private fun handleMappings(mappings: JsonProperty): CfnMappingsNode = parseNameValues(
        mappings,
        { mapping -> handleFirstLevelMapping(mapping) },
        { nameNode, list -> CfnMappingsNode(nameNode, list) }
    )

    private fun handleFirstLevelMapping(mapping: JsonProperty): CfnFirstLevelMappingNode = parseNameValues(
        mapping,
        { secondLevel -> handleSecondLevelMapping(secondLevel) },
        { nameNode, list -> CfnFirstLevelMappingNode(nameNode, list) }
    )

    private fun handleSecondLevelMapping(mapping: JsonProperty): CfnSecondLevelMappingNode = parseNameValues(
        mapping,
        { node -> CfnMappingValue(keyName(node), node.value?.let { handleExpression(it, allowFunctions = false) }).registerNode(node) },
        { nameNode, list -> CfnSecondLevelMappingNode(nameNode, list) }
    )

    private fun handleExpression(value: JsonValue, allowFunctions: Boolean): CfnExpressionNode? = when (value) {
        is JsonStringLiteral -> CfnScalarValueNode(value.value).registerNode(value)
        is JsonBooleanLiteral -> CfnScalarValueNode(value.text).registerNode(value)
        is JsonNumberLiteral -> CfnScalarValueNode(value.text).registerNode(value)
        is JsonArray -> {
            val items = value.valueList.mapNotNull { handleExpression(it, allowFunctions) }
            CfnArrayValueNode(items).registerNode(value)
        }
        is JsonReferenceExpression -> {
//                addProblem(value, "Expected an expression")
            CfnScalarValueNode(value.identifier.text).registerNode(value)
        }
        is JsonObject -> {
            if (allowFunctions &&
                value.propertyList.size == 1 &&
                CfnIntrinsicFunction.FULL_NAMES.contains(value.propertyList.first().name)
            ) {
                val single = value.propertyList.single()
                val nameNode = CfnScalarValueNode(single.name).registerNode(single.nameElement)
                val functionId = CfnIntrinsicFunction.FULL_NAMES[single.name]!!

                when (val jsonValueNode = single.value) {
                    is JsonArray -> {
                        val items = jsonValueNode.valueList.mapNotNull { handleExpression(it, allowFunctions) }
                        CfnFunctionNode(nameNode, functionId, items).registerNode(value)
                    }
                    null -> CfnFunctionNode(nameNode, functionId, emptyList()).registerNode(value)
                    else -> CfnFunctionNode(nameNode, functionId, listOfNotNull(handleExpression(jsonValueNode, allowFunctions))).registerNode(value)
                }
            } else {
                val properties = value.propertyList.map {
                    val nameNode = CfnScalarValueNode(it.name).registerNode(it.nameElement)

                    val jsonValueNode = it.value
                    val valueNode = if (jsonValueNode == null) null else {
                        handleExpression(jsonValueNode, allowFunctions)
                    }

                    CfnNameValueNode(nameNode, valueNode).registerNode(it)
                }

                CfnObjectValueNode(properties).registerNode(value)
            }
        }
        else -> {
//                addProblem(value, message("format.unknown.value", value.javaClass.simpleName))
            null
        }
    }

    private fun <ResultNodeType : CfnNode, ValueNodeType : CfnNode> parseNameValues(
        property: JsonProperty,
        valueFactory: (JsonProperty) -> ValueNodeType,
        resultFactory: (CfnScalarValueNode?, List<ValueNodeType>) -> ResultNodeType
    ): ResultNodeType {
        val nameNode = keyName(property)

        val obj = checkAndGetObject(property.value!!) ?: return resultFactory(nameNode, emptyList()).registerNode(property)

        val list = obj.propertyList.mapNotNull { value ->
            if (value.value == null) {
//                addProblemOnNameElement(value, "A value is expected")
                return@mapNotNull null
            }

            return@mapNotNull valueFactory(value)
        }

        return resultFactory(nameNode, list).registerNode(property)
    }

    private fun checkAndGetStringElement(value: JsonValue?): CfnScalarValueNode? = when (value) {
        null -> null
        is JsonStringLiteral -> CfnScalarValueNode(value.value)
        is JsonReferenceExpression -> {
//                addProblem(expression, message("cloudformation.template.expected_quoted_string"))
            CfnScalarValueNode(value.identifier.text)
        }
        else -> {
//                addProblem(expression, message("cloudformation.template.expected_quoted_string"))
            null
        }
    }

    private fun checkAndGetStringOrStringArray(property: JsonProperty?): List<CfnScalarValueNode> = when (val value = property?.value) {
        null -> emptyList()
        is JsonArray -> value.valueList.asSequence()
            .mapNotNull { checkAndGetStringElement(it) }
            .toList()
        is JsonStringLiteral -> listOf(CfnScalarValueNode(value.value))
        is JsonReferenceExpression -> {
//                addProblemOnNameElement(property,  "Expected a string or an array of strings")
            listOf(CfnScalarValueNode(value.text))
        }
        else -> {
//                addProblemOnNameElement(property,  "Expected a string or an array of strings")
            emptyList()
        }
    }

    private fun checkAndGetObject(expression: JsonValue?): JsonObject? {
        val obj = expression as? JsonObject
        if (obj == null) {
//            addProblem(
//                expression,
//                CloudFormationBundle.message("format.expected.json.object"))

            return null
        }

        return obj
    }

    private fun keyName(property: JsonProperty): CfnScalarValueNode = CfnScalarValueNode(property.name)

    private fun <T : CfnNode> T.registerNode(psiElement: PsiElement): T {
        assert(!nodeMap.containsKey(this)) { "Nodes map already has $psiElement" }

        nodeMap[this] = psiElement

        return this
    }

    private fun PsiElement.addProblem(description: String) {
        problems.add(CfnProblem(this, description))
    }

    private fun JsonProperty.addProblemOnNameElement(description: String) {
        this.nameElement.addProblem(description)
    }

    companion object {
        private val SUPPORTED_VERSIONS = setOf("2010-09-09")

        private const val CONDITION = "Condition"
        private const val TYPE = "Type"
        private const val PROPERTIES = "Properties"
        private const val CREATION_POLICY = "CreationPolicy"
        private const val DELETION_POLICY = "DeletionPolicy"
        private const val DESCRIPTION = "Description"
        private const val DEPENDS_ON = "DependsOn"
        private const val METADATA = "Metadata"
        private const val UPDATE_POLICY = "UpdatePolicy"
        private const val UPDATE_REPLACE_POLICY = "UpdateReplacePolicy"
        private const val VERSION = "Version"

        val TOP_LEVEL_RESOURCE_PROPERTIES = setOf(
            CONDITION,
            TYPE,
            PROPERTIES,
            CREATION_POLICY,
            DELETION_POLICY,
            DESCRIPTION,
            DEPENDS_ON,
            METADATA,
            UPDATE_POLICY,
            UPDATE_REPLACE_POLICY,
            VERSION
        )

        fun parse(jsonObject: JsonObject): CfnParsedFile? {
            val cfnParser = JsonCfnParser()

            val rootNode = cfnParser.handleRoot(jsonObject)

            return CfnParsedFile(rootNode, cfnParser.problems)
        }
    }
}
