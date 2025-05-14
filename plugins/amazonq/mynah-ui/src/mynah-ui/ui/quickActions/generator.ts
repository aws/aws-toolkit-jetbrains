/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { QuickActionCommand, QuickActionCommandGroup } from '@aws/mynah-ui-chat/dist/static'
import { TabType } from '../storages/tabsStorage'
import {MynahIcons} from "@aws/mynah-ui-chat";

export interface QuickActionGeneratorProps {
    isFeatureDevEnabled: boolean
    isCodeTransformEnabled: boolean
    isDocEnabled: boolean
    isCodeScanEnabled: boolean
    isCodeTestEnabled: boolean
}

export class QuickActionGenerator {
    public isFeatureDevEnabled: boolean
    public isCodeTransformEnabled: boolean
    public isDocEnabled: boolean
    public isCodeScanEnabled: boolean
    public isCodeTestEnabled: boolean

    constructor(props: QuickActionGeneratorProps) {
        this.isFeatureDevEnabled = props.isFeatureDevEnabled
        this.isCodeTransformEnabled = props.isCodeTransformEnabled
        this.isDocEnabled = props.isDocEnabled
        this.isCodeScanEnabled = props.isCodeScanEnabled
        this.isCodeTestEnabled = props.isCodeTestEnabled
    }

    public generateForTab(tabType: TabType): QuickActionCommandGroup[] {
        // agentWalkthrough is static and doesn't have any quick actions
        if (tabType === 'agentWalkthrough') {
            return []
        }

        const quickActionCommands = [
            {
                groupName: `Q Developer Agent for <b>Software Development</b>`,
                commands: [
                    ...(this.isFeatureDevEnabled
                        ? [
                              {
                                  command: '/dev',
                                  icon: MynahIcons.CODE_BLOCK,
                                  placeholder: 'Describe your task or issue in as much detail as possible',
                                  description: 'Generate code to make a change in your project',
                              },
                          ]
                        : []),
                        ...(this.isDocEnabled
                            ? [
                                {
                                    command: '/doc',
                                    icon: MynahIcons.FILE,
                                    description: 'Generate documentation for your code',
                                },
                            ]
                            : []),
                    ...(this.isCodeScanEnabled
                        ? [
                            {
                                command: '/review',
                                icon: MynahIcons.BUG,
                                description: 'Identify and fix code issues before committing'
                            }
                        ]
                        : []),
                    ...(this.isCodeTestEnabled
                        ? [
                            {
                                command: '/test',
                                icon: MynahIcons.CHECK_LIST,
                                placeholder: 'Specify a function(s) in the current file(optional)',
                                description: 'Generate unit tests',
                            },
                        ]
                        : []),
                ],
            },
            {
                groupName: `Q Developer Agent for <b>Code Transformation</b>`,
                commands:[
                    ...(this.isCodeTransformEnabled
                        ? [
                            {
                                command: '/transform',
                                icon: MynahIcons.TRANSFORM,
                                description: 'Transform your Java project',
                            },
                        ]
                        : []),
                ],
            },
            {
                groupName: 'Quick Actions',
                commands: [
                    {
                        command: '/help',
                        icon: MynahIcons.HELP,
                        description: 'Learn more about Amazon Q',
                    },
                    {
                        command: '/clear',
                        icon: MynahIcons.TRASH,
                        description: 'Clear this session',
                    },
                ],
            },
        ].filter((section) => section.commands.length > 0)

        const commandUnavailability: Record<
            Exclude<TabType, 'agentWalkthrough'>,
            {
                description: string
                unavailableItems: string[]
            }
        > = {
            cwc: {
                description: '',
                unavailableItems: [],
            },
            featuredev: {
                description: "This command isn't available in /dev",
                unavailableItems: ['/dev', '/transform', '/doc', '/help', '/clear', '/review', '/test'],
            },
            codetransform: {
                description: "This command isn't available in /transform",
                unavailableItems: ['/help', '/clear'],
            },
            codescan: {
                description: "This command isn't available in /review",
                unavailableItems: ['/help', '/clear'],
            },
            codetest: {
                description: "This command isn't available in /test",
                unavailableItems: ['/help', '/clear'],
            },
            doc: {
                description: "This command isn't available in /doc",
                unavailableItems: ['/help', '/clear'],
            },
            testgen: {
                description: "This command isn't available",
                unavailableItems: ['/help', '/clear'],
            },
            welcome: {
                description: '',
                unavailableItems: ['/clear'],
            },
            unknown: {
                description: '',
                unavailableItems: [],
            },
        }

        return quickActionCommands.map((commandGroup: QuickActionCommandGroup) => {
            return {
                groupName: commandGroup.groupName,
                commands: commandGroup.commands.map((commandItem: QuickActionCommand) => {
                    const commandNotAvailable = commandUnavailability[tabType].unavailableItems.includes(
                        commandItem.command
                    )
                    return {
                        ...commandItem,
                        disabled: commandNotAvailable,
                        description: commandNotAvailable
                            ? commandUnavailability[tabType].description
                            : commandItem.description,
                    }
                }) as QuickActionCommand[],
            }
        }) as QuickActionCommandGroup[]
    }
}
