/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChatPrompt, MynahUI, QuickActionCommand, QuickActionCommandGroup} from '@aws/mynah-ui-chat'
import { isTabType } from './ui/storages/tabsStorage'
import { WebviewUIHandler } from './ui/main'
import { TabDataGenerator } from './ui/tabs/generator'
import { ChatClientAdapter, ChatEventHandler } from '@aws/chat-client'
import { FqnExtractor } from "./fqn/extractor";

export * from "./ui/main";

declare global {
    interface Window { fqnExtractor: FqnExtractor; }
}

window.fqnExtractor = new FqnExtractor();

export const initiateAdapter = (showWelcomePage: boolean,
                             disclaimerAcknowledged: boolean,
                             isFeatureDevEnabled: boolean,
                             isCodeTransformEnabled: boolean,
                             isDocEnabled: boolean,
                             isCodeScanEnabled: boolean,
                             isCodeTestEnabled: boolean,
                             ideApiPostMessage: (message: any) => void,
                             profileName?: string) : HybridChatAdapter => {
    return new HybridChatAdapter(showWelcomePage, disclaimerAcknowledged, isFeatureDevEnabled, isCodeTransformEnabled, isDocEnabled, isCodeScanEnabled, isCodeTestEnabled, ideApiPostMessage, profileName)
}

export class HybridChatAdapter implements ChatClientAdapter {
    private uiHandler?: WebviewUIHandler

    private mynahUIRef?: { mynahUI: MynahUI}

    constructor(

        private showWelcomePage: boolean,
        private disclaimerAcknowledged: boolean,
        private isFeatureDevEnabled: boolean,
        private isCodeTransformEnabled: boolean,
        private isDocEnabled: boolean,
        private isCodeScanEnabled: boolean,
        private isCodeTestEnabled: boolean,
        private ideApiPostMessage: (message: any) => void,
        private profileName?: string,

    ) {}

    /**
     * First we create the ui handler to get the props, then once mynah UI gets created flare will re-inject the
     * mynah UI instance on the hybrid chat adapter
     */
    createChatEventHandler(mynahUIRef: { mynahUI: MynahUI }): ChatEventHandler {
        this.mynahUIRef = mynahUIRef

        this.uiHandler = new WebviewUIHandler({
            postMessage: this.ideApiPostMessage,
            mynahUIRef: this.mynahUIRef,
            showWelcomePage: this.showWelcomePage,
            disclaimerAcknowledged: this.disclaimerAcknowledged,
            isFeatureDevEnabled: this.isFeatureDevEnabled,
            isCodeTransformEnabled: this.isCodeTransformEnabled,
            isDocEnabled: this.isDocEnabled,
            isCodeScanEnabled: this.isCodeScanEnabled,
            isCodeTestEnabled: this.isCodeTestEnabled,
            profileName: this.profileName,
            hybridChat: true,
        })

        return this.uiHandler.mynahUIProps
    }

    isSupportedTab(tabId: string): boolean {
        const tabType = this.uiHandler?.tabsStorage.getTab(tabId)?.type
        if (!tabType) {
            return false
        }
        return isTabType(tabType) && tabType !== 'cwc'
    }

    async handleMessageReceive(message: MessageEvent): Promise<void> {
        if (this.uiHandler) {
            return this.uiHandler?.connector?.handleMessageReceive(message)
        }

        console.error('unknown message: ', message.data)
    }

    isSupportedQuickAction(command: string): boolean {
        return (
            command === '/dev' ||
            command === '/test' ||
            command === '/review' ||
            command === '/doc' ||
            command === '/transform'
        )
    }

    handleQuickAction(prompt: ChatPrompt, tabId: string, eventId: string | undefined): void {
        return this.uiHandler?.quickActionHandler?.handleCommand(prompt, tabId, eventId)
    }

    get initialQuickActions(): QuickActionCommandGroup[] {
        const tabDataGenerator = new TabDataGenerator({
            isFeatureDevEnabled: this.isFeatureDevEnabled,
            isCodeTransformEnabled: this.isCodeTransformEnabled,
            isDocEnabled: this.isDocEnabled,
            isCodeScanEnabled: this.isCodeScanEnabled,
            isCodeTestEnabled: this.isCodeTestEnabled,
            profileName: this.profileName
        })
        return tabDataGenerator.quickActionsGenerator.generateForTab('cwc') ?? []
    }
}


