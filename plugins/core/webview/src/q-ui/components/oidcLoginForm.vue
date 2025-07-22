<!-- Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div class="font-amazon" @keydown.enter="handleContinueClick">
        <div class="bottom-small-gap">
            <div class="title">Sign into Amazon Q Developer Pro</div>
        </div>
        <div>
            <div class="title no-bold">Work email</div>
            <input
                class="url-input font-amazon"
                type="text"
                id="oidcEmail"
                name="oidcEmail"
                v-model="oidcEmail"
                tabindex="0"
                spellcheck="false"
                placeholder="Work email"
            />
        </div>
        <div>
            <div class="hint invalid-start-url" v-if="metadataCallErrorMessage !== ''">{{`${metadataCallErrorMessage}`}}</div>
        </div>
        <br/>
        <br/><br/>
        <button
            class="login-flow-button continue-button font-amazon"
            :disabled="!isInputValid || isHandlingInput"
            v-on:click="handleContinueClick()"
            tabindex="-1"
        >
            Continue
        </button>
    </div>
</template>

<script lang="ts">
import {defineComponent} from 'vue'
import {Feature, ExternalIdC} from "../../model";
import * as crypto from "node:crypto";

export default defineComponent({
    name: "oidcForm",
    data() {
        return {
            metadataCallErrorMessage: '',
            isHandlingInput: false
        }
    },
    computed: {
        feature(): Feature {
            return this.$store.state.feature
        },
        oidcEmail: {
            get() {
                return this.$store.state.lastLoginExtIdcInfo.oidcEmail ?? '';
            },
            set(value: string) {
                window.ideClient.updateLastLoginExtIdcInfo({
                    ...this.$store.state.lastLoginExtIdcInfo,
                    oidcEmail: value
                })
            }
        },
        isInputValid: {
            get() {
                return /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/.test(this.oidcEmail)
            },
            set() {}
        },
    },
    methods: {
        resetResponse() {
            this.$store.state.getLoginMetadataResponse = undefined
        },
        async handleContinueClick() {
            // disable button
            this.$data.isHandlingInput = true
            try {
                await this.getLoginMetadata()
                // reset fields here before redirection
                this.$data.metadataCallErrorMessage = ''
                this.$data.isHandlingInput = false
                this.$emit('login', new ExternalIdC(
                    this.$store.state.getLoginMetadataResponse?.issuerUrl ?? '',
                    this.$store.state.getLoginMetadataResponse?.clientId ?? ''
                ))
            } catch (e: any) {
                this.$data.isHandlingInput = false
                this.$data.metadataCallErrorMessage = e
            }
        },
        async getLoginMetadata() {
            this.resetResponse()
            // retry every 100ms: 10 seconds
            const MAX_RETRIES = 100
            // use a timestamp as uuid...crypto lib doesn't work with webpack
            const uuid = Date.now().toString()
            window.ideApi.postMessage({ command: 'getExtIdPMetadata', email: this.oidcEmail, uuid })
            // poll state object to see if request was successful
            await new Promise<void>(async (resolve, reject) => {
                for (let i = 0; i < MAX_RETRIES; i++) {
                    const response = this.$store.state.getLoginMetadataResponse
                    // matches request uuid
                    if (response && response.uuid === uuid) {
                        if (response.error) {
                            reject(`getLoginMetadata call failed: ${response.error.type} : ${response.error.message}`)
                        } else {
                            resolve()
                        }
                    }
                    await new Promise(res => {
                        setTimeout(res, 100)
                    })
                }
                this.$store.state.getLoginMetadataResponse = {
                    error: {
                        type: 'Timeout',
                        message: 'No response returned from getLoginMetadata API'
                    },
                    uuid,
                }
                reject("getLoginMetadata call did not return")
            })
        },
    },
    mounted() {
        document.getElementById("oidcEmail")?.focus()
    }
})
</script>

<style scoped lang="scss">
.hint {
    color: #909090;
    margin-bottom: 5px;
    margin-top: 5px;
    font-size: 12px;
}

.invalid-start-url {
    color: red !important;
    margin-left: 3px;
}

/* Theme specific styles */
body.jb-dark {
    .url-input, .region-select, .sso-profile {
        background-color: #252526;
        color: white;
        border: none;
    }
}

body.jb-light {
    .url-input, .region-select, .sso-profile {
        color: black;
        border: 1px solid #c9ccd6;
    }
}
</style>
