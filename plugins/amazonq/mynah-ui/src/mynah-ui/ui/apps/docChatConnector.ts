/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItem, ChatItemAction, ChatItemType, FeedbackPayload, MynahIcons, ProgressField } from '@aws/mynah-ui-chat'
import { ExtensionMessage } from '../commands'
import { TabType, TabsStorage } from '../storages/tabsStorage'
import { CodeReference } from './amazonqCommonsConnector'
import { FollowUpGenerator } from '../followUps/generator'
import { getActions } from '../diffTree/actions'
import { DiffTreeFileInfo } from '../diffTree/types'

interface ChatPayload {
    chatMessage: string
}

export interface ConnectorProps {
    sendMessageToExtension: (message: ExtensionMessage) => void
    onMessageReceived?: (tabID: string, messageData: any, needToShowAPIDocsTab: boolean) => void
    onUpdatePromptProgress: (tabID: string, progressField: ProgressField) => void
    onAsyncEventProgress: (tabID: string, inProgress: boolean, message: string, cancelButtonWhenLoading?: boolean) => void
    onChatAnswerReceived?: (tabID: string, message: ChatItem) => void
    sendFeedback?: (tabId: string, feedbackPayload: FeedbackPayload) => void | undefined
    onError: (tabID: string, message: string, title: string) => void
    onWarning: (tabID: string, message: string, title: string) => void
    onUpdatePlaceholder: (tabID: string, newPlaceholder: string) => void
    onChatInputEnabled: (tabID: string, enabled: boolean) => void
    onUpdateAuthentication: (
        featureDevEnabled: boolean,
        codeTransformEnabled: boolean,
        docEnabled: boolean,
        codeScanEnabled: boolean,
        codeTestEnabled: boolean,
        authenticatingTabIDs: string[]
    ) => void
    onNewTab: (tabType: TabType) => void
    tabsStorage: TabsStorage
    onFileComponentUpdate: (
        tabID: string,
        filePaths: DiffTreeFileInfo[],
        deletedFiles: DiffTreeFileInfo[],
        messageId: string
    ) => void
}

export class Connector {
    private readonly sendMessageToExtension
    private readonly onError
    private readonly onWarning
    private readonly onChatAnswerReceived
    private readonly onUpdatePromptProgress
    private readonly onAsyncEventProgress
    private readonly updatePlaceholder
    private readonly chatInputEnabled
    private readonly onUpdateAuthentication
    private readonly followUpGenerator: FollowUpGenerator
    private readonly onNewTab
    private readonly onFileComponentUpdate

    constructor(props: ConnectorProps) {
        this.sendMessageToExtension = props.sendMessageToExtension
        this.onChatAnswerReceived = props.onChatAnswerReceived
        this.onWarning = props.onWarning
        this.onError = props.onError
        this.onUpdatePromptProgress = props.onUpdatePromptProgress
        this.onAsyncEventProgress = props.onAsyncEventProgress
        this.updatePlaceholder = props.onUpdatePlaceholder
        this.chatInputEnabled = props.onChatInputEnabled
        this.onUpdateAuthentication = props.onUpdateAuthentication
        this.followUpGenerator = new FollowUpGenerator()
        this.onNewTab = props.onNewTab
        this.onFileComponentUpdate = props.onFileComponentUpdate
    }

