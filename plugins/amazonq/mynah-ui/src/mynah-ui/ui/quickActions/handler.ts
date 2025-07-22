/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItemType, ChatPrompt, MynahIcons, MynahUI, NotificationType } from '@aws/mynah-ui-chat'
import { TabDataGenerator } from '../tabs/generator'
import { Connector } from '../connector'
import { Tab, TabsStorage } from '../storages/tabsStorage'
import { uiComponentsTexts } from '../texts/constants'
import { MynahUIRef } from "../main";

export interface QuickActionsHandlerProps {
    mynahUIRef: MynahUIRef
    connector: Connector
    tabsStorage: TabsStorage
    isFeatureDevEnabled: boolean
    isCodeTransformEnabled: boolean
    isDocEnabled: boolean
    isCodeScanEnabled: boolean
    isCodeTestEnabled: boolean
    hybridChat?: boolean
}

export class QuickActionHandler {
    private mynahUIRef: MynahUIRef
    private connector: Connector
    private tabsStorage: TabsStorage
    private tabDataGenerator: TabDataGenerator
    public isFeatureDevEnabled: boolean
    public isCodeTransformEnabled: boolean
    public isDocEnabled: boolean
    public isCodeScanEnabled: boolean
    public isCodeTestEnabled: boolean
    private isHybridChatEnabled: boolean

    constructor(props: QuickActionsHandlerProps) {
        this.mynahUIRef = props.mynahUIRef
        this.connector = props.connector
        this.tabsStorage = props.tabsStorage
        this.tabDataGenerator = new TabDataGenerator({
            isFeatureDevEnabled: props.isFeatureDevEnabled,
            isCodeTransformEnabled: props.isCodeTransformEnabled,
            isDocEnabled: props.isDocEnabled,
            isCodeScanEnabled: props.isCodeScanEnabled,
            isCodeTestEnabled: props.isCodeTestEnabled,
        })
        this.isFeatureDevEnabled = props.isFeatureDevEnabled
        this.isCodeTransformEnabled = props.isCodeTransformEnabled
        this.isDocEnabled = props.isDocEnabled
        this.isCodeScanEnabled = props.isCodeScanEnabled
        this.isCodeTestEnabled = props.isCodeTestEnabled
        this.isHybridChatEnabled = props.hybridChat ?? false
    }

    // Entry point for `/xxx` commands
    public handleCommand(chatPrompt: ChatPrompt, tabID: string, eventId?: string) {
        this.tabsStorage.resetTabTimer(tabID)
        switch (chatPrompt.command) {
            case '/dev':
                this.handleFeatureDevCommand(chatPrompt, tabID, 'Q - Dev')
                break
            case '/help':
                this.handleHelpCommand(tabID)
                break
            case '/transform':
                this.handleCodeTransformCommand(tabID, eventId)
                break
            case '/doc':
                this.handleDocCommand(chatPrompt, tabID, 'Q - Doc')
                break
            case '/review':
                this.handleCodeScanCommand(tabID, eventId)
                break
            case '/test':
                this.handleCodeTestCommand(chatPrompt, tabID, eventId)
                break
            case '/clear':
                this.handleClearCommand(tabID)
                break
        }
    }

    private handleCodeTransformCommand(tabID: string, eventId?: string) {
        if (!this.isCodeTransformEnabled) {
            return
        }

        // Check for existing opened transform tab
        const existingTransformTab = this.tabsStorage.getTabs().find((tab) => tab.type === 'codetransform')
        if (existingTransformTab !== undefined) {
            this.mynahUI?.selectTab(existingTransformTab.id, eventId || "")
            this.connector.onTabChange(existingTransformTab.id)

            this.mynahUI?.notify({
                duration: 5000,
                title: "Q CodeTransformation",
                content: "Switched to the existing /transform tab; click 'Start a new transformation' below to run another transformation"
            });
            return
        }

        // Add new tab
        const affectedTabId: string | undefined = this.addTab(tabID)
        if (affectedTabId === undefined) {
            this.mynahUI?.notify({
                content: uiComponentsTexts.noMoreTabsTooltip,
                type: NotificationType.WARNING,
            })
            return
        } else {
            this.tabsStorage.updateTabTypeFromUnknown(affectedTabId, 'codetransform')
            this.connector.onKnownTabOpen(affectedTabId)
            // Clear unknown tab type's welcome message
            this.mynahUI?.updateStore(affectedTabId, {chatItems: []})
            this.mynahUI?.updateStore(affectedTabId, this.tabDataGenerator.getTabData('codetransform', true))
            this.mynahUI?.updateStore(affectedTabId, {
                promptInputDisabledState: true,
                promptInputPlaceholder: 'Open a new tab to chat with Q.',
                loadingChat: true,
                cancelButtonWhenLoading: false,
            })

            this.connector.onTabAdd(affectedTabId)
        }

        this.connector.transform(affectedTabId)
    }

