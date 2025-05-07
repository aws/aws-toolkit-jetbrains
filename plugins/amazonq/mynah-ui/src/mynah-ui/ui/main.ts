/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import { Connector, CWCChatItem } from './connector'
import {
    ChatItem, ChatItemAction,
    ChatItemType,
    MynahIcons,
    MynahUI,
    MynahUIDataModel, MynahUIProps,
    NotificationType,
    ProgressField, QuickActionCommand,
    ReferenceTrackerInformation,
    ChatPrompt,
} from '@aws/mynah-ui-chat'
import './styles/dark.scss'
import { TabsStorage, TabType } from './storages/tabsStorage'
import { WelcomeFollowupType } from './apps/amazonqCommonsConnector'
import { TabDataGenerator } from './tabs/generator'
import { feedbackOptions } from './feedback/constants'
import { uiComponentsTexts } from './texts/constants'
import { FollowUpInteractionHandler } from './followUps/handler'
import { QuickActionHandler } from './quickActions/handler'
import { TextMessageHandler } from './messages/handler'
import { MessageController } from './messages/controller'
import { getActions, getDetails } from './diffTree/actions'
import { DiffTreeFileInfo } from './diffTree/types'
import './styles.css'
import {welcomeScreenTabData} from "./walkthrough/welcome";
import { agentWalkthroughDataModel } from './walkthrough/agent'
import {createClickTelemetry, createOpenAgentTelemetry} from "./telemetry/actions";
import {disclaimerAcknowledgeButtonId, disclaimerCard} from "./texts/disclaimer";
import {FeatureContext, tryNewMap} from "./types";



// Ref: https://github.com/aws/aws-toolkit-vscode/blob/e9ea8082ffe0b9968a873437407d0b6b31b9e1a5/packages/core/src/amazonq/webview/ui/main.ts

export class WebviewUIHandler {
    postMessage: any
    showWelcomePage: boolean
    disclaimerAcknowledged: boolean
    isFeatureDevEnabled: boolean
    isCodeTransformEnabled: boolean
    isDocEnabled: boolean
    isCodeScanEnabled: boolean
    isCodeTestEnabled: boolean
    highlightCommand?: QuickActionCommand
    profileName?: string
    responseMetadata: Map<string, string[]>
    tabsStorage: TabsStorage
    featureConfigs?: Map<string, FeatureContext>

    mynahUIProps: MynahUIProps
    connector?: Connector
    tabDataGenerator?: TabDataGenerator
    followUpsInteractionHandler?: FollowUpInteractionHandler
    quickActionHandler?: QuickActionHandler
    textMessageHandler?: TextMessageHandler
    messageController?: MessageController

    disclaimerCardActive : boolean


