// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cdklsp.server

import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler

/**
 * Minimal LSP client. The `cdk lsp` server advertises hover, definition, and
 * diagnostics (auto-wired by the platform LSP API) plus codeLens. The platform
 * API does not surface codeLens, so the open-resource lens is handled by
 * CdkCodeLensProvider (fetches lenses from this server, navigates on click). No
 * custom server->client notifications are needed today.
 */
internal class CdkLspClient(
    handler: LspServerNotificationsHandler,
) : Lsp4jClient(handler)
