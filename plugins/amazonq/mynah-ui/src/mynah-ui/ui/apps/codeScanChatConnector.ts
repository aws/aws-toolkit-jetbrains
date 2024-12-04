/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import {ExtensionMessage} from "../commands";
import {ChatItem, ChatItemType, ProgressField} from "@aws/mynah-ui-chat";
import {FormButtonIds} from "../forms/constants";

export interface ICodeScanChatConnectorProps {
    sendMessageToExtension: (message: ExtensionMessage) => void
    onCodeScanMessageReceived: (tabID: string, message: ChatItem, isLoading: boolean, clearPreviousItemButtons?: boolean, runReview?: boolean) => void
    onUpdatePlaceholder: (tabID: string, newPlaceholder: string) => void,
    onUpdatePromptProgress: (tabID: string, progressField: ProgressField | null | undefined) => void
    onChatInputEnabled: (tabID: string, enabled: boolean) => void
}

export class CodeScanChatConnector {
    private readonly sendMessageToExtension
    private readonly onCodeScanMessageReceived
    private readonly updatePlaceholder
    private readonly updatePromptProgress
    private readonly chatInputEnabled

    constructor(props: ICodeScanChatConnectorProps) {
        this.sendMessageToExtension = props.sendMessageToExtension
        this.onCodeScanMessageReceived = props.onCodeScanMessageReceived
        this.updatePlaceholder = props.onUpdatePlaceholder
        this.updatePromptProgress = props.onUpdatePromptProgress
        this.chatInputEnabled = props.onChatInputEnabled
    }

    private processChatMessage = (messageData: any): void => {
        const runReview = messageData.command === "review"
        if (this.onCodeScanMessageReceived === undefined) {
            return
        }

        const tabID = messageData.tabID
        const isAddingNewItem: boolean = messageData.isAddingNewItem
        const isLoading: boolean = messageData.isLoading
        const clearPreviousItemButtons: boolean = messageData.clearPreviousItemButtons
        const type = messageData.messageType

        if (isAddingNewItem && type === ChatItemType.ANSWER_PART) {
            this.onCodeScanMessageReceived(tabID, {
                type: ChatItemType.ANSWER_STREAM,
            }, isLoading)
        }

        const chatItem: ChatItem = {
            type: type,
            body: messageData.message ?? undefined,
            messageId: messageData.messageId ?? messageData.triggerID ?? '',
            relatedContent: undefined,
            canBeVoted: messageData.canBeVoted ?? true,
            formItems: messageData.formItems,
            buttons:
                messageData.buttons !== undefined && messageData.buttons.length > 0 ? messageData.buttons : undefined,
            followUp:
                messageData.followUps !== undefined && messageData.followUps.length > 0
                    ? {
                          text: '',
                          options: messageData.followUps,
                      }
                    : undefined
        }
        this.onCodeScanMessageReceived(tabID, chatItem, isLoading, clearPreviousItemButtons, runReview)
    }

    handleMessageReceive = async (messageData: any): Promise<void> => {
        if (messageData.type === 'chatMessage') {
            this.processChatMessage(messageData)
            return
        }
        if (messageData.type === 'updatePlaceholderMessage') {
            this.updatePlaceholder(messageData.tabID, messageData.newPlaceholder)
            return
        }

        if(messageData.type === 'updatePromptProgress') {
            this.updatePromptProgress(messageData.tabID, messageData.progressField)
        }

        if(messageData.type === 'chatInputEnabledMessage') {
            this.chatInputEnabled(messageData.tabID, messageData.enabled)
        }
    }

    onFormButtonClick = (
        tabID: string,
        action: {
            id: string
            text?: string
            formItemValues?: Record<string, string>
        }
    ) => {
        if (action.id === FormButtonIds.CodeScanStartProjectScan) {
            this.sendMessageToExtension({
                command: 'codescan_start_project_scan',
                tabID,
                tabType: 'codescan',
            })
        } else if (action.id === FormButtonIds.CodeScanStartFileScan) {
            this.sendMessageToExtension({
                command: 'codescan_start_file_scan',
                tabID,
                tabType: 'codescan'
            })
        } else if (action.id === FormButtonIds.CodeScanStopFileScan) {
            this.sendMessageToExtension({
                command: 'codescan_stop_file_scan',
                tabID,
                tabType: 'codescan'
            })
        } else if (action.id === FormButtonIds.CodeScanStopProjectScan) {
            this.sendMessageToExtension({
                command: 'codescan_stop_project_scan',
                tabID,
                tabType: 'codescan'
            })
        } else if (action.id === FormButtonIds.CodeScanOpenIssues) {
            this.sendMessageToExtension({
                command: 'codescan_open_issues',
                tabID,
                tabType: 'codescan'
            })
        }
    }
    onResponseBodyLinkClick(tabID: string, messageId: string, link: string) {
        this.sendMessageToExtension({
            command: 'response-body-link-click',
            tabID,
            messageId,
            link,
            tabType: 'codescan',
        })
    }

    clearChat = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'clear',
            chatMessage: '',
            tabType: 'codescan',
        })
    }

    help = (tabID: string): void => {
        console.log("reached here")
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'help',
            chatMessage: '',
            tabType: 'codescan',
        })
    }

    onTabOpen = (tabID: string) => {
        this.sendMessageToExtension({
            tabID,
            command: 'new-tab-was-created',
            tabType: 'codescan'
        })
    }

    onTabRemove = (tabID: string) => {
        this.sendMessageToExtension({
            tabID,
            command: 'tab-was-removed',
            tabType: 'codescan'
        })
    }

    scan = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'scan',
            chatMessage: 'scan',
            tabType: 'codescan'
        })
    }
}
