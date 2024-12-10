// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import com.fasterxml.jackson.annotation.JsonProperty
import software.aws.toolkits.jetbrains.common.session.SessionState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Interaction

data class SessionStateInteraction<T : SessionState>(
    val nextState: T? = null,
    val interaction: Interaction,
)

data class DocGenerationStreamResult(
    @JsonProperty("new_file_contents")
    var newFileContents: Map<String, String>,
    @JsonProperty("deleted_files")
    var deletedFiles: List<String>?,
    var references: List<CodeReferenceGenerated>,
)

data class ExportDocTaskAssistResultArchiveStreamResult(
    @JsonProperty("code_generation_result")
    var codeGenerationResult: DocGenerationStreamResult,
)
