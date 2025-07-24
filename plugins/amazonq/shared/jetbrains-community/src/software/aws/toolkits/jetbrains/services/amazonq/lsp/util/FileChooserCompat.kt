// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * Compatibility interface for handling file chooser extension filtering across different IntelliJ Platform versions.
 *
 * The withExtensionFilter method was introduced in IntelliJ Platform 2024.3 (baseline version 243).
 * For older versions, fall back to withFileFilter which provides similar functionality
 */
interface FileChooserCompat {
    /**
     * Applies file extension filtering to the given FileChooserDescriptor.
     *
     * @param descriptor The FileChooserDescriptor to apply filtering to
     * @param filterName The display name for the filter (e.g., "Images")
     * @param allowedExtensions Set of allowed file extensions (e.g., "jpg", "png")
     * @return The modified FileChooserDescriptor
     */
    fun applyExtensionFilter(
        descriptor: FileChooserDescriptor,
        filterName: String,
        allowedExtensions: Set<String>,
    ): FileChooserDescriptor

    companion object {
        fun getInstance(): FileChooserCompat = FileChooserCompatImpl()
    }
}
