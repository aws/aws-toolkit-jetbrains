/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import {MynahUIDataModel} from "@aws/mynah-ui-chat";

export type TabStatus = 'free' | 'busy' | 'dead'
const TabTypes = [
    'cwc',
    'featuredev',
    'codetransform',
    'doc',
    'codescan',
    'codetest',
    'agentWalkthrough',
    'welcome',
    'unknown',
] as const
export type TabType = (typeof TabTypes)[number]
export function isTabType(value: string): value is TabType {
    return (TabTypes as readonly string[]).includes(value)
}

export function getTabCommandFromTabType(tabType: TabType): string {
    switch (tabType) {
        case 'featuredev':
            return '/dev'
        case 'codetransform':
            return '/transform'
        case 'doc':
            return '/doc'
        case 'codescan':
            return '/review'
        case 'codetest':
            return '/test'
        default:
            return ''
    }
}
export type TabOpenType = 'click' | 'contextMenu' | 'hotkeys'

const TabTimeoutDuration = 172_800_000 // 48hrs
export interface Tab {
    readonly id: string
    status: TabStatus
    type: TabType
    isSelected: boolean
    openInteractionType?: TabOpenType
}

export class TabsStorage {
    private tabs: Map<string, Tab> = new Map()
    private lastCreatedTabByType: Map<TabType, string> = new Map()
    private lastSelectedTab: Tab | undefined = undefined
    private tabActivityTimers: Record<string, ReturnType<typeof setTimeout>> = {}
    private onTabTimeout?: (tabId: string) => void
    private tabMutator?: (tabId: string, data: MynahUIDataModel) => void

    constructor(props?: { onTabTimeout: (tabId: string) => void, tabMutator: (tabId: string, data: MynahUIDataModel) => void }) {
        this.onTabTimeout = props?.onTabTimeout
        this.tabMutator = props?.tabMutator
    }

    public addTab(tab: Tab) {
        if (this.tabs.has(tab.id)) {
            return
        }
        this.tabs.set(tab.id, tab)
        this.lastCreatedTabByType.set(tab.type, tab.id)
        if (tab.isSelected) {
            this.setSelectedTab(tab.id)
        }
    }

    public deleteTab(tabID: string) {
        if (this.tabActivityTimers[tabID] !== undefined) {
            clearTimeout(this.tabActivityTimers[tabID])
            delete this.tabActivityTimers[tabID]
        }
        // Reset the last selected tab if the deleted one is selected
        if (tabID === this.lastSelectedTab?.id) {
            this.lastSelectedTab = undefined
        }
        this.tabs.delete(tabID)
    }

    public getTab(tabID: string): Tab | undefined {
        return this.tabs.get(tabID)
    }

    public getTabs(): Tab[] {
        return Array.from(this.tabs.values())
    }

    public isTabDead(tabID: string): boolean {
        return this.tabs.get(tabID)?.status === 'dead'
    }

    public updateTabStatus(tabID: string, tabStatus: TabStatus) {
        const currentTabValue = this.tabs.get(tabID)
        if (currentTabValue === undefined || currentTabValue.status === 'dead') {
            return
        }
        currentTabValue.status = tabStatus
        this.tabs.set(tabID, currentTabValue)
    }

    public updateTabContent(tabId: string, dataModel:  MynahUIDataModel) {
        const currentTabValue = this.tabs.get(tabId)
        if (currentTabValue === undefined || currentTabValue.status === 'dead') {
            return
        }

        if (this.tabMutator) {
            this.tabMutator(tabId, dataModel)
        }
    }

    public updateTabTypeFromUnknown(tabID: string, tabType: TabType) {
        const currentTabValue = this.tabs.get(tabID)
        if (
            currentTabValue === undefined ||
            (currentTabValue.type !== 'unknown' && currentTabValue.type !== 'welcome')
        ) {
            return
        }

        currentTabValue.type = tabType

        this.tabs.set(tabID, currentTabValue)
        this.lastCreatedTabByType.set(tabType, tabID)
    }

    public resetTabTimer(tabID: string) {
        if (this.onTabTimeout !== undefined) {
            if (this.tabActivityTimers[tabID] !== undefined) {
                clearTimeout(this.tabActivityTimers[tabID])
            }
            this.tabActivityTimers[tabID] = setTimeout(() => {
                if (this.onTabTimeout !== undefined) {
                    this.updateTabStatus(tabID, 'dead')
                    this.onTabTimeout(tabID)
                }
            }, TabTimeoutDuration)
        }
    }

    public setSelectedTab(tabID: string): string | undefined {
        const prevSelectedTab = this.lastSelectedTab
        const prevSelectedTabID = this.lastSelectedTab?.id
        if (prevSelectedTab !== undefined) {
            prevSelectedTab.isSelected = false
            this.tabs.set(prevSelectedTab.id, prevSelectedTab)
        }

        const newSelectedTab = this.tabs.get(tabID)
        if (newSelectedTab === undefined) {
            return prevSelectedTabID
        }

        newSelectedTab.isSelected = true
        this.tabs.set(newSelectedTab.id, newSelectedTab)
        this.lastSelectedTab = newSelectedTab
        return prevSelectedTabID
    }

    public getSelectedTab(): Tab | undefined {
        return this.lastSelectedTab
    }
}
