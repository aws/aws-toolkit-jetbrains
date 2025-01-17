// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

interface SupplementalContextStrategy

enum class UtgStrategy : SupplementalContextStrategy {
    ByName,
    ByContent,
    Empty,
    ;

    override fun toString() = when (this) {
        ByName -> "byName"
        ByContent -> "byContent"
        Empty -> "empty"
    }
}

enum class CrossFileStrategy : SupplementalContextStrategy {
    OpenTabsBM25,
    Empty,
    ProjectContext,
    Codemap,
    ;

    override fun toString() = when (this) {
        OpenTabsBM25 -> "opentabs"
        Empty -> "empty"
        ProjectContext -> "projectContext"
        Codemap -> "codemap"
    }
}
