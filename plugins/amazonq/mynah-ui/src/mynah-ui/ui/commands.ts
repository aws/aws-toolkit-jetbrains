/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import { QuickActionCommandGroup } from "@aws/mynah-ui-chat"

type MessageCommand =
    | 'chat-prompt'
    | 'trigger-message-processed'
    | 'new-tab-was-created'
    | 'tab-was-added'
    | 'tab-was-removed'
    | 'tab-was-changed'
    | 'ui-is-ready'
    | 'disclaimer-acknowledged'
    | 'ui-focus'
    | 'follow-up-was-clicked'
    | 'auth-follow-up-was-clicked'
    | 'open-diff'
    | 'code_was_copied_to_clipboard'
    | 'insert_code_at_cursor_position'
    | 'stop-response'
    | 'trigger-tabID-received'
    | 'clear'
    | 'help'
    | 'chat-item-voted'
    | 'chat-item-feedback'
    | 'link-was-clicked'
    | 'onboarding-page-interaction'
    | 'source-link-click'
    | 'response-body-link-click'
    | 'transform'
    | 'footer-info-link-click'
    | 'codetransform-start'
    | 'codetransform-select-sql-metadata'
    | 'codetransform-select-sql-module-schema'
    | 'codetransform-cancel'
    | 'codetransform-stop'
    | 'codetransform-continue'
    | 'codetransform-confirm-skip-tests'
    | 'codetransform-confirm-one-or-multiple-diffs'
    | 'codetransform-input-confirm-custom-dependency-versions'
    | 'codetransform-new'
    | 'codetransform-open-transform-hub'
    | 'codetransform-open-mvn-build'
    | 'codetransform-view-diff'
    | 'codetransform-view-summary'
    | 'codetransform-view-build-log'
    | 'codetransform-confirm-hil-selection'
    | 'codetransform-reject-hil-selection'
    | 'codetransform-pom-file-open-click'
    | 'file-click'
    | 'open-settings'
    | 'button-click'
    | 'store-code-result-message-id'
    | 'folderConfirmationMessage'
    | 'scan'
    | 'codescan_start_project_scan'
    | 'codescan_start_file_scan'
    | 'codescan_stop_project_scan'
    | 'codescan_stop_file_scan'
    | 'codescan_open_issues'
    | 'generate-test'
    | 'start-test-gen'
    | 'open-user-guide'
    | 'send-telemetry'
    | 'doc_stop_generate'
    | 'updatePromptProgress'

export type ExtensionMessage = Record<string, any> & { command: MessageCommand }

export const workspaceCommand: QuickActionCommandGroup = {
    groupName: 'Mention code',
    commands: [
        {
            command: '@workspace',
            description: 'Reference all code in workspace.',
        },
    ],
}
