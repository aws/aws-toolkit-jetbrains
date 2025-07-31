// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * Applies file extension filtering to the given FileChooserDescriptor for IntelliJ Platform versions 2024.3+ (baseline >= 243).
 * Uses withExtensionFilter method which provides both filtering functionality and visual
 * filtering in the chooser dialog.
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
): FileChooserDescriptor = descriptor.withExtensionFilter(filterName, *allowedExtensions.toTypedArray())
