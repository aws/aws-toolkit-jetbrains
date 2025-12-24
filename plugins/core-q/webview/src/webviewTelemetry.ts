// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import {error} from "console";
import {Stage} from "./model";

export class WebviewTelemetry {
    static #instance: WebviewTelemetry
    public static get instance() {
        return (this.#instance ??= new WebviewTelemetry())
    }

    private intentTimestamp: number = 0

    willShowPage(page: Stage) {
        if (page === 'START' || page === 'REAUTH' || page === 'PROFILE_SELECT') {
            this.intentTimestamp = Date.now()
        }
    }

    didShowPage(page: Stage, errorMessage: string | undefined = undefined) {
        // align with other ides
        let module = ''
        if (page === 'START') {
            module = 'login'
        } else if (page === 'REAUTH') {
            module = 'reauth'
        } else if (page === 'PROFILE_SELECT') {
            module = 'selectProfile'
        } else {
            console.log(`didShowPage ${page}`)
            return
        }
        const duration = Date.now() - this.intentTimestamp

        const event: Toolkit_didLoadModule = errorMessage ? {
            metricName: 'toolkit_didLoadModule',
            module: module,
            result: 'Failed',
            reason: errorMessage,
        } : {
            metricName: 'toolkit_didLoadModule',
            module: module,
            result: 'Succeeded',
            duration: duration,
        }

        this.publishTelemetryToIde(event)
    }

    reset() {
        this.intentTimestamp = 0
    }

    private publishTelemetryToIde(event: Toolkit_didLoadModule) {
        console.log(`publishing telemetry event to IDE`, event)
        const eventStr = JSON.stringify(event)
        window.ideApi.postMessage({command: 'webviewTelemetry', event: eventStr})
    }
}

type Toolkit_didLoadModule = {
    metricName: string,
    module: string,
    result: 'Failed' | 'Succeeded',
    reason?: string,
    duration?: number
}