    private handleClearCommand(tabID: string) {
        this.mynahUI?.updateStore(tabID, {
            chatItems: [],
        })
        this.connector.clearChat(tabID)
    }

    private handleHelpCommand(tabID: string) {
        // User entered help action, so change the tab type to 'cwc' if it's an unknown tab
        if (this.tabsStorage.getTab(tabID)?.type === 'unknown') {
            this.tabsStorage.updateTabTypeFromUnknown(tabID, 'cwc')
        }

        this.connector.help(tabID)
    }

    private handleFeatureDevCommand(chatPrompt: ChatPrompt, tabID: string, taskName: string) {
        if (!this.isFeatureDevEnabled) {
            return
        }

        const affectedTabId: string | undefined = this.addTab(tabID)
        const realPromptText = chatPrompt.escapedPrompt?.trim() ?? ''

        if (affectedTabId === undefined) {
            this.mynahUI?.notify({
                content: uiComponentsTexts.noMoreTabsTooltip,
                type: NotificationType.WARNING,
            })
            return
        } else {
            this.tabsStorage.updateTabTypeFromUnknown(affectedTabId, 'featuredev')
            this.connector.onKnownTabOpen(affectedTabId)
            this.connector.onUpdateTabType(affectedTabId)

            this.mynahUI?.updateStore(affectedTabId, { chatItems: [] })
            this.mynahUI?.updateStore(
                affectedTabId,
                this.tabDataGenerator.getTabData('featuredev', false, taskName)
            )

            const addInformationCard = (tabId: string) => {
                this.mynahUI?.addChatItem(tabId, {
                    type: ChatItemType.ANSWER,
                    informationCard: {
                        title: "Feature development",
                        description: "Amazon Q Developer Agent for Software Development",
                        icon: MynahIcons.BUG,
                        content: {
                            body: [
                                "I can generate code to accomplish a task or resolve an issue.",
                                "After you provide a task, I will:",
                                "1. Generate code based on your description and the code in your workspace",
                                "2. Provide a list of suggestions for you to review and add to your workspace",
                                "3. If needed, iterate based on your feedback",
                                "",
                                "To learn more, visit the [user guide](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/software-dev.html).",
                            ].join("\n")
                        },
                    },
                })
            };

            if (realPromptText !== '') {
                this.mynahUI?.addChatItem(affectedTabId, {
                    type: ChatItemType.PROMPT,
                    body: realPromptText,
                })

                addInformationCard(affectedTabId)

                this.mynahUI?.addChatItem(affectedTabId, {
                    type: ChatItemType.ANSWER_STREAM,
                    body: '',
                })

                this.mynahUI?.updateStore(affectedTabId, {
                    loadingChat: true,
                    promptInputDisabledState: true,
                })

                this.connector.requestGenerativeAIAnswer(affectedTabId, {
                    chatMessage: realPromptText,
                })
            } else {
                addInformationCard(affectedTabId)
            }
        }
    }

private handleDocCommand(chatPrompt: ChatPrompt, tabID: string, taskName: string) {
        if (!this.isDocEnabled) {
            return
        }

        const affectedTabId: string | undefined = this.addTab(tabID)
        const realPromptText = chatPrompt.escapedPrompt?.trim() ?? ''


        if (affectedTabId === undefined) {
            this.mynahUI?.notify({
                content: uiComponentsTexts.noMoreTabsTooltip,
                type: NotificationType.WARNING,
            })
            return
        } else {
            this.tabsStorage.updateTabTypeFromUnknown(affectedTabId, 'doc')
            this.connector.onKnownTabOpen(affectedTabId)
            this.connector.onUpdateTabType(affectedTabId)

            this.mynahUI?.updateStore(affectedTabId, { chatItems: [] })

            this.mynahUI?.updateStore(
                affectedTabId, {
                    ...this.tabDataGenerator.getTabData('doc', realPromptText === '', taskName),
                    promptInputDisabledState: true
                }
            )

            if (realPromptText !== '') {
                this.mynahUI?.addChatItem(affectedTabId, {
                    type: ChatItemType.PROMPT,
                    body: realPromptText,
                })

                this.mynahUI?.updateStore(affectedTabId, {
                    loadingChat: true,
                    promptInputDisabledState: true,
                })

                void this.connector.requestGenerativeAIAnswer(affectedTabId, {
                    chatMessage: realPromptText,
                })
            }
        }
    }

    private showScanInTab( tabId: string) {
        this.mynahUI?.addChatItem(tabId, {
            type: ChatItemType.PROMPT,
            body: "Run a code review",
        })
        this.mynahUI?.addChatItem(tabId, {
            type: ChatItemType.ANSWER,
            informationCard: {
                title: "/review",
                description: "Included in your Q Developer subscription",
                icon: MynahIcons.BUG,
                content: {
                    body: "Automated code review allowing developers to identify and resolve code quality issues, " +
                        "security vulnerabilities, misconfigurations, and deviations from coding best practices.\n\n" +
                        "For this workflow, Q will:\n1. Review the project or a particular file you select and identify issues before code commit\n" +
                        "2. Provide a list of findings from where you can follow up with Q to find solutions\n3. Generate on-demand code fixes inline\n\n" +
                        "To learn more, check out our [user guide](https://aws.amazon.com/q/developer/)."
                },
            },
        })
        this.connector.scan(tabId)
    }

