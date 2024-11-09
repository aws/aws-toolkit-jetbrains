// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "tree")
data class SctMetadata(
    @JsonProperty("instances")
    val instances: Instances
)

data class Instances(
    @JsonProperty("ProjectModel")
    val projectModel: ProjectModel
)

data class ProjectModel(
    @JsonProperty("entities")
    val entities: Entities,
    @JsonProperty("relations")
    val relations: Relations,
)

data class Entities(
    @JsonProperty("sources")
    val sources: Sources,
    @JsonProperty("targets")
    val targets: Targets,
)

data class Sources(
    @JsonProperty("DbServer")
    val dbServer: DbServer,
)

data class Targets(
    @JsonProperty("DbServer")
    val dbServer: DbServer,
)

data class DbServer(
    @JsonProperty("vendor")
    val vendor: String,
    @JsonProperty("name")
    val name: String,
)

data class Relations(
    @JsonProperty("server-node-location")
    @JacksonXmlElementWrapper(useWrapping = false)
    val serverNodeLocation: List<ServerNodeLocation>,
)

data class ServerNodeLocation(
    @JsonProperty("FullNameNodeInfoList")
    val fullNameNodeInfoList: FullNameNodeInfoList,
)

data class FullNameNodeInfoList(
    @JsonProperty("nameParts")
    val nameParts: NameParts,
)

data class NameParts(
    @JsonProperty("FullNameNodeInfo")
    @JacksonXmlElementWrapper(useWrapping = false)
    val fullNameNodeInfo: List<FullNameNodeInfo>,
)

data class FullNameNodeInfo(
    @JsonProperty("typeNode")
    val typeNode: String,
    @JsonProperty("nameNode")
    val nameNode: String,
)
