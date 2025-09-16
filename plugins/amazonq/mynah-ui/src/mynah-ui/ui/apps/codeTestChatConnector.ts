/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import {ExtensionMessage} from "../commands";
import {ChatPayload, ConnectorProps} from "../connector";
import {FormButtonIds} from "../forms/constants";
import {ChatItem, ChatItemAction, ChatItemType, FeedbackPayload, MynahIcons, MynahUIDataModel} from '@aws/mynah-ui-chat'
import {CodeReference} from "./amazonqCommonsConnector";

export interface ICodeTestChatConnectorProps {
    sendMessageToExtension: (message: ExtensionMessage) => void
    onChatAnswerReceived?: (tabID: string, message: ChatItem) => void
    sendFeedback?: (tabId: string, feedbackPayload: FeedbackPayload) => void | undefined
    onUpdateAuthentication: (
        featureDevEnabled: boolean,
        codeTransformEnabled: boolean,
        docEnabled: boolean,
        codeScanEnabled: boolean,
        codeTestEnabled: boolean,
        authenticatingTabIDs: string[]
    ) => void
    onRunTestMessageReceived?: (tabID: string, showRunTestMessage: boolean) => void
    onChatAnswerUpdated?: (tabID: string, message: ChatItem) => void
    onChatInputEnabled: (tabID: string, enabled: boolean) => void
    onUpdatePlaceholder: (tabID: string, newPlaceholder: string) => void
    onError: (tabID: string, message: string, title: string) => void
}

interface IntroductionCardContentType {
    title: string
    description: string
    icon: MynahIcons
    content: {
        body: string
    }
}

interface MessageData {
    message?: string
    messageType: ChatItemType
    messageId?: string
    triggerID?: string
    informationCard?: boolean
    canBeVoted: boolean
    filePath?: string
    tabID: string
}

function getIntroductionCardContent(): IntroductionCardContentType {
    const introductionBody = [
        "I can generate unit tests for the active file or open project in your IDE.",
        "\n\n",
        "I can do things like:\n",
        "- Add unit tests for highlighted functions\n",
        "- Generate tests for null and empty inputs\n",
        "\n\n",
        "To learn more, visit the [Amazon Q Developer User Guide](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/test-generation.html)."
    ].join("");

    return {
        title: "/test -  Unit test generation",
        description: "Generate unit tests for selected code",
        icon: MynahIcons.CHECK_LIST,
        content: {
            body: introductionBody
        }
    }
}

export class CodeTestChatConnector {
    private readonly sendMessageToExtension
    private readonly onChatAnswerReceived
    private readonly onChatAnswerUpdated
    private readonly onMessageReceived
    private readonly onUpdateAuthentication
    private readonly chatInputEnabled
    private readonly updatePlaceholder
    private readonly updatePromptProgress
    private readonly onError
    private readonly runTestMessageReceived

    constructor(props: ConnectorProps) {
        this.sendMessageToExtension = props.sendMessageToExtension
        this.onChatAnswerReceived = props.onChatAnswerReceived
        this.onChatAnswerUpdated = props.onChatAnswerUpdated
        this.runTestMessageReceived = props.onRunTestMessageReceived
        this.onMessageReceived = props.onMessageReceived
        this.onUpdateAuthentication = props.onUpdateAuthentication
        this.chatInputEnabled = props.onChatInputEnabled
        this.updatePlaceholder = props.onUpdatePlaceholder
        this.updatePromptProgress = props.onUpdatePromptProgress
        this.onError = props.onError
    }

    private addAnswer = (messageData: any): void => {
        console.log("message data in addAnswer:")
        console.log(messageData)
        if (this.onChatAnswerReceived === undefined) {
            return
        }
        if (messageData.command === 'test' && this.runTestMessageReceived) {
            this.runTestMessageReceived(messageData.tabID, true)
            return
        }
        const answer: ChatItem = {
            type: messageData.messageType,
            messageId: messageData.messageId ?? messageData.triggerID,
            body: messageData.message,
            relatedContent: undefined,
            snapToTop: messageData.snapToTop,
            canBeVoted: messageData.canBeVoted ?? false,
            followUp: messageData.followUps ? {
                text: '',
                options: messageData.followUps,
            } : undefined,
            buttons: messageData.buttons ?? undefined,
            fileList: messageData.fileList ? {
                rootFolderTitle: messageData.projectRootName,
                fileTreeTitle: 'READY FOR REVIEW',
                filePaths: messageData.fileList,
                details: {
                    [messageData.filePaths]: {
                        icon: MynahIcons.FILE,
                    },
                },
            } : undefined,
            codeBlockActions: {
                'insert-to-cursor': undefined
            },
            codeReference: messageData.codeReference?.length ? messageData.codeReference : undefined
        }

        this.onChatAnswerReceived(messageData.tabID, answer)
    }

