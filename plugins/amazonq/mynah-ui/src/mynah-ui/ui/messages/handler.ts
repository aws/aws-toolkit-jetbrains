/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItemType, ChatPrompt, MynahUI } from '@aws/mynah-ui-chat'
import { Connector } from '../connector'
import { TabsStorage } from '../storages/tabsStorage'
import {MynahUIRef} from "../main";

export interface TextMessageHandlerProps {
    mynahUIRef: MynahUIRef
    connector: Connector
    tabsStorage: TabsStorage
}

export class TextMessageHandler {
    private mynahUIRef: MynahUIRef
    private connector: Connector
    private tabsStorage: TabsStorage

    constructor(props: TextMessageHandlerProps) {
        this.mynahUIRef = props.mynahUIRef
        this.connector = props.connector
        this.tabsStorage = props.tabsStorage
    }

    public handle(chatPrompt: ChatPrompt, tabID: string) {
        this.tabsStorage.updateTabTypeFromUnknown(tabID, 'cwc')
        this.tabsStorage.resetTabTimer(tabID)
        this.connector.onUpdateTabType(tabID)
        this.mynahUI?.addChatItem(tabID, {
            type: ChatItemType.PROMPT,
            body: chatPrompt.escapedPrompt,
        })

        this.mynahUI?.updateStore(tabID, {
            loadingChat: true,
            cancelButtonWhenLoading: false,
            promptInputDisabledState: true,
        })

        this.tabsStorage.updateTabStatus(tabID, 'busy')

        this.connector
            .requestGenerativeAIAnswer(tabID, {
                chatMessage: chatPrompt.prompt ?? '',
                chatCommand: chatPrompt.command,
            })
            .then(() => {})
    }

    private get mynahUI(): MynahUI | undefined {
        return this.mynahUIRef.mynahUI
    }
}
