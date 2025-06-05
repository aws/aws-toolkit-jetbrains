// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

data class ZipManifest(
    val sourcesRoot: String = ZIP_SOURCES_PATH,
    val dependenciesRoot: String = ZIP_DEPENDENCIES_PATH,
    val version: String = UPLOAD_ZIP_MANIFEST_VERSION,
    val hilCapabilities: List<String> = listOf(HIL_1P_UPGRADE_CAPABILITY),
    // TODO: add AGENTIC_PLAN_V1 or something here AND in processCodeTransformSkipTests when backend allowlists everyone
    // TODO: is SELECTIVE_TRANSFORMATION_V2 needed below?
    val transformCapabilities: List<String> = listOf(EXPLAINABILITY_V1, CLIENT_SIDE_BUILD),
    val customBuildCommand: String = MAVEN_BUILD_RUN_UNIT_TESTS,
    val requestedConversions: RequestedConversions? = null, // only used for SQL conversions for now
    var dependencyUpgradeConfigFile: String? = null,
    // TODO: make sure the below 2 keys don't mess up SQL conversions when present
    val noInteractiveMode: Boolean = true,
    val compilationsJsonFile: String = COMPILATIONS_JSON_FILE
)

data class RequestedConversions(
    val sqlConversion: SQLConversion? = null,
)

data class SQLConversion(
    val source: String? = null,
    val target: String? = null,
    val schema: String? = null,
    val host: String? = null,
    val sctFileName: String? = null,
)
