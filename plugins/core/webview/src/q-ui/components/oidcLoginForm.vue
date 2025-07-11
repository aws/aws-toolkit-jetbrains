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
        <br/>
        <br/><br/>
        <button
            class="login-flow-button continue-button font-amazon"
            :disabled="!isInputValid"
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

export default defineComponent({
    name: "oidcForm",
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
                console.log(this.oidcEmail)
                return /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/.test(this.oidcEmail)
            },
            set() {}
        },
    },
    methods: {
        async handleContinueClick() {
            // if (!this.isInputValid) {
            //     return
            // }
            this.$emit('login', new ExternalIdC(this.oidcEmail))
        }
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