    onCodeInsertToCursorPosition = (
        tabID: string,
        code?: string,
        type?: 'selection' | 'block',
        codeReference?: CodeReference[]
    ): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            code,
            command: 'insert_code_at_cursor_position',
            codeReference,
            tabType: 'doc',
        })
    }

    onCopyCodeToClipboard = (
        tabID: string,
        code?: string,
        type?: 'selection' | 'block',
        codeReference?: CodeReference[]
    ): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            code,
            command: 'code_was_copied_to_clipboard',
            codeReference,
            tabType: 'doc',
        })
    }

    onOpenDiff = (tabID: string, filePath: string, deleted: boolean): void => {
        this.sendMessageToExtension({
            command: 'open-diff',
            tabID,
            filePath,
            deleted,
            tabType: 'doc',
        })
    }

    followUpClicked = (tabID: string, followUp: ChatItemAction): void => {
        this.sendMessageToExtension({
            command: 'follow-up-was-clicked',
            followUp,
            tabID,
            tabType: 'doc',
        })
    }

    requestGenerativeAIAnswer = (tabID: string, payload: ChatPayload): Promise<any> =>
        new Promise((resolve, reject) => {
            const message: ExtensionMessage = {
                tabID: tabID,
                command: 'chat-prompt',
                chatMessage: payload.chatMessage,
                tabType: 'doc',
            }
            this.sendMessageToExtension(message)
        })

    onFileActionClick = (tabID: string, messageId: string, filePath: string, actionName: string): void => {
        this.sendMessageToExtension({
            command: 'file-click',
            tabID,
            messageId,
            filePath,
            actionName,
            tabType: 'doc',
        })
    }

    private processFolderConfirmationMessage = async (messageData: any, folderPath: string): Promise<void> => {
        if (this.onChatAnswerReceived !== undefined) {
            const answer: ChatItem = {
                type: ChatItemType.ANSWER,
                body: messageData.message ?? undefined,
                messageId: messageData.messageID ?? messageData.triggerID ?? '',
                fileList: {
                    rootFolderTitle: undefined,
                    fileTreeTitle: '',
                    filePaths: [folderPath],
                    details: {
                        [folderPath]: {
                            icon: MynahIcons.FOLDER,
                            clickable: false,
                        },
                    },
                },
                followUp: {
                    text: '',
                    options: messageData.followUps,
                },
            }
            this.onChatAnswerReceived(messageData.tabID, answer)
        }
    }

    private processRetryChangeFolderMessage = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived !== undefined) {
            const answer: ChatItem = {
                type: ChatItemType.ANSWER,
                body: messageData.message ?? undefined,
                messageId: messageData.messageID ?? messageData.triggerID ?? '',
                followUp: {
                    text: '',
                    options: messageData.followUps,
                },
            }
            this.onChatAnswerReceived(messageData.tabID, answer)
        }
    }

    private processChatMessage = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived !== undefined) {
            const answer: ChatItem = {
                type: messageData.messageType,
                body: messageData.message ?? undefined,
                messageId: messageData.messageID ?? messageData.triggerID ?? '',
                relatedContent: undefined,
                canBeVoted: messageData.canBeVoted,
                snapToTop: messageData.snapToTop,
                followUp:
                    messageData.followUps !== undefined && messageData.followUps.length > 0
                        ? {
                              text:
                                  messageData.messageType === ChatItemType.SYSTEM_PROMPT
                                      ? ''
                                      : 'Please follow up with one of these',
                              options: messageData.followUps,
                          }
                        : undefined,
            }
            this.onChatAnswerReceived(messageData.tabID, answer)
        }
    }

    private processCodeResultMessage = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived !== undefined) {
            const answer: ChatItem = {
                type: ChatItemType.ANSWER,
                relatedContent: undefined,
                followUp: undefined,
                canBeVoted: false,
                codeReference: messageData.references,
                // TODO get the backend to store a message id in addition to conversationID
                messageId: messageData.messageID ?? messageData.triggerID ?? messageData.conversationID,
                fileList: {
                    fileTreeTitle: 'Documents ready',
                    rootFolderTitle: 'Generated documentation',
                    filePaths: (messageData.filePaths as DiffTreeFileInfo[]).map(path => path.zipFilePath),
                    deletedFiles: (messageData.deletedFiles as DiffTreeFileInfo[]).map(path => path.zipFilePath)
                },
                body: '',
            }
            this.onChatAnswerReceived(messageData.tabID, answer)
        }
    }

    private processAuthNeededException = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived === undefined) {
            return
        }

        this.onChatAnswerReceived(messageData.tabID, {
            type: ChatItemType.ANSWER,
            body: messageData.message,
            followUp: undefined,
            canBeVoted: false,
        })

        // this.onChatAnswerReceived(messageData.tabID, {
        //     type: ChatItemType.SYSTEM_PROMPT,
        //     body: undefined,
        //     followUp: this.followUpGenerator.generateAuthFollowUps('doc', messageData.authType),
        //     canBeVoted: false,
        // })

        return
    }

    handleMessageReceive = async (messageData: any): Promise<void> => {
        if (messageData.type === 'updateFileComponent') {
            this.onFileComponentUpdate(
                messageData.tabID,
                messageData.filePaths,
                messageData.deletedFiles,
                messageData.messageId
            )
            return
        }
        if (messageData.type === 'errorMessage') {
            this.onError(messageData.tabID, messageData.message, messageData.title)
            return
        }

        if (messageData.type === 'showInvalidTokenNotification') {
            this.onWarning(messageData.tabID, messageData.message, messageData.title)
            return
        }

        if (messageData.type === 'folderConfirmationMessage') {
            await this.processFolderConfirmationMessage(messageData, messageData.folderPath)
            return
        }

        if (messageData.type === 'retryChangeFolderMessage') {
            await this.processRetryChangeFolderMessage(messageData)
            return
        }

        if (messageData.type === 'chatMessage') {
            await this.processChatMessage(messageData)
            return
        }

        if (messageData.type === 'codeResultMessage') {
            await this.processCodeResultMessage(messageData)
            return
        }

        if (messageData.type === 'asyncEventProgressMessage') {
            this.onAsyncEventProgress(messageData.tabID, messageData.inProgress, messageData.message ?? undefined)
            return
        }

        if (messageData.type === 'updatePlaceholderMessage') {
            this.updatePlaceholder(messageData.tabID, messageData.newPlaceholder)
            return
        }

        if (messageData.type === 'chatInputEnabledMessage') {
            this.chatInputEnabled(messageData.tabID, messageData.enabled)
            return
        }

        if (messageData.type === 'authenticationUpdateMessage') {
            this.onUpdateAuthentication(
                messageData.featureDevEnabled,
                messageData.codeTransformEnabled,
                messageData.docEnabled,
                messageData.codeScanEnabled,
                messageData.codeTestEnabled,
                messageData.authenticatingTabIDs
            )
            return
        }

        if (messageData.type === 'authNeededException') {
            // this.processAuthNeededException(messageData)
            return
        }

        if (messageData.type === 'openNewTabMessage') {
            // this.onNewTab('doc')
            return
        }

        if (messageData.type === 'updatePromptProgress') {
            this.onUpdatePromptProgress(messageData.tabId, messageData.progressField)
        }
    }

    onStopChatResponse = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'stop-response',
        })
    }

    onTabOpen = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID,
            command: 'new-tab-was-created',
            tabType: 'doc',
        })
    }

    onTabRemove = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'tab-was-removed',
            tabType: 'doc',
        })
    }

    sendFeedback = (tabId: string, feedbackPayload: FeedbackPayload): void | undefined => {
        this.sendMessageToExtension({
            command: 'chat-item-feedback',
            ...feedbackPayload,
            tabType: 'doc',
            tabID: tabId,
        })
    }

    onChatItemVoted = (tabId: string, messageId: string, vote: string): void | undefined => {
        this.sendMessageToExtension({
            tabID: tabId,
            messageId: messageId,
            vote: vote,
            command: 'chat-item-voted',
            tabType: 'doc',
        })
    }

    onResponseBodyLinkClick = (tabID: string, messageId: string, link: string): void => {
        this.sendMessageToExtension({
            command: 'response-body-link-click',
            tabID,
            messageId,
            link,
            tabType: 'doc',
        })
    }

    sendFolderConfirmationMessage = (tabID: string, messageId: string): void => {
        this.sendMessageToExtension({
            command: 'folderConfirmationMessage',
            tabID,
            messageId,
            tabType: 'doc',
        })
    }

    onFormButtonClick = (
        tabID: string,
        action: {
            id: string
            text?: string
            formItemValues?: Record<string, string>
        }
    ) => {
        if (action.id === "doc_stop_generate") {
            this.sendMessageToExtension({
                command: 'doc_stop_generate',
                tabID,
                tabType: 'doc',
            })
        }
    }
}
