// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

// TODO: include custom yaml file path in manifest.json?
data class ZipManifest(
    val sourcesRoot: String = ZIP_SOURCES_PATH,
    val dependenciesRoot: String = ZIP_DEPENDENCIES_PATH,
    val buildLogs: String = BUILD_LOG_PATH,
    val version: String = UPLOAD_ZIP_MANIFEST_VERSION,
    val hilCapabilities: List<String> = listOf(HIL_1P_UPGRADE_CAPABILITY),
    // TODO: add CLIENT_SIDE_BUILD to transformCapabilities when releasing CSB
    val transformCapabilities: List<String> = listOf(EXPLAINABILITY_V1),
    val customBuildCommand: String = MAVEN_BUILD_RUN_UNIT_TESTS,
    val requestedConversions: RequestedConversions? = null, // only used for SQL conversions for now
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