    private handleCodeScanCommand(tabID: string, eventId?: string) {
        if (!this.isCodeScanEnabled) {
            return
        }

        // Check for existing opened code scan tab
        const existingCodeScanTab = this.tabsStorage.getTabs().find(tab => tab.type === 'codescan')
        if (existingCodeScanTab !== undefined ) {
            this.mynahUI?.selectTab(existingCodeScanTab.id, eventId || "")
            this.connector.onTabChange(existingCodeScanTab.id)

            this.mynahUI?.notify({
                title: "Q - Review",
                content: "Switched to the opened code review tab"
            });
            this.showScanInTab(existingCodeScanTab.id)
            return
        }

        // Add new tab
        const affectedTabId: string | undefined = this.addTab(tabID)

        if (affectedTabId === undefined) {
            this.mynahUI?.notify({
                content: uiComponentsTexts.noMoreTabsTooltip,
                type: NotificationType.WARNING
            })
            return
        } else {
            this.tabsStorage.updateTabTypeFromUnknown(affectedTabId, 'codescan')
            this.connector.onKnownTabOpen(affectedTabId)
            // Clear unknown tab type's welcome message
            this.mynahUI?.updateStore(affectedTabId, {chatItems: []})
            this.mynahUI?.updateStore(affectedTabId, this.tabDataGenerator.getTabData('codescan', true))
            this.mynahUI?.updateStore(affectedTabId, {
                promptInputDisabledState: true,
                promptInputPlaceholder: 'Waiting on your inputs...',
                loadingChat: true,
            })

            this.connector.onTabAdd(affectedTabId)
        }
        this.showScanInTab(affectedTabId)
    }

    private handleCodeTestCommand(chatPrompt: ChatPrompt, tabID: string | undefined, eventId: string | undefined) {
        if (!this.isCodeTestEnabled) {
            return
        }
        const testTabId = this.tabsStorage.getTabs().find((tab) => tab.type === 'codetest')?.id
        const realPromptText = chatPrompt.escapedPrompt?.trim() ?? ''
        if (testTabId !== undefined) {
            this.mynahUI?.selectTab(testTabId, eventId || '')
            this.connector.onTabChange(testTabId)
            this.connector.startTestGen(testTabId, realPromptText)
            return
        }

        const affectedTabId: string | undefined = this.addTab(tabID)

        // if there is no test tab, open a new one
        if (affectedTabId === undefined) {
            this.mynahUI?.notify({
                content: uiComponentsTexts.noMoreTabsTooltip,
                type: NotificationType.WARNING,
            })
            return
        } else {
            this.tabsStorage.updateTabTypeFromUnknown(affectedTabId, 'codetest')
            this.connector.onKnownTabOpen(affectedTabId)
            this.connector.onUpdateTabType(affectedTabId)
            // reset chat history
            this.mynahUI?.updateStore(affectedTabId, {
                chatItems: [],
            })

            // creating a new tab and printing some title
            this.mynahUI?.updateStore(
                 affectedTabId,
                 this.tabDataGenerator.getTabData('codetest', realPromptText === '', 'Q - Test')
             )

            this.connector.startTestGen(affectedTabId, realPromptText)
        }
    }

    // Ref: https://github.com/aws/aws-toolkit-vscode/blob/e9ea8082ffe0b9968a873437407d0b6b31b9e1a5/packages/core/src/amazonq/webview/ui/quickActions/handler.ts#L345
    private addTab(affectedTabId: string | undefined) {
        if (!affectedTabId || !this.mynahUI) {
            return
        }

        const currTab = this.mynahUI.getAllTabs()[affectedTabId]
        const currTabWasUsed =
            (currTab.store?.chatItems?.filter((item) => item.type === ChatItemType.PROMPT).length ?? 0) > 0
        if (currTabWasUsed) {
            affectedTabId = this.mynahUI.updateStore('', {
                loadingChat: true,
                cancelButtonWhenLoading: false,
            })
        } else {
            this.mynahUI?.updateStore(affectedTabId, { promptInputOptions: [], promptTopBarTitle: '' })
        }

        if (affectedTabId && this.isHybridChatEnabled) {
            this.tabsStorage.addTab({
                id: affectedTabId,
                type: 'unknown',
                status: 'free',
                isSelected: true,
            })
        }

        return affectedTabId
    }

    private get mynahUI(): MynahUI | undefined {
        return this.mynahUIRef.mynahUI
    }
}
