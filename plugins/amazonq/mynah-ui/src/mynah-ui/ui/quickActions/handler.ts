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
            case '/help':
                this.handleHelpCommand(tabID)
                break
            case '/transform':
                this.handleCodeTransformCommand(tabID, eventId)
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
