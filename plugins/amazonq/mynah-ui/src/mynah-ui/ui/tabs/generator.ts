/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItemType, MynahUIDataModel, QuickActionCommandGroup } from '@aws/mynah-ui-chat'
import { TabType } from '../storages/tabsStorage'
import { FollowUpGenerator } from '../followUps/generator'
import { QuickActionGenerator } from '../quickActions/generator'
import { workspaceCommand } from '../commands'

export interface TabDataGeneratorProps {
    isFeatureDevEnabled: boolean
    isCodeTransformEnabled: boolean
    isDocEnabled: boolean
    isCodeScanEnabled: boolean
    isCodeTestEnabled: boolean
}

export class TabDataGenerator {
    private followUpsGenerator: FollowUpGenerator
    public quickActionsGenerator: QuickActionGenerator

    private tabTitle: Map<TabType, string> = new Map([
        ['unknown', 'Chat'],
        ['cwc', 'Chat'],
        ['featuredev', 'Q - Dev'],
        ['codetransform', 'Q - Transform'],
        ['doc', 'Q - Documentation'],
        ['codescan', 'Q - Review'],
        ['codetest', 'Q - Test'],
    ])

    private tabInputPlaceholder: Map<TabType, string> = new Map([
        ['unknown', 'Ask a question or enter "/" for quick commands'],
        ['cwc', 'Ask a question or enter "/" for quick commands'],
        ['featuredev', 'Describe your task or issue in detail'],
        ['doc', 'Ask Amazon Q to generate documentation for your project'],
        ['codescan', 'Waiting for your inputs...'],
        ['codetest', 'Specify a function(s) in the current file(optional)'],
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
            `Hi! I'm the Amazon Q Developer Agent for software development. 

I can generate code to implement new functionality across your workspace. To get started, describe the task you're trying to accomplish, and I'll generate code to implement it. If you want to make changes to the code, you can tell me what to improve and I'll generate new code based on your feedback. 

What would you like to work on?`,
        ],
        [
            'codetransform',
            `Welcome to Code Transformation!`,
        ],
        [
            'doc',
         `Welcome to doc generation!\n\nI can help generate documentation for your code. To get started, choose what type of doc update you'd like to make.`,
        ],
        [
            'codetest',
            `Welcome to Amazon Q Unit Test Generation. I can help you generate unit tests for your active file.`,
        ]
    ])

    private tabContextCommand: Map<TabType, QuickActionCommandGroup[]> = new Map([
        ['cwc', [workspaceCommand]],
    ])

    constructor(props: TabDataGeneratorProps) {
        this.followUpsGenerator = new FollowUpGenerator()
        this.quickActionsGenerator = new QuickActionGenerator({
            isFeatureDevEnabled: props.isFeatureDevEnabled,
            isCodeTransformEnabled: props.isCodeTransformEnabled,
            isDocEnabled: props.isDocEnabled,
            isCodeScanEnabled: props.isCodeScanEnabled,
            isCodeTestEnabled: props.isCodeTestEnabled,
        })
    }

    public getTabData(tabType: TabType, needWelcomeMessages: boolean, taskName?: string): MynahUIDataModel {
        return {
            tabTitle: taskName ?? this.tabTitle.get(tabType),
            promptInputInfo:
                'Amazon Q Developer uses generative AI. You may need to verify responses. See the [AWS Responsible AI Policy](https://aws.amazon.com/machine-learning/responsible-ai/policy/). Amazon Q Developer processes data across all US Regions. See [here](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/cross-region-inference.html) for more info. Amazon Q may retain chats to provide and maintain the service.',
            quickActionCommands: this.quickActionsGenerator.generateForTab(tabType),
            promptInputPlaceholder: this.tabInputPlaceholder.get(tabType),
            contextCommands: this.tabContextCommand.get(tabType),
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