    mynahUIRef: { mynahUI: MynahUI | undefined }
    constructor({
                    postMessage,
                    mynahUIRef,
                    featureConfigsSerialized,
                    showWelcomePage,
                    disclaimerAcknowledged,
                    isFeatureDevEnabled,
                    isCodeTransformEnabled,
                    isDocEnabled,
                    isCodeScanEnabled,
                    isCodeTestEnabled,
                    highlightCommand,
                    profileName,
                    hybridChat,

                } : {
        postMessage: any
        mynahUIRef: { mynahUI: MynahUI | undefined }
        featureConfigsSerialized: [string, FeatureContext][]
        showWelcomePage: boolean,
        disclaimerAcknowledged: boolean,
        isFeatureDevEnabled: boolean
        isCodeTransformEnabled: boolean
        isDocEnabled: boolean
        isCodeScanEnabled: boolean
        isCodeTestEnabled: boolean
        highlightCommand?: QuickActionCommand,
        profileName?: string,
        hybridChat?: boolean


    }) {
        this.postMessage = postMessage
        this.mynahUIRef = mynahUIRef
        this.featureConfigs = tryNewMap(featureConfigsSerialized)
        this.showWelcomePage = showWelcomePage;
        this.disclaimerAcknowledged = disclaimerAcknowledged
        this.isFeatureDevEnabled = isFeatureDevEnabled
        this.isCodeTransformEnabled = isCodeTransformEnabled
        this.isDocEnabled = isDocEnabled
        this.isCodeScanEnabled = isCodeScanEnabled
        this.isCodeTestEnabled = isCodeTestEnabled
        this.profileName = profileName
        this.responseMetadata = new Map<string, string[]>()
        this.disclaimerCardActive = !disclaimerAcknowledged


        this.tabsStorage = new TabsStorage({
            onTabTimeout: tabID => {
                this.mynahUI?.addChatItem(tabID, {
                    type: ChatItemType.ANSWER,
                    body: 'This conversation has timed out after 48 hours. It will not be saved. Start a new conversation.',
                })
                this.mynahUI?.updateStore(tabID, {
                    promptInputDisabledState: true,
                    promptInputPlaceholder: 'Session ended.',
                })
            },
        })

        this.tabDataGenerator = new TabDataGenerator({
            isFeatureDevEnabled,
            isCodeTransformEnabled,
            isDocEnabled,
            isCodeScanEnabled,
            isCodeTestEnabled,
            highlightCommand,
            profileName
        })

        this.connector = new Connector({
            tabsStorage: this.tabsStorage,
            /**
             * Proxy for allowing underlying common connectors to call quick action handlers
             */
            handleCommand: (chatPrompt: ChatPrompt, tabId: string) => {
                this.quickActionHandler?.handleCommand(chatPrompt, tabId)
            },
            onUpdateAuthentication: (
                featureDevEnabled: boolean,
                codeTransformEnabled: boolean,
                docEnabled: boolean,
                codeScanEnabled: boolean,
                codeTestEnabled: boolean,
                authenticatingTabIDs: string[]
            ): void => {
                isFeatureDevEnabled = featureDevEnabled
                isCodeTransformEnabled = codeTransformEnabled
                isDocEnabled = docEnabled
                isCodeScanEnabled = codeScanEnabled
                isCodeTestEnabled = codeTestEnabled

                this.quickActionHandler = new QuickActionHandler({
                    mynahUIRef: this.mynahUIRef,
                    connector: this.connector!,
                    tabsStorage: this.tabsStorage,
                    isFeatureDevEnabled: this.isFeatureDevEnabled,
                    isCodeTransformEnabled: this.isCodeTransformEnabled,
                    isDocEnabled: this.isDocEnabled,
                    isCodeScanEnabled: this.isCodeScanEnabled,
                    isCodeTestEnabled: this.isCodeTestEnabled,
                    hybridChat
                })

                this.tabDataGenerator = new TabDataGenerator({
                    isFeatureDevEnabled,
                    isCodeTransformEnabled,
                    isDocEnabled,
                    isCodeScanEnabled,
                    isCodeTestEnabled,
                    highlightCommand,
                    profileName
                })

                this.featureConfigs = tryNewMap(featureConfigsSerialized)

                // Set the new defaults for the quick action commands in all tabs now that isFeatureDevEnabled and isCodeTransformEnabled were enabled/disabled
                for (const tab of this.tabsStorage.getTabs()) {
                    this.mynahUI?.updateStore(tab.id, {
                        quickActionCommands: this.tabDataGenerator.quickActionsGenerator.generateForTab(tab.type),
                    })
                }

                // Unlock every authenticated tab that is now authenticated
                for (const tabID of authenticatingTabIDs) {
                    const tabType = this.tabsStorage.getTab(tabID)?.type
                    if (
                        (tabType === 'featuredev' && featureDevEnabled) ||
                        (tabType === 'codetransform' && codeTransformEnabled) ||
                        (tabType === 'doc' && docEnabled) ||
                        (tabType === 'codetransform' && codeTransformEnabled) ||
                        (tabType === 'codetest' && codeTestEnabled)
                    ) {
                        this.mynahUI?.addChatItem(tabID, {
                            type: ChatItemType.ANSWER,
                            body: 'Authentication successful. Connected to Amazon Q.',
                        })
                        this.mynahUI?.updateStore(tabID, {
                            // Always disable prompt for code transform tabs
                            promptInputDisabledState: tabType === 'codetransform',
                        })
                    }
                }
            },
            onFileActionClick: (): void => {},
            onCWCOnboardingPageInteractionMessage: (message: ChatItem): string | undefined => {
                return this.messageController?.sendMessageToTab(message, 'cwc')
            },
            onQuickHandlerCommand: (tabID: string, command?: string, eventId?: string) => {
                this.tabsStorage.updateTabLastCommand(tabID, command)
                if (command === 'aws.awsq.transform') {
                    this.quickActionHandler?.handleCommand({ command: '/transform' }, tabID, eventId)
                } else if (command === 'aws.awsq.clearchat') {
                    this.quickActionHandler?.handleCommand({ command: '/clear' }, tabID)
                }
            },
            onCWCContextCommandMessage: (message: ChatItem, command?: string): string | undefined => {
                const selectedTab = this.tabsStorage.getSelectedTab()
                this.tabsStorage.updateTabLastCommand(selectedTab?.id || '', command || '')
                if (command === 'aws.amazonq.sendToPrompt') {
                    return this.messageController?.sendSelectedCodeToTab(message)
                } else {
                    const tabID = this.messageController?.sendMessageToTab(message, 'cwc')
                    if (tabID && command) {
                        this.postMessage.postMessage(createOpenAgentTelemetry('cwc', 'right-click'))
                    }

                    return tabID
                }
            },
            onWelcomeFollowUpClicked: (tabID: string, welcomeFollowUpType: WelcomeFollowupType) => {
                this.followUpsInteractionHandler?.onWelcomeFollowUpClicked(tabID, welcomeFollowUpType)
            },
            onChatInputEnabled: (tabID: string, enabled: boolean) => {
                this.mynahUI?.updateStore(tabID, {
                    promptInputDisabledState: this.tabsStorage.isTabDead(tabID) || !enabled,
                })
            },
            onAsyncEventProgress: (tabID: string, inProgress: boolean, message: string | undefined, cancelButtonWhenLoading: boolean = false) => {
                if (inProgress) {
                    this.mynahUI?.updateStore(tabID, {
                        loadingChat: true,
                        promptInputDisabledState: true,
                        cancelButtonWhenLoading,
                    })
                    if (message) {
                        this.mynahUI?.updateLastChatAnswer(tabID, {
                            body: message,
                        })
                    }
                    this.mynahUI?.addChatItem(tabID, {
                        type: ChatItemType.ANSWER_STREAM,
                        body: '',
                    })
                    this.tabsStorage.updateTabStatus(tabID, 'busy')
                    return
                }

                this.mynahUI?.updateStore(tabID, {
                    loadingChat: false,
                    promptInputDisabledState: this.tabsStorage.isTabDead(tabID),
                })
                this.tabsStorage.updateTabStatus(tabID, 'free')
            },
            onCodeTransformChatDisabled: (tabID: string) => {
                // Clear the chat window to prevent button clicks or form selections
                this.mynahUI?.updateStore(tabID, {
                    loadingChat: false,
                    chatItems: [],
                })
            },
            onCodeTransformMessageReceived: (
                tabID: string,
                chatItem: ChatItem,
                isLoading: boolean,
                clearPreviousItemButtons?: boolean
            ) => {
                if (chatItem.type === ChatItemType.ANSWER_PART) {
                    this.mynahUI?.updateLastChatAnswer(tabID, {
                        ...(chatItem.messageId !== undefined ? { messageId: chatItem.messageId } : {}),
                        ...(chatItem.canBeVoted !== undefined ? { canBeVoted: chatItem.canBeVoted } : {}),
                        ...(chatItem.codeReference !== undefined ? { codeReference: chatItem.codeReference } : {}),
                        ...(chatItem.body !== undefined ? { body: chatItem.body } : {}),
                        ...(chatItem.relatedContent !== undefined ? { relatedContent: chatItem.relatedContent } : {}),
                        ...(chatItem.formItems !== undefined ? { formItems: chatItem.formItems } : {}),
                        ...(chatItem.buttons !== undefined ? { buttons: chatItem.buttons } : { buttons: [] }),
                        // For loading animation to work, do not update the chat item type
                        ...(chatItem.followUp !== undefined ? { followUp: chatItem.followUp } : {}),
                    })

                    if (!isLoading) {
                        this.mynahUI?.updateStore(tabID, {
                            loadingChat: false,
                        })
                    }

                    return
                }

                if (
                    chatItem.type === ChatItemType.PROMPT ||
                    chatItem.type === ChatItemType.ANSWER_STREAM ||
                    chatItem.type === ChatItemType.ANSWER
                ) {
                    if (chatItem.followUp === undefined && clearPreviousItemButtons === true) {
                        this.mynahUI?.updateLastChatAnswer(tabID, {
                            buttons: [],
                            followUp: { options: [] },
                        })
                    }

                    this.mynahUI?.addChatItem(tabID, chatItem)
                    this.mynahUI?.updateStore(tabID, {
                        cancelButtonWhenLoading: false,
                        loadingChat: chatItem.type !== ChatItemType.ANSWER,
                    })

                    if (chatItem.type === ChatItemType.PROMPT) {
                        this.tabsStorage.updateTabStatus(tabID, 'busy')
                    } else if (chatItem.type === ChatItemType.ANSWER) {
                        this.tabsStorage.updateTabStatus(tabID, 'free')
                    }
                }
            },
            onCodeTransformMessageUpdate: (tabID: string, messageId: string, chatItem: Partial<ChatItem>) => {
                this.mynahUI?.updateChatAnswerWithMessageId(tabID, messageId, chatItem)
            },
            onNotification: (notification: { content: string; title?: string; type: NotificationType }) => {
                this.mynahUI?.notify(notification)
            },
            onCodeTransformCommandMessageReceived: (_message: ChatItem, command?: string) => {
                if (command === 'stop') {
                    const codeTransformTab = this.tabsStorage.getTabs().find(tab => tab.type === 'codetransform')
                    if (codeTransformTab !== undefined && codeTransformTab.isSelected) {
                        return
                    }

                    this.mynahUI?.notify({
                        type: NotificationType.INFO,
                        title: 'Q - Transform',
                        content: `Amazon Q is stopping your transformation. To view progress in the Q - Transform tab, click anywhere on this notification.`,
                        duration: 10000,
                        onNotificationClick: eventId => {
                            if (codeTransformTab !== undefined) {
                                // Click to switch to the opened code transform tab
                                this.mynahUI?.selectTab(codeTransformTab.id, eventId)
                            } else {
                                // Click to open a new code transform tab
                                this.quickActionHandler?.handleCommand({ command: '/transform' }, '', eventId)
                            }
                        },
                    })
                }
            },
            sendMessageToExtension: message => {
                postMessage.postMessage(message)
            },
            onChatAnswerUpdated: (tabID: string, item) => {
                if (item.messageId !== undefined) {
                    this.mynahUI?.updateChatAnswerWithMessageId(tabID, item.messageId, {
                        ...(item.body !== undefined ? { body: item.body } : {}),
                        ...(item.buttons !== undefined ? { buttons: item.buttons } : {}),
                        ...(item.fileList !== undefined ? { fileList: item.fileList } : {}),
                        ...(item.footer !== undefined ? { footer: item.footer } : {}),
                        ...(item.canBeVoted !== undefined ? { canBeVoted: item.canBeVoted } : {}),
                        ...(item.codeReference !== undefined ? { codeReference: item.codeReference } : {}),
                    })
                } else {
                    this.mynahUI?.updateLastChatAnswer(tabID, {
                        ...(item.body !== undefined ? { body: item.body } : {}),
                        ...(item.buttons !== undefined ? { buttons: item.buttons } : {}),
                        ...(item.fileList !== undefined ? { fileList: item.fileList } : {}),
                        ...(item.footer !== undefined ? { footer: item.footer } : {}),
                        ...(item.canBeVoted !== undefined ? { canBeVoted: item.canBeVoted } : {}),
                        ...(item.codeReference !== undefined ? { codeReference: item.codeReference } : {}),
                    } as ChatItem)
                }
            },
            onChatAnswerReceived: (tabID: string, item: CWCChatItem, messageData: any) => {
                if (item.type === ChatItemType.ANSWER_PART || item.type === ChatItemType.CODE_RESULT) {
                    this.mynahUI?.updateLastChatAnswer(tabID, {
                        ...(item.messageId !== undefined ? { messageId: item.messageId } : {}),
                        ...(item.canBeVoted !== undefined ? { canBeVoted: item.canBeVoted } : {}),
                        ...(item.codeReference !== undefined ? { codeReference: item.codeReference } : {}),
                        ...(item.body !== undefined ? { body: item.body } : {}),
                        ...(item.relatedContent !== undefined ? { relatedContent: item.relatedContent } : {}),
                        ...(item.type === ChatItemType.CODE_RESULT
                            ? { type: ChatItemType.CODE_RESULT, fileList: item.fileList }
                            : {}),
                        ...(item.codeReference !== undefined ? { codeReference: item.codeReference } : {}),
                    })
                    if (item.messageId !== undefined && item.userIntent !== undefined && item.codeBlockLanguage !== undefined) {
                        this.responseMetadata.set(item.messageId, [item.userIntent, item.codeBlockLanguage])
                    }
                    return
                }

                if (item.body !== undefined || item.relatedContent !== undefined || item.followUp !== undefined || item.formItems !== undefined || item.buttons !== undefined) {
                    this.mynahUI?.addChatItem(tabID, {
                        ...item,
                        messageId: item.messageId,
                        codeBlockActions: this.getCodeBlockActions(messageData),
                    })
                }

                if (
                    item.type === ChatItemType.PROMPT ||
                    item.type === ChatItemType.SYSTEM_PROMPT ||
                    item.type === ChatItemType.AI_PROMPT
                ) {
                    this.mynahUI?.updateStore(tabID, {
                        loadingChat: true,
                        cancelButtonWhenLoading: false,
                        promptInputDisabledState: true,
                    })

                    this.tabsStorage.updateTabStatus(tabID, 'busy')
                    return
                }

                if (item.type === ChatItemType.ANSWER) {
                    this.mynahUI?.updateStore(tabID, {
                        loadingChat: false,
                        promptInputDisabledState: this.tabsStorage.isTabDead(tabID),
                    })
                    this.tabsStorage.updateTabStatus(tabID, 'free')
                }
            },
            onRunTestMessageReceived: (tabID: string, shouldRunTestMessage: boolean) => {
                if (shouldRunTestMessage) {
                    this.quickActionHandler?.handleCommand({ command: '/test' }, tabID)
                }
            },
            onMessageReceived: (tabID: string, messageData: MynahUIDataModel) => {
                this.mynahUI?.updateStore(tabID, messageData)
            },
            onFileComponentUpdate: (
                tabID: string,
                filePaths: DiffTreeFileInfo[],
                deletedFiles: DiffTreeFileInfo[],
                messageId: string,
                disableFileActions: boolean = false
            ) => {
                const updateWith: Partial<ChatItem> = {
                    type: ChatItemType.ANSWER,
                    fileList: {
                        rootFolderTitle: 'Changes',
                        filePaths: filePaths.map(i => i.zipFilePath),
                        deletedFiles: deletedFiles.map(i => i.zipFilePath),
                        details: getDetails([...filePaths, ...deletedFiles]),
                        actions: disableFileActions ? undefined : getActions([...filePaths, ...deletedFiles]),
                    },
                }
                this.mynahUI?.updateChatAnswerWithMessageId(tabID, messageId, updateWith)
            },
            onWarning: (tabID: string, message: string, title: string) => {
                this.mynahUI?.notify({
                    title: title,
                    content: message,
                    type: NotificationType.WARNING,
                })
                this.mynahUI?.updateStore(tabID, {
                    loadingChat: false,
                    promptInputDisabledState: this.tabsStorage.isTabDead(tabID),
                })
                this.tabsStorage.updateTabStatus(tabID, 'free')
            },
            onError: (tabID: string, message: string, title: string) => {
                const answer: ChatItem = {
                    type: ChatItemType.ANSWER,
                    body: `**${title}**${message}`,
                }

                if (tabID !== '') {
                    this.mynahUI?.updateStore(tabID, {
                        loadingChat: false,
                        promptInputDisabledState: this.tabsStorage.isTabDead(tabID),
                    })
                    this.tabsStorage.updateTabStatus(tabID, 'free')

                    this.mynahUI?.addChatItem(tabID, answer)
                } else {
                    const newTabId = this.mynahUI?.updateStore('', {
                        tabTitle: 'Error',
                        quickActionCommands: [],
                        promptInputPlaceholder: '',
                        chatItems: [answer],
                    })
                    if (newTabId === undefined) {
                        this.mynahUI?.notify({
                            content: uiComponentsTexts.noMoreTabsTooltip,
                            type: NotificationType.WARNING,
                        })
                        return
                    } else {
                        // TODO remove this since it will be added with the onTabAdd and onTabAdd is now sync,
                        // It means that it cannot trigger after the updateStore function returns.
                        this.tabsStorage.addTab({
                            id: newTabId,
                            status: 'busy',
                            type: 'cwc',
                            isSelected: true,
                        })
                    }
                }
                return
            },
            onUpdatePlaceholder: (tabID: string, newPlaceholder: string) => {
                this.mynahUI?.updateStore(tabID, {
                    promptInputPlaceholder: newPlaceholder,
                })
            },
            onUpdatePromptProgress: (tabID: string, progressField: ProgressField | null | undefined) => {
                this.mynahUI?.updateStore(tabID, {
                    // eslint-disable-next-line no-null/no-null
                    promptInputProgress: progressField ? progressField : null,
                })
            },
            onNewTab: (tabType: TabType) => {
                const newTabID = this.mynahUI?.updateStore('', {})
                if (!newTabID) {
                    return
                }

                this.tabsStorage.updateTabTypeFromUnknown(newTabID, tabType)
                this.connector?.onKnownTabOpen(newTabID)
                this.connector?.onUpdateTabType(newTabID)
                this.featureConfigs = tryNewMap(featureConfigsSerialized)
                this.mynahUI?.updateStore(newTabID, this.tabDataGenerator!.getTabData(tabType, true))
            },
            onStartNewTransform: (tabID: string) => {
                this.mynahUI?.updateStore(tabID, { chatItems: [] })
                this.mynahUI?.updateStore(tabID, this.tabDataGenerator!.getTabData('codetransform', true))
            },
            onOpenSettingsMessage: (tabId: string) => {
                this.mynahUI?.addChatItem(tabId, {
                    type: ChatItemType.ANSWER,
                    body: `You need to enable local workspace index in Amazon Q settings.`,
                    buttons: [
                        {
                            id: 'open-settings',
                            text: 'Open settings',
                            icon: MynahIcons.EXTERNAL,
                            keepCardAfterClick: false,
                            status: 'info',
                        },
                    ],
                })
                this.tabsStorage.updateTabStatus(tabId, 'free')
                this.mynahUI?.updateStore(tabId, {
                    loadingChat: false,
                    promptInputDisabledState: this.tabsStorage.isTabDead(tabId),
                })
                return
            },
            onCodeScanMessageReceived: (tabID: string, chatItem: ChatItem, isLoading: boolean, clearPreviousItemButtons?: boolean, runReview?: boolean) => {
                if (runReview) {
                    this.quickActionHandler?.handleCommand({ command: "/review" }, "")
                    return
                }
                if (chatItem.type === ChatItemType.ANSWER_PART) {
                    this.mynahUI?.updateLastChatAnswer(tabID, {
                        ...(chatItem.messageId !== undefined ? { messageId: chatItem.messageId } : {}),
                        ...(chatItem.canBeVoted !== undefined ? { canBeVoted: chatItem.canBeVoted } : {}),
                        ...(chatItem.codeReference !== undefined ? { codeReference: chatItem.codeReference } : {}),
                        ...(chatItem.body !== undefined ? { body: chatItem.body } : {}),
                        ...(chatItem.relatedContent !== undefined ? { relatedContent: chatItem.relatedContent } : {}),
                        ...(chatItem.formItems !== undefined ? { formItems: chatItem.formItems } : {}),
                        ...(chatItem.buttons !== undefined ? { buttons: chatItem.buttons } : { buttons: [] }),
                        // For loading animation to work, do not update the chat item type
                        ...(chatItem.followUp !== undefined ? { followUp: chatItem.followUp } : {}),
                    })

                    if (!isLoading) {
                        this.mynahUI?.updateStore(tabID, {
                            loadingChat: false,
                        })
                    } else {
                        this.mynahUI?.updateStore(tabID, {
                            cancelButtonWhenLoading: false
                        })
                    }
                }

                if (
                    chatItem.type === ChatItemType.PROMPT ||
                    chatItem.type === ChatItemType.ANSWER_STREAM ||
                    chatItem.type === ChatItemType.ANSWER
                ) {
                    if (chatItem.followUp === undefined && clearPreviousItemButtons === true) {
                        this.mynahUI?.updateLastChatAnswer(tabID, {
                            buttons: [],
                            followUp: { options: [] },
                        })
                    }

                    this.mynahUI?.addChatItem(tabID, chatItem)
                    this.mynahUI?.updateStore(tabID, {
                        loadingChat: chatItem.type !== ChatItemType.ANSWER
                    })

                    if (chatItem.type === ChatItemType.PROMPT) {
                        this.tabsStorage.updateTabStatus(tabID, 'busy')
                    } else if (chatItem.type === ChatItemType.ANSWER) {
                        this.tabsStorage.updateTabStatus(tabID, 'free')
                    }
                }
            },
            onFeatureConfigsAvailable: (
                highlightCommand?: QuickActionCommand
            ): void => {
                this.tabDataGenerator!.highlightCommand = highlightCommand

                for (const tab of this.tabsStorage.getTabs()) {
                    this.mynahUI?.updateStore(tab.id, {
                        contextCommands: this.tabDataGenerator!.getTabData(tab.type, true).contextCommands
                    })
                }
            }
        })
        this.mynahUIProps = {
            onReady: () => {
                // the legacy event flow adds events listeners to the window, we want to avoid these in the lsp flow, since those
                // are handled by the flare chat-client
                if (hybridChat && this.connector) {
                    this.connector.isUIReady = true
                    postMessage.postMessage({
                        command: 'ui-is-ready',
                    })
                    return
                }
                this.connector?.uiReady()
            },
            onTabAdd: (tabID: string) => {
                // If featureDev or gumby has changed availability in between the default store settings and now
                // make sure to show/hide it accordingly
                this.mynahUI?.updateStore(tabID, {
                    quickActionCommands: this.tabDataGenerator?.quickActionsGenerator.generateForTab('unknown'),
                    ...(this.disclaimerCardActive ? { promptInputStickyCard: disclaimerCard } : {}),
                })
                this.connector?.onTabAdd(tabID)
            },
            onStopChatResponse: (tabID: string) => {
                this.mynahUI?.updateStore(tabID, {
                    loadingChat: false,
                    promptInputDisabledState: false,
                })
                this.connector?.onStopChatResponse(tabID)
            },
            onTabRemove: this.connector.onTabRemove,
            onTabChange: this.connector.onTabChange,
            onChatPrompt: (tabID: string, prompt : ChatPrompt, eventId: string | undefined) => {
                if ((prompt.prompt ?? '') === '' && (prompt.command ?? '') === '') {
                    return
                }

                if (this.tabsStorage.getTab(tabID)?.type === 'featuredev') {
                    this.mynahUI?.addChatItem(tabID, {
                        type: ChatItemType.ANSWER_STREAM,
                    })
                } else if (this.tabsStorage.getTab(tabID)?.type === 'codetransform') {
                    this.connector?.requestAnswer(tabID, {
                        chatMessage: prompt.prompt ?? ''
                    })
                    return
                } else if (this.tabsStorage.getTab(tabID)?.type === 'codetest') {
                    if(prompt.command !== undefined && prompt.command.trim() !== '' && prompt.command !== '/test') {
                        this.quickActionHandler?.handleCommand(prompt, tabID, eventId)
                        return
                    } else {
                        this.connector?.requestAnswer(tabID, {
                            chatMessage: prompt.prompt ?? ''
                        })
                        return
                    }
                } else if (this.tabsStorage.getTab(tabID)?.type === 'codescan') {
                    if(prompt.command !== undefined && prompt.command.trim() !== '') {
                        this.quickActionHandler?.handleCommand(prompt, tabID, eventId)
                        return
                    }
                }

                if (this.tabsStorage.getTab(tabID)?.type === 'welcome') {
                    this.mynahUI?.updateStore(tabID, {
                        tabHeaderDetails: void 0,
                        compactMode: false,
                        tabBackground: false,
                        promptInputText: '',
                        promptInputLabel: void 0,
                        chatItems: [],
                    })
                }

                if (prompt.command !== undefined && prompt.command.trim() !== '') {
                    this.quickActionHandler?.handleCommand(prompt, tabID, eventId)

                    const newTabType = this.tabsStorage.getSelectedTab()?.type
                    if (newTabType) {
                        postMessage.postMessage(createOpenAgentTelemetry(newTabType, 'quick-action'))
                    }
                    return
                }

                this.textMessageHandler!.handle(prompt, tabID)
            },
            onVote: this.connector.onChatItemVoted,
            onSendFeedback: (tabId, feedbackPayload) => {
                this.connector?.sendFeedback(tabId, feedbackPayload)
                this.mynahUI?.notify({
                    type: NotificationType.INFO,
                    title: 'Your feedback is sent',
                    content: 'Thanks for your feedback.',
                })
            },
            onCodeInsertToCursorPosition: this.connector.onCodeInsertToCursorPosition,
            onCopyCodeToClipboard: (
                tabId,
                messageId,
                code,
                type,
                referenceTrackerInfo,
                eventId,
                codeBlockIndex,
                totalCodeBlocks
            ) => {
                this.connector?.onCopyCodeToClipboard(
                    tabId,
                    messageId,
                    code,
                    type,
                    referenceTrackerInfo,
                    eventId,
                    codeBlockIndex,
                    totalCodeBlocks
                )
                this.mynahUI?.notify({
                    type: NotificationType.SUCCESS,
                    content: 'Selected code is copied to clipboard',
                })
            },
            onChatItemEngagement: this.connector.triggerSuggestionEngagement,
            onSourceLinkClick: (tabId, messageId, link, mouseEvent) => {
                mouseEvent?.preventDefault()
                mouseEvent?.stopPropagation()
                mouseEvent?.stopImmediatePropagation()
                this.connector?.onSourceLinkClick(tabId, messageId, link)
            },
            onLinkClick: (tabId, messageId, link, mouseEvent) => {
                mouseEvent?.preventDefault()
                mouseEvent?.stopPropagation()
                mouseEvent?.stopImmediatePropagation()
                this.connector?.onResponseBodyLinkClick(tabId, messageId, link)
            },
            onInfoLinkClick: (tabId: string, link: string, mouseEvent?: MouseEvent) => {
                mouseEvent?.preventDefault()
                mouseEvent?.stopPropagation()
                mouseEvent?.stopImmediatePropagation()
                this.connector?.onInfoLinkClick(tabId, link)
            },
            onResetStore: () => {},
            onFollowUpClicked: (tabID, messageId, followUp) => {
                this.followUpsInteractionHandler!.onFollowUpClicked(tabID, messageId, followUp)
            },
            onFileActionClick: async (tabID: string, messageId: string, filePath: string, actionName: string) => {
                this.connector?.onFileActionClick(tabID, messageId, filePath, actionName)
            },
            onFileClick: this.connector?.onFileClick,
            onChatPromptProgressActionButtonClicked: (tabID, action) => {
                this.connector?.onCustomFormAction(tabID, undefined, action)
            },
            tabs: {
                'tab-1': {
                    isSelected: true,
                    store: {
                        ...(showWelcomePage
                            ? welcomeScreenTabData(this.tabDataGenerator).store
                            : this.tabDataGenerator.getTabData('cwc', true)),
                        ...(this.disclaimerCardActive ? { promptInputStickyCard: disclaimerCard } : {}),
                    },
                },
            },
            onInBodyButtonClicked: (tabId, messageId, action, eventId) => {
                if (action.id === disclaimerAcknowledgeButtonId) {
                    this.disclaimerCardActive = false
                    // post message to tell IDE that disclaimer is acknowledged
                    postMessage.postMessage({
                        command: 'disclaimer-acknowledged',
                    })

                    // create telemetry
                    postMessage.postMessage(createClickTelemetry('amazonq-disclaimer-acknowledge-button'))

                    // remove all disclaimer cards from all tabs
                    Object.keys(this.mynahUI?.getAllTabs() ?? []).forEach((storeTabKey) => {
                        // eslint-disable-next-line no-null/no-null
                        this.mynahUI?.updateStore(storeTabKey, { promptInputStickyCard: null })
                    })
                }

                if (action.id === 'quick-start') {
                    /**
                     * quick start is the action on the welcome page. When its
                     * clicked it collapses the view and puts it into regular
                     * "chat" which is cwc
                     */
                    this.tabsStorage.updateTabTypeFromUnknown(tabId, 'cwc')

                    // show quick start in the current tab instead of a new one
                    this.mynahUI?.updateStore(tabId, {
                        tabHeaderDetails: undefined,
                        compactMode: false,
                        tabBackground: false,
                        promptInputText: '/',
                        promptInputLabel: undefined,
                        chatItems: [],
                    })

                    postMessage.postMessage(createClickTelemetry('amazonq-welcome-quick-start-button'))
                    return
                }

                if (action.id === 'explore') {
                    const newTabId = this.mynahUI?.updateStore('', agentWalkthroughDataModel)
                    if (newTabId === undefined) {
                        this.mynahUI?.notify({
                            content: uiComponentsTexts.noMoreTabsTooltip,
                            type: NotificationType.WARNING,
                        })
                        return
                    }
                    this.tabsStorage.updateTabTypeFromUnknown(newTabId, 'agentWalkthrough')
                    postMessage.postMessage(createClickTelemetry('amazonq-welcome-explore-button'))
                    return
                }

                this.connector?.onCustomFormAction(tabId, messageId, action, eventId)
            },
            defaults: {
                store: this.tabDataGenerator.getTabData('cwc', true),
            },
            config: {
                maxTabs: 10,
                feedbackOptions: feedbackOptions,
                texts: uiComponentsTexts,
            },
        }
        if (!hybridChat) {
            /**
             * when in hybrid chat the reference gets resolved later so we
             * don't need to create mynah UI
             */
            this.mynahUIRef = { mynahUI: new MynahUI({ ...this.mynahUIProps, loadStyles: false }) }
        }
        this.followUpsInteractionHandler = new FollowUpInteractionHandler({
            mynahUIRef: this.mynahUIRef,
            connector: this.connector,
            tabsStorage: this.tabsStorage,
        })

        this.textMessageHandler = new TextMessageHandler({
            mynahUIRef: this.mynahUIRef,
            connector: this.connector,
            tabsStorage: this.tabsStorage,
        })
        this.messageController = new MessageController({
            mynahUIRef: this.mynahUIRef,
            connector: this.connector,
            tabsStorage: this.tabsStorage,
            isFeatureDevEnabled,
            isCodeTransformEnabled,
            isDocEnabled,
            isCodeScanEnabled,
            isCodeTestEnabled,
        })
        this.quickActionHandler = new QuickActionHandler({
            mynahUIRef: this.mynahUIRef,
            connector: this.connector!,
            tabsStorage: this.tabsStorage,
            isFeatureDevEnabled: this.isFeatureDevEnabled,
            isCodeTransformEnabled: this.isCodeTransformEnabled,
            isDocEnabled: this.isDocEnabled,
            isCodeScanEnabled: this.isCodeScanEnabled,
            isCodeTestEnabled: this.isCodeTestEnabled,
            hybridChat
        })

    }

