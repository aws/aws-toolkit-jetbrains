// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import {Store} from "vuex";
import {IdcInfo, Region, Stage, State} from "./model";

export class IdeClient {
    constructor(private readonly store: Store<State>) {}

    // TODO: design and improve the API here

    updateStage(stage: Stage) {
        this.store.commit('setStage', stage)
    }

    loginCodeCatalyst() {
        this.updateStage('TOOLKIT_BEARER')
        console.log('about to set feature to...', 'CodeCatalyst')
        this.store.commit("setFeature", 'CodeCatalyst')
    }

    updateIsConnected(isConnected: boolean) {
        this.store.commit("setIsConnected", isConnected)
    }

    updateSsoRegions(regions: Region[]) {
        console.log(regions)
        this.store.commit('setSsoRegions', regions)
    }

    updateAuthorization(code: string) {
        console.log('authorization code: ', code)
        this.store.commit('setAuthorizationCode', code)
    }

    updateLastLoginIdcInfo(idcInfo: IdcInfo) {
        this.store.commit('setLastLoginIdcInfo', idcInfo)
    }

    reset() {
        this.store.commit('reset')
    }

    cancelLogin(): void {
        this.reset()
        window.ideApi.postMessage({ command: 'cancelLogin' })
    }
}
