/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItemContent, ChatItemType, MynahIcons, MynahUIDataModel } from '@aws/mynah-ui-chat'

function createdTabbedData(examples: string[], agent: string): ChatItemContent['tabbedContent'] {
    const exampleText = examples.map((example) => `- ${example}`).join('\n')
    return [
        {
            label: 'Examples',
            value: 'examples',
            content: {
                body: `**Example use cases:**\n${exampleText}\n\nEnter ${agent} in Q Chat to get started`,
            },
        },
    ]
}

export const agentWalkthroughDataModel: MynahUIDataModel = {
    tabBackground: false,
    compactMode: false,
    tabTitle: 'Explore',
    promptInputVisible: false,
    tabHeaderDetails: {
        icon: MynahIcons.ASTERISK,
        title: 'Amazon Q Developer agents capabilities',
        description: '',
    },
    chatItems: [
        {
            type: ChatItemType.ANSWER,
            hoverEffect: true,
            body: `### Transformation
Upgrade library and language versions in your codebase.
`,
            icon: MynahIcons.TRANSFORM,
            footer: {
                tabbedContent: createdTabbedData(
                    ['Upgrade Java language and dependency versions', 'Convert embedded SQL code in Java apps'],
                    '/transform'
                ),
            },
            buttons: [
                {
                    status: 'clear',
                    id: 'user-guide-codetransform',
                    disabled: false,
                    text: 'Read user guide',
                },
                {
                    status: 'main',
                    disabled: false,
                    flash: 'once',
                    fillState: 'hover',
                    icon: MynahIcons.RIGHT_OPEN,
                    id: 'quick-start-codetransform',
                    text: `Quick start with **/transform**`,
                },
            ],
        },
    ],
}