    private getCodeBlockActions(messageData: any) {
        // Show ViewDiff and AcceptDiff for allowedCommands in CWC
        const isEnabled = this.featureConfigs?.get('ViewDiffInChat')?.variation === 'TREATMENT'
        const tab = this.tabsStorage.getTab(messageData?.tabID || '')
        const allowedCommands = [
            'aws.amazonq.refactorCode',
            'aws.amazonq.fixCode',
            'aws.amazonq.optimizeCode',
            'aws.amazonq.sendToPrompt',
        ]
        if (isEnabled && tab?.type === 'cwc' && allowedCommands.includes(tab.lastCommand || '')) {
            return {
                'insert-to-cursor': undefined,
                accept_diff: {
                    id: 'accept_diff',
                    label: 'Apply Diff',
                    icon: MynahIcons.OK_CIRCLED,
                    data: messageData,
                },
                view_diff: {
                    id: 'view_diff',
                    label: 'View Diff',
                    icon: MynahIcons.EYE,
                    data: messageData,
                },
            }
        }
        // Show only "Copy" option for codeblocks in Q Test Tab
        if (tab?.type === 'testgen') {
            return {
                'insert-to-cursor': undefined,
            }
        }
        // Default will show "Copy" and "Insert at cursor" for codeblocks
        return {}
    }
    get mynahUI(): MynahUI | undefined {
        return this.mynahUIRef.mynahUI
    }
}


export type MynahUIRef = { mynahUI: MynahUI | undefined }
