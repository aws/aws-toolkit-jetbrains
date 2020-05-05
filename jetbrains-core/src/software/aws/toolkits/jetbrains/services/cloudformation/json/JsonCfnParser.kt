// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonElement
import com.intellij.json.psi.JsonNumberLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
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
import software.aws.toolkits.resources.message

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
            CfnSection.Description -> handleDescription(value)
            CfnSection.Parameters -> handleParameters(property)
            CfnSection.Resources -> handleResources(property)
            CfnSection.Conditions -> handleConditions(property)
            CfnSection.Metadata -> handleMetadata(property)
            CfnSection.Outputs -> handleOutputs(property)
            CfnSection.Mappings -> handleMappings(property)
            else -> {
                property.addProblemOnNameElement(message("cloudformation.template.unknown_property", name))

                null
            }
        }
    }

    private fun handleFormatVersion(value: JsonValue): CfnNode? {
        val version = checkAndGetStringElement(value)?.value ?: return null

        if (version !in SUPPORTED_VERSIONS) {
            val supportedVersions = SUPPORTED_VERSIONS.joinToString()
            value.addProblem(message("cloudformation.template.unknown_version", supportedVersions))
        }

        return CfnScalarValueNode(version).registerNode(value)
    }

    private fun handleTransform(property: JsonProperty): CfnNode? {
        val values = checkAndGetStringOrStringArray(property)
        return CfnTransformNode(property.cfnKey(), values)
    }

    private fun handleDescription(value: JsonValue): CfnNode? {
        val description = checkAndGetStringElement(value)?.value ?: return null

        return CfnScalarValueNode(description).registerNode(value)
    }

    private fun handleParameters(parameters: JsonProperty): CfnParametersNode = parseNameValues(
        parameters,
        { parameter -> handleParameter(parameter) },
        { nameNode, list -> CfnParametersNode(nameNode, list) }
    )

    private fun handleParameter(parameter: JsonProperty): CfnParameterNode = parseNameValues(
        parameter,
        { node -> CfnNameValueNode(node.cfnKey(), handleExpression(node, allowFunctions = false)).registerNode(node) },
        { nameNode, list -> CfnParameterNode(nameNode, list) }
    )

    private fun handleResources(resources: JsonProperty): CfnResourcesNode = parseNameValues(
        resources,
        { resource -> handleResource(resource) },
        { nameNode, list -> CfnResourcesNode(nameNode, list) }
    )

    private fun handleResource(resourceProperty: JsonProperty): CfnResourceNode {
        val key = resourceProperty.cfnKey()

        val obj = checkAndGetObject(resourceProperty) ?: return CfnResourceNode(key, null, null, null, null, emptyMap()).registerNode(resourceProperty)

        val topLevelProperties = mutableMapOf<String, CfnNamedNode>()

        for (property in obj.propertyList) {
            val propertyName = property.name

            if (!TOP_LEVEL_RESOURCE_PROPERTIES.contains(propertyName)) {
                property.addProblemOnNameElement(message("cloudformation.template.unknown_property", propertyName))
            }

            val node = when (propertyName) {
                DEPENDS_ON -> handleResourceDependsOn(property)
                TYPE -> handleResourceType(property)
                CONDITION -> handleResourceCondition(property)
                PROPERTIES -> handleResourceProperties(property)
                else -> {
                    CfnNameValueNode(property.cfnKey(), handleExpression(property, true)).registerNode(property)
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
        return CfnResourceDependsOnNode(property.cfnKey(), values).registerNode(property)
    }

    private fun handleResourceType(typeProperty: JsonProperty): CfnResourceTypeNode = CfnResourceTypeNode(
        typeProperty.cfnKey(),
        // Contract implies that if value is null return type is null, else nonnull
        checkAndGetStringElement(typeProperty.value)?.registerNode(typeProperty.value as PsiElement)
    ).registerNode(typeProperty)

    private fun handleResourceCondition(property: JsonProperty): CfnResourceConditionNode =
        CfnResourceConditionNode(property.cfnKey(), checkAndGetStringElement(property.value)).registerNode(property)

    private fun handleResourceProperties(propertiesProperty: JsonProperty): CfnResourcePropertiesNode {
        val value = checkAndGetObject(propertiesProperty)
            ?: return CfnResourcePropertiesNode(propertiesProperty.cfnKey(), emptyList()).registerNode(propertiesProperty)

        val propertyNodes = value.propertyList.mapNotNull { property ->
            CfnResourcePropertyNode(property.cfnKey(), handleExpression(property, allowFunctions = true)).registerNode(property)
        }

        return CfnResourcePropertiesNode(propertiesProperty.cfnKey(), propertyNodes).registerNode(propertiesProperty)
    }

    private fun handleConditions(conditions: JsonProperty): CfnConditionsNode = parseNameValues(
        conditions,
        { node -> CfnConditionNode(node.cfnKey(), handleExpression(node, allowFunctions = true)).registerNode(node) },
        { nameNode, list -> CfnConditionsNode(nameNode, list) }
    )

    private fun handleMetadata(metadata: JsonProperty): CfnMetadataNode {
        // Metadata must be an Object on the right
        val valueNode = checkAndGetObject(metadata)

        // If metadata is wrong type, a warning is already attached and null is returned, but if its actually null we want to let handleExpression deal with it
        return if (valueNode === metadata.value) {
            CfnMetadataNode(metadata.cfnKey(), handleExpression(metadata, valueNode, allowFunctions = false) as? CfnObjectValueNode).registerNode(metadata)
        } else {
            CfnMetadataNode(metadata.cfnKey(), null).registerNode(metadata)
        }
    }

    private fun handleOutputs(outputs: JsonProperty): CfnOutputsNode = parseNameValues(
        outputs,
        { output -> CfnOutputNode(output.cfnKey(), handleExpression(output, allowFunctions = true)).registerNode(output) },
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
        { node -> CfnMappingValue(node.cfnKey(), handleExpression(node, allowFunctions = false)).registerNode(node) },
        { nameNode, list -> CfnSecondLevelMappingNode(nameNode, list) }
    )

    private fun handleExpression(property: JsonProperty, allowFunctions: Boolean): CfnExpressionNode? =
        handleExpression(property, property.value, allowFunctions)

    private fun handleExpression(parent: JsonElement, value: JsonValue?, allowFunctions: Boolean): CfnExpressionNode? = when (value) {
        null -> {
            if (parent is JsonProperty) {
                parent.addProblemOnNameElement(message("cloudformation.template.expected_value"))
            } else {
                parent.addProblem(message("cloudformation.template.expected_value"))
            }
            null
        }
        is JsonStringLiteral -> CfnScalarValueNode(value.value).registerNode(value)
        is JsonBooleanLiteral -> CfnScalarValueNode(value.text).registerNode(value)
        is JsonNumberLiteral -> CfnScalarValueNode(value.text).registerNode(value)
        is JsonArray -> {
            val items = value.valueList.mapNotNull { handleExpression(value, it, allowFunctions) }
            CfnArrayValueNode(items).registerNode(value)
        }
        is JsonObject -> {
            val function = value.propertyList.singleOrNull()?.let {
                CfnIntrinsicFunction.FULL_NAMES[it.name]
            }

            if (allowFunctions && function != null) {
                val single = value.propertyList.single()
                val nameNode = single.cfnKey()

                when (val jsonValueNode = single.value) {
                    is JsonArray -> {
                        val items = jsonValueNode.valueList.mapNotNull { handleExpression(single, allowFunctions) }
                        CfnFunctionNode(nameNode, function, items).registerNode(value)
                    }
                    null -> CfnFunctionNode(nameNode, function, emptyList()).registerNode(value)
                    else -> CfnFunctionNode(nameNode, function, listOfNotNull(handleExpression(single, allowFunctions))).registerNode(value)
                }
            } else {
                val properties = value.propertyList.map {
                    val nameNode = CfnScalarValueNode(it.name).registerNode(it.nameElement)
                    val valueNode = handleExpression(it, it.value, allowFunctions)

                    CfnNameValueNode(nameNode, valueNode).registerNode(it)
                }

                CfnObjectValueNode(properties).registerNode(value)
            }
        }
        else -> {
            value.addProblem(message("cloudformation.template.unknown_type", value.javaClass.simpleName))
            null
        }
    }

    private fun <ResultNodeType : CfnNode, ValueNodeType : CfnNode> parseNameValues(
        property: JsonProperty,
        valueFactory: (JsonProperty) -> ValueNodeType,
        resultFactory: (CfnScalarValueNode?, List<ValueNodeType>) -> ResultNodeType
    ): ResultNodeType {
        val nameNode = property.cfnKey()

        val obj = checkAndGetObject(property) ?: return resultFactory(nameNode, emptyList()).registerNode(property)

        val list = obj.propertyList.mapNotNull { value ->
            if (value.value == null) {
                value.addProblemOnNameElement(message("cloudformation.template.expected_value"))
                return@mapNotNull null
            }

            return@mapNotNull valueFactory(value)
        }

        return resultFactory(nameNode, list).registerNode(property)
    }

    private fun checkAndGetStringElement(value: JsonValue?): CfnScalarValueNode? = when (value) {
        null -> null
        is JsonStringLiteral -> CfnScalarValueNode(value.value)
        else -> {
            value.addProblem(message("cloudformation.template.expected_quoted_string"))
            null
        }
    }

    private fun checkAndGetStringOrStringArray(property: JsonProperty?): List<CfnScalarValueNode> = when (val value = property?.value) {
        null -> emptyList()
        is JsonArray -> value.valueList.asSequence()
            .mapNotNull { checkAndGetStringElement(it)?.registerNode(value) }
            .toList()
        is JsonStringLiteral -> listOf(CfnScalarValueNode(value.value).registerNode(value))
        else -> {
            property.addProblemOnNameElement(message("cloudformation.template.expected_string_string_array"))
            emptyList()
        }
    }

    private fun checkAndGetObject(property: JsonProperty): JsonObject? {
        val obj = property.value as? JsonObject
        if (obj == null) {
            property.addProblemOnNameElement(message("cloudformation.template.expected_object"))

            return null
        }

        return obj
    }

    private fun JsonProperty.cfnKey(): CfnScalarValueNode = CfnScalarValueNode(name).registerNode(nameElement)

    private fun <T : CfnNode> T.registerNode(psiElement: PsiElement): T {
        assert(nodeMap.putIfAbsent(this, psiElement) == null) {
            "Nodes map already has ${psiElement.text}"
        }

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
