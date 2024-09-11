/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItemType, MynahUIDataModel } from '@aws/mynah-ui-chat'
import { TabType } from '../storages/tabsStorage'
import { FollowUpGenerator } from '../followUps/generator'
import { QuickActionGenerator } from '../quickActions/generator'

export interface TabDataGeneratorProps {
    isFeatureDevEnabled: boolean
    isCodeTransformEnabled: boolean
}

export class TabDataGenerator {
    private followUpsGenerator: FollowUpGenerator
    public quickActionsGenerator: QuickActionGenerator

    private tabTitle: Map<TabType, string> = new Map([
        ['unknown', 'Chat'],
        ['cwc', 'Chat'],
        ['featuredev', 'Q - Dev'],
        ['codetransform', 'Q - Transform'],
    ])

    private tabInputPlaceholder: Map<TabType, string> = new Map([
        ['unknown', 'Ask a question or enter "/" for quick commands'],
        ['cwc', 'Ask a question or enter "/" for quick commands'],
        ['featuredev', 'Describe your task or issue in as much detail as possible'],
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
            'featuredev',
            `I can generate code to implement new functionality across your workspace. To get started, describe the task you're trying to accomplish, and I'll generate code. If you want to make changes, you can provide feedback and I'll regenerate code.
            
            What would you like work on?
`,
        ],
        [
            'codetransform',
            `Welcome to Code Transformation!

I can help you upgrade your Java 8 and 11 codebases to Java 17.
`,
        ],
    ])

    constructor(props: TabDataGeneratorProps) {
        this.followUpsGenerator = new FollowUpGenerator()
        this.quickActionsGenerator = new QuickActionGenerator({
            isFeatureDevEnabled: props.isFeatureDevEnabled,
            isCodeTransformEnabled: props.isCodeTransformEnabled,
        })
    }

    public getTabData(tabType: TabType, needWelcomeMessages: boolean, taskName?: string): MynahUIDataModel {
        return {
            tabTitle: taskName ?? this.tabTitle.get(tabType),
            promptInputInfo:
                'Use of Amazon Q is subject to the [AWS Responsible AI Policy](https://aws.amazon.com/machine-learning/responsible-ai/policy/).',
            quickActionCommands: this.quickActionsGenerator.generateForTab(tabType),
            promptInputPlaceholder: this.tabInputPlaceholder.get(tabType),
            contextCommands: [
                {
                    groupName: 'Mention code',
                    commands: [
                        {
                            command: '@workspace',
                            description: '(BETA) Reference all code in workspace.',
                        },
                    ],
                },
            ],
            chatItems: needWelcomeMessages
                ? [
                      {
                          type: ChatItemType.ANSWER,
                          body: this.tabWelcomeMessage.get(tabType),
                      },
                      {
                          type: ChatItemType.ANSWER,
                          followUp: this.followUpsGenerator.generateWelcomeBlockForTab(tabType),
                      },
                  ]
                : [],
        }
    }
}
