// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.TextDocumentIdentifier
import kotlin.io.path.Path

fun getTextDocumentIdentifier(project: Project): TextDocumentIdentifier {
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
    val filePath = Path(selectedEditor?.file?.path.orEmpty()).toUri()
    return TextDocumentIdentifier(filePath.toString())
}