    private updateAnswer = (messageData: any): void => {
        console.log("message data in updateAnswer:")
        console.log(messageData)
        if (this.onChatAnswerUpdated == undefined) {
            return
        }
        const answer: ChatItem = {
            type: messageData.messageType,
            messageId: messageData.messageId ?? messageData.triggerID,
            body: messageData.message,
            buttons: messageData.buttons ?? undefined,
            followUp: messageData.followUps ? {
                text: '',
                options: messageData.followUps,
            } : undefined,
            canBeVoted: messageData.canBeVoted ?? false,
            fileList: messageData.fileList ? {
                rootFolderTitle: messageData.projectRootName,
                fileTreeTitle: 'READY FOR REVIEW',
                filePaths: messageData.fileList,
                details: {
                    [messageData.fileList]: {
                        icon: MynahIcons.FILE,
                    },
                },
            } : undefined,
            footer: messageData.footer ? {
                fileList: {
                    rootFolderTitle: undefined,
                    fileTreeTitle: '',
                    filePaths: messageData.footer,
                    details: {
                        [messageData.footer]: {
                            icon: MynahIcons.FILE,
                        },
                    },
                },
            } : undefined,
            codeReference: messageData.codeReference?.length ? messageData.codeReference : undefined
        }
        this.onChatAnswerUpdated(messageData.tabID, answer)
    }

    private updateUI = (messageData: any): void => {
        if (!this.onMessageReceived) {
            return
        }

        const settings: MynahUIDataModel = {
            ...(messageData.loadingChat !== undefined ? { loadingChat: messageData.loadingChat } : {}),
            ...(messageData.cancelButtonWhenLoading !== undefined ? { cancelButtonWhenLoading: messageData.cancelButtonWhenLoading } : {}),
            ...(messageData.promptInputPlaceholder !== undefined ? { promptInputPlaceholder: messageData.promptInputPlaceholder } : {}),
            ...(messageData.promptInputProgress !== undefined ? { promptInputProgress: messageData.promptInputProgress } : {}),
            // ...(messageData.promptInputDisabledState !== undefined ? { promptInputDisabledState: messageData.promptInputDisabledState } : {}),
        }

        console.log("UI settings to be updated")
        console.log(settings)
        this.onMessageReceived(messageData.tabID, settings, false)
        this.chatInputEnabled(messageData.tabID, !messageData.promptInputDisabledState)
    }

    private processChatMessage = async (messageData: any): Promise<void> => {
        if (!this.onChatAnswerReceived) {
            return
        }
        if (messageData.message === undefined && !messageData.informationCard) {
            return
        }
        const answer: ChatItem = {
            type: messageData.messageType,
            messageId: messageData.messageId || messageData.triggerID,
            body: messageData.informationCard ? "" : messageData.message,
            canBeVoted: messageData.canBeVoted,
            informationCard: messageData.informationCard ? getIntroductionCardContent() : undefined,
            footer: messageData.filePath
                ? {
                    fileList: {
                        rootFolderTitle: undefined,
                        fileTreeTitle: '',
                        filePaths: [messageData.filePaths],
                    },
                }
                : undefined,
        }
        this.onChatAnswerReceived(messageData.tabID, answer)
    }

