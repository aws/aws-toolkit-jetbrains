// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

data class ZipManifest(
    val sourcesRoot: String = ZIP_SOURCES_PATH,
    val dependenciesRoot: String = ZIP_DEPENDENCIES_PATH,
    val version: String = UPLOAD_ZIP_MANIFEST_VERSION,
    val hilCapabilities: List<String> = listOf(HIL_1P_UPGRADE_CAPABILITY),
    val transformCapabilities: List<String> = listOf(EXPLAINABILITY_V1, CLIENT_SIDE_BUILD, SELECTIVE_TRANSFORMATION_V2),
    val customBuildCommand: String = MAVEN_BUILD_RUN_UNIT_TESTS,
    val requestedConversions: RequestedConversions? = null, // only used for SQL conversions for now
    var dependencyUpgradeConfigFile: String? = null,
    val noInteractiveMode: Boolean = true,
    val compilationsJsonFile: String = COMPILATIONS_JSON_FILE,
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
