/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChatItemType, MynahUIDataModel, QuickActionCommandGroup, QuickActionCommand, ChatItem} from '@aws/mynah-ui-chat'
import { TabType } from '../storages/tabsStorage'
import { FollowUpGenerator } from '../followUps/generator'
import { QuickActionGenerator } from '../quickActions/generator'
import { workspaceCommand } from '../commands'

export interface TabDataGeneratorProps {
    isCodeTransformEnabled: boolean
    isCodeScanEnabled: boolean
    highlightCommand?: QuickActionCommand
    profileName?: string
}

export class TabDataGenerator {
    private followUpsGenerator: FollowUpGenerator
    public quickActionsGenerator: QuickActionGenerator
    public highlightCommand?: QuickActionCommand
    profileName?: string

    private tabTitle: Map<TabType, string> = new Map([
        ['unknown', 'Chat'],
        ['cwc', 'Chat'],
        ['codetransform', 'Q - Transform'],
    ])

    private tabInputPlaceholder: Map<TabType, string> = new Map([
        ['unknown', 'Ask a question or enter "/" for quick commands'],
        ['cwc', 'Ask a question or enter "/" for quick commands'],
    ])

    private tabWelcomeMessage: Map<TabType, string> = new Map([
        [
            'unknown',
            `Hi, I'm Amazon Q. I can answer your software development questions. 
        Ask me to explain, debug, or optimize your code. 
        You can enter \`/\` to see a list of quick actions.`,
        ],
        [
            'cwc',
            `Hi, I'm Amazon Q. I can answer your software development questions.
        Ask me to explain, debug, or optimize your code. 
        You can enter \`/\` to see a list of quick actions. Add @workspace at the beginning of your message to enhance Q response with entire workspace files.`,
        ],
        [
            'codetransform',
            `Welcome to Code Transformation!
            **ℹ️ AWS Transform custom now available for Java upgrades. Agentic AI that handles version upgrades, SDK migration, and more, and improves with every execution. [Learn more](https://aws.amazon.com/transform/custom/)**`,
        ],
    ])

    private tabContextCommand: Map<TabType, QuickActionCommandGroup[]> = new Map([
        ['cwc', [workspaceCommand]],
    ])

    constructor(props: TabDataGeneratorProps) {
        this.followUpsGenerator = new FollowUpGenerator()
        this.quickActionsGenerator = new QuickActionGenerator({
            isCodeTransformEnabled: props.isCodeTransformEnabled,
            isCodeScanEnabled: props.isCodeScanEnabled,
        })
        this.highlightCommand = props.highlightCommand
        this.profileName = props.profileName
    }

    private get regionProfileCard(): ChatItem | undefined {
        console.log('[DEBUG] Received profileName:', this.profileName)
        if (!this.profileName) {
            return undefined
        }
        return {
            type: ChatItemType.ANSWER,
            body: `You are using the <b>${this.profileName}</b> profile for this chat period`,
            status: 'info',
            messageId: 'regionProfile',
        }
    }

    public getTabData(tabType: TabType, needWelcomeMessages: boolean, taskName?: string): MynahUIDataModel {
        return {
            tabTitle: taskName ?? this.tabTitle.get(tabType),
            promptInputInfo:
                'Amazon Q Developer uses generative AI. You may need to verify responses. See the [AWS Responsible AI Policy](https://aws.amazon.com/machine-learning/responsible-ai/policy/).',
            quickActionCommands: this.quickActionsGenerator.generateForTab(tabType),
            promptInputPlaceholder: this.tabInputPlaceholder.get(tabType),
            contextCommands: this.getContextCommands(tabType),
            chatItems: needWelcomeMessages
                ? [
                    ...(this.regionProfileCard ? [this.regionProfileCard] : []),
                    {
                          type: ChatItemType.ANSWER,
                          body: this.tabWelcomeMessage.get(tabType),
                      },
                      {
                          type: ChatItemType.ANSWER,
                          followUp: this.followUpsGenerator.generateWelcomeBlockForTab(tabType),
                      },
                  ]
                : [...(this.regionProfileCard ? [this.regionProfileCard] : [])],
        }
    }

    private getContextCommands(tabType: TabType): QuickActionCommandGroup[] | undefined {
        const contextCommands = this.tabContextCommand.get(tabType)

        if (this.highlightCommand) {
            const commandHighlight: QuickActionCommandGroup = {
                groupName: 'Additional Commands',
                commands: [this.highlightCommand],
            }

            if (contextCommands !== undefined) {
                return [...contextCommands, commandHighlight]
            }

            return [commandHighlight]
        }

        return contextCommands
    }
}