    private processAuthNeededException = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived === undefined) {
            return
        }
        this.onChatAnswerReceived(
            messageData.tabID,
            {
                type: ChatItemType.SYSTEM_PROMPT,
                body: messageData.message,
            }
        )
    }

    private processCodeResultMessage = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived !== undefined) {
            const answer: ChatItem = {
                type: ChatItemType.ANSWER,
                canBeVoted: true,
                messageId: messageData.uploadId,
                followUp: {
                    text: '',
                    options: messageData.followUps,
                },
                fileList: {
                    fileTreeTitle: 'READY FOR REVIEW',
                    rootFolderTitle: messageData.projectName,
                    filePaths: messageData.filePaths,
                },
                body: messageData.message,
            }
            this.onChatAnswerReceived(messageData.tabID, answer)
        }
    }


    private processChatAIPromptMessage = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerReceived === undefined) {
            return
        }

        if (messageData.message !== undefined) {
            const answer: ChatItem = {
                type: messageData.messageType,
                messageId: messageData.messageId ?? messageData.triggerID,
                body: messageData.message,
                relatedContent: undefined,
                snapToTop: messageData.snapToTop,
                canBeVoted: false,
            }

            this.onChatAnswerReceived(messageData.tabID, answer)
        }
    }

    private processChatSummaryMessage = async (messageData: any): Promise<void> => {
        if (this.onChatAnswerUpdated === undefined) {
            return
        }
        if (messageData.message !== undefined) {
            const answer: ChatItem = {
                type: messageData.messageType,
                messageId: messageData.messageId ?? messageData.triggerID,
                body: messageData.message,
                buttons: messageData.buttons ?? [],
                canBeVoted: true,
                footer: {
                    fileList: {
                        rootFolderTitle: undefined,
                        fileTreeTitle: '',
                        filePaths: [messageData.filePath],
                    },
                },
            }
            this.onChatAnswerUpdated(messageData.tabID, answer)
        }
    }

    handleMessageReceive = async (messageData: any): Promise<void> => {
        // TODO: Implement the logic to handle received messages for Unit Test generator chat
        switch(messageData.type){
            case 'authNeededException':
                await this.processAuthNeededException(messageData)
                break
            case 'authenticationUpdateMessage':
                this.onUpdateAuthentication(
                    messageData.featureDevEnabled,
                    messageData.codeTransformEnabled,
                    messageData.docEnabled,
                    messageData.codeScanEnabled,
                    messageData.codeTestEnabled,
                    messageData.authenticatingTabIDs
                )
                break
            case 'chatInputEnabledMessage':
                this.chatInputEnabled(messageData.tabID, messageData.enabled)
                break
            case 'updatePromptProgress':
                this.updatePromptProgress(messageData.tabID, messageData.progressField)
                break
            case 'chatMessage':
                await this.processChatMessage(messageData)
                break
            case 'addAnswer':
                this.addAnswer(messageData)
                break
            case 'updateAnswer':
                this.updateAnswer(messageData)
                break
            case 'chatAIPromptMessage':
                await this.processChatAIPromptMessage(messageData)
                break
            case 'chatSummaryMessage':
                await this.processChatSummaryMessage(messageData)
                break
            case 'updatePlaceholderMessage':
                this.updatePlaceholder(messageData.tabID, messageData.newPlaceholder)
                break
            case 'codeResultMessage':
                await this.processCodeResultMessage(messageData)
                break
            case 'errorMessage':
                this.onError(messageData.tabID, messageData.message, messageData.title)
                break
            case 'updateUI':
                this.updateUI(messageData)
                break
        }
    }

    onFormButtonClick = (
        tabID: string,
        messageId: string,
        action: {
            id: string
            text?: string
            formItemValues?: Record<string,string>
        }
    ) => {
        if (action === undefined) {
            return
        }

        this.sendMessageToExtension({
            command: 'button-click',
            actionID: action.id,
            formSelectedValues: action.formItemValues,
            tabType: 'codetest',
            tabID: tabID,
        })

        if (this.onChatAnswerUpdated === undefined) {
            return
        }

        const answer: ChatItem = {
            type: ChatItemType.ANSWER,
            messageId: messageId,
            buttons: []
        };

        switch (action.id) {
            case FormButtonIds.CodeTestViewDiff:
                // does nothing
                break
            case FormButtonIds.CodeTestAccept:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Accepted',
                        id: 'utg_accepted',
                        status: 'success',
                        position: 'outside',
                        disabled: true
                    }
                ];
                break;
            case FormButtonIds.CodeTestReject:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Rejected',
                        id: 'utg_rejected',
                        status: 'error',
                        position: 'outside',
                        disabled: true
                    }
                ];
                break;
            case FormButtonIds.CodeTestBuildAndExecute:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Build and execute',
                        id: 'utg_build_and_execute',
                        status: 'primary',
                        position: 'outside',
                        disabled: true
                    }
                ]
                break;
            case FormButtonIds.CodeTestSkipAndFinish:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Skip and finish',
                        id: 'utg_skip_and_finish',
                        status: 'primary',
                        position: 'outside',
                        disabled: true
                    }
                ]
                break;
                /*
                //TODO: generate button
                case FormButtonIds.CodeTestRegenerate:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Regenerate',
                        id: 'utg_regenerate',
                        status: 'info',
                        position: 'outside',
                        disabled: true
                    }
                ]
                break;
                 */
            case FormButtonIds.CodeTestRejectAndRevert:
                // TODO: what behavior should this be?
                break;
            case FormButtonIds.CodeTestProceed:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Proceeded',
                        id: 'utg_proceeded',
                        status: 'primary',
                        position: 'outside',
                        disabled: true
                    }
                ]
                break;
            case FormButtonIds.CodeTestModifyCommand:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Modify command',
                        id: 'utg_modify_command',
                        status: 'primary',
                        position: 'outside',
                        disabled: true
                    }
                ]
                break

            case FormButtonIds.CodeTestProvideFeedback:
                answer.buttons = [
                    {
                        keepCardAfterClick: true,
                        text: 'Thanks for providing feedback.',
                        id: 'utg_provided_feedback',
                        status: 'success',
                        position: 'outside',
                        disabled: true
                    }
                ]
                break
            default:
                console.warn(`Unhandled action ID: ${action.id}`);
                break;
        }

        this.onChatAnswerUpdated(tabID, answer);
    }

    clearChat = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'clear',
            chatMessage: '',
            tabType: 'codetest',
        })
    }

    help = (tabID: string): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'help',
            chatMessage: '',
            tabType: 'codetest',
        })
    }

    onTabOpen = (tabID: string) => {
        this.sendMessageToExtension({
            tabID,
            command: 'new-tab-was-created',
            tabType: 'codetest'
        })
    }

    /**
     * Ignore for this pr, this request Answer function is used to in the future to receive users' input
     */
    requestAnswer = (tabID: string, payload: ChatPayload) => {
        this.sendMessageToExtension({
            tabID,
            command: 'chat-prompt',
            chatMessage: payload.chatMessage,
            tabType: 'codetest'
        })
    }

    onTabRemove = (tabID: string) => {
        this.sendMessageToExtension({
            tabID,
            command: 'tab-was-removed',
            tabType: 'codetest'
        })
    }

    startTestGen = (tabID: string, prompt: string): void => {
        console.log("calling generate-test here")
        this.sendMessageToExtension({
            tabID: tabID,
            command: 'start-test-gen',
            prompt,
            tabType: 'codetest'
        })
    }

    onCodeInsertToCursorPosition = (tabID: string, code?: string, type?: 'selection' | 'block', codeReference?: CodeReference[]): void => {
        this.sendMessageToExtension({
            tabID: tabID,
            code,
            command: 'insert_code_at_cursor_position',
            codeReference,
            tabType: 'codetest'
        })
    }

    onFileClick = (tabID: string, filePath: string, deleted: boolean, messageId?: string): void => {
        this.sendMessageToExtension({
            command: 'open-diff',
            tabID,
            filePath,
            deleted,
            messageId,
            tabType: 'codetest',
        })
    }

    followUpClicked = (tabID: string, followUp: ChatItemAction): void => {
        this.sendMessageToExtension({
            command: 'follow-up-was-clicked',
            followUp,
            tabID,
            tabType: 'codetest',
        })
    }

    onChatItemVoted = (tabId: string, messageId: string, vote: string): void | undefined => {
        this.sendMessageToExtension({
            tabID: tabId,
            vote: vote,
            command: 'chat-item-voted',
            tabType: 'codetest',
        })
    }

    sendFeedback = (tabId: string, feedbackPayload: FeedbackPayload): void | undefined => {
        this.sendMessageToExtension({
            command: 'chat-item-feedback',
            ...feedbackPayload,
            tabType: 'codetest',
            tabID: tabId,
        })
    }

    onResponseBodyLinkClick = (tabID: string, messageId: string, link: string): void => {
        this.sendMessageToExtension({
            command: 'response-body-link-click',
            tabID,
            messageId,
            link,
            tabType: 'codetest',
        })
    }

}
