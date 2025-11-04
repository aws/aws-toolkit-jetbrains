/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItemAction, ChatPrompt } from '@aws/mynah-ui-chat'
import { AuthFollowUpType } from '../followUps/generator'
import { ExtensionMessage } from '../commands'
import {getTabCommandFromTabType, isTabType, TabType } from '../storages/tabsStorage'
import {codeScanUserGuide, codeTransformUserGuide} from "../texts/constants";
import {createClickTelemetry, createOpenAgentTelemetry, Trigger} from "../telemetry/actions";

export type WelcomeFollowupType = 'continue-to-chat'

export interface ConnectorProps {
    sendMessageToExtension: (message: ExtensionMessage) => void
    onWelcomeFollowUpClicked: (tabID: string, welcomeFollowUpType: WelcomeFollowupType) => void
    handleCommand: (chatPrompt: ChatPrompt, tabId: string) => void
}
export interface CodeReference {
    licenseName?: string
    repository?: string
    url?: string
    recommendationContentSpan?: {
        start?: number
        end?: number
    }
}

export class Connector {
    private readonly sendMessageToExtension
    private readonly onWelcomeFollowUpClicked
    private readonly handleCommand

    constructor(props: ConnectorProps) {
        this.sendMessageToExtension = props.sendMessageToExtension
        this.onWelcomeFollowUpClicked = props.onWelcomeFollowUpClicked
        this.handleCommand = props.handleCommand
    }

    followUpClicked = (tabID: string, followUp: ChatItemAction): void => {
        if (followUp.type !== undefined && followUp.type === 'continue-to-chat') {
            this.onWelcomeFollowUpClicked(tabID, followUp.type)
        }
    }

    authFollowUpClicked = (tabID: string, tabType: string, authType: AuthFollowUpType): void => {
        this.sendMessageToExtension({
            command: 'auth-follow-up-was-clicked',
            authType,
            tabID,
            tabType,
        })
    }

    onCustomFormAction(
        tabId: string,
        action: {
            id: string
            text?: string | undefined
            formItemValues?: Record<string, string> | undefined
        }
    ) {
        const tabType = action.id.split('-')[2]
        if (!isTabType(tabType)) {
            return
        }

        if (action.id.startsWith('user-guide-')) {
            this.processUserGuideLink(tabType, action.id)
            return
        }

        if (action.id.startsWith('quick-start-')) {
            this.handleCommand(
                {
                    command: getTabCommandFromTabType(tabType),
                },
                tabId
            )

            this.sendMessageToExtension(createOpenAgentTelemetry(tabType, 'quick-start'))
        }
    }

    private processUserGuideLink(tabType: TabType, actionId: string) {
        let userGuideLink = ''
        switch (tabType) {
            case 'codetransform':
                userGuideLink = codeTransformUserGuide
                break
        }

        // e.g. amazonq-explore-user-guide-featuredev
        this.sendMessageToExtension(createClickTelemetry(`amazonq-explore-${actionId}`))

        this.sendMessageToExtension({
            command: 'open-user-guide',
            userGuideLink,
        })
    }
}
