// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * FileChooserCompat implementation for IntelliJ Platform versions 2024.3+ (baseline >= 243).
 * Uses withExtensionFilter method which provides both filtering functionality and visual
 * filtering in the chooser dialog.
 */
internal class FileChooserCompatImpl : FileChooserCompat {
    override fun applyExtensionFilter(
        descriptor: FileChooserDescriptor,
        filterName: String,
        allowedExtensions: Set<String>,
    ): FileChooserDescriptor = descriptor.withExtensionFilter(filterName, *allowedExtensions.toTypedArray())
}
