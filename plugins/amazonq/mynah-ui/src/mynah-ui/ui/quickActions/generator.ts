/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { QuickActionCommand, QuickActionCommandGroup } from '@aws/mynah-ui-chat/dist/static'
import { TabType } from '../storages/tabsStorage'
import {MynahIcons} from "@aws/mynah-ui-chat";

// TODO: Need to remove legacy code of isCodeScanEnabled, isCodeTestEnabled, isDocEnabled and isFeatureDevEnabled in followup PR
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
                commands: [
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
            }
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
            codetransform: {
                description: "This command isn't available in /transform",
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
