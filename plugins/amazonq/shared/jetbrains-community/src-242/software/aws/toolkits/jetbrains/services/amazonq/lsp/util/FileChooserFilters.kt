// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * Applies file extension filtering to the given FileChooserDescriptor for IntelliJ Platform versions before 2024.3 (baseline < 243).
 * Uses withFileFilter method which provides filtering functionality but doesn't visually filter
 * files in the chooser dialog.
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
): FileChooserDescriptor = descriptor.withFileFilter { virtualFile ->
    if (virtualFile.isDirectory) {
        true // Always allow directories for navigation
    } else {
        val extension = virtualFile.extension?.lowercase()
        extension != null && allowedExtensions.contains(extension)
    }
}
