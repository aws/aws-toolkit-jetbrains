// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import {Store} from "vuex";
import {
    IdcInfo,
    State,
    AuthSetupMessageFromIde,
    ListProfileResult,
    ListProfileSuccessResult,
    ListProfileFailureResult, ListProfilePendingResult, ListProfilesMessageFromIde, ExtIdcInfo, GetLoginMetadataResponse
} from "./model";
import {WebviewTelemetry} from './webviewTelemetry'

export class IdeClient {
    constructor(private readonly store: Store<State>) {}

    // TODO: design and improve the API here

    prepareUi(state: AuthSetupMessageFromIde | ListProfilesMessageFromIde) {
        WebviewTelemetry.instance.reset()
        console.log('browser is preparing UI with state ', state)
        // hack as window.onerror don't have access to vuex store
        void ((window as any).uiState = state.stage)
        WebviewTelemetry.instance.willShowPage(state.stage)

        this.store.commit('setStage', state.stage)

        switch (state.stage) {
            case "PROFILE_SELECT":
                this.handleProfileSelectMessage(state as ListProfilesMessageFromIde)
                break

            default:
                this.handleAuthSetupMessage(state as AuthSetupMessageFromIde)
        }
    }

    private handleProfileSelectMessage(msg: ListProfilesMessageFromIde) {
        let result: ListProfileResult | undefined
        switch (msg.status) {
            case 'succeeded':
                result = new ListProfileSuccessResult(msg.profiles)
                break
            case 'failed':
                result = new ListProfileFailureResult(msg.errorMessage)
                break
            case 'pending':
                result = new ListProfilePendingResult()
                break
        }
        this.store.commit('setProfilesResult', result)
    }

    private handleAuthSetupMessage(msg: AuthSetupMessageFromIde) {
        this.store.commit('setSsoRegions', msg.regions)
        this.updateLastLoginIdcInfo(msg.idcInfo)
        this.store.commit("setCancellable", msg.cancellable)
        this.store.commit("setFeature", msg.feature)
        const existConnections = msg.existConnections.map(it => {
            return {
                sessionName: it.sessionName,
                startUrl: it.startUrl,
                region: it.region,
                scopes: it.scopes,
                id: it.id
            }
        })

        this.store.commit("setExistingConnections", existConnections)
        this.updateAuthorization(undefined)
    }

    updateAuthorization(code: string | undefined) {
        this.store.commit('setAuthorizationCode', code)
        // TODO: mutage stage to AUTHing here probably makes life easier
    }

    updateLastLoginIdcInfo(idcInfo: IdcInfo) {
        this.store.commit('setLastLoginIdcInfo', idcInfo)
    }

    updateLastLoginExtIdcInfo(idcInfo: ExtIdcInfo) {
        this.store.commit('setLastLoginExtIdcInfo', idcInfo)
    }

    reset() {
        this.store.commit('setStage', 'START')
    }

    cancelLogin(): void {
        // this.reset()
        this.store.commit('setStage', 'START')
        window.ideApi.postMessage({ command: 'cancelLogin' })
    }

    returnLoginMetadataResponse(response: GetLoginMetadataResponse): void {
        this.store.commit('setLoginMetadataResponse', response)
    }
}
