<!-- Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div class="font-amazon" @keydown.enter="handleContinueClick">
        <div class="bottom-small-gap">
            <div class="title">Sign in with SSO:</div>
            <div class="code-catalyst-login" v-if="app === 'TOOLKIT'">
                <div class="hint">
                    Using CodeCatalyst with AWS Builder ID?
                    <a href="#" @click="handleCodeCatalystSignin()">Skip to sign-in.</a>
                </div>
            </div>
        </div>
        <div>
            <div class="title no-bold">Start URL</div>
            <div class="hint">URL for your organization, provided by an admin or help desk</div>
            <input
                class="url-input font-amazon"
                type="text"
                id="startUrl"
                name="startUrl"
                v-model="startUrl"
                @change="handleUrlInput"
                tabindex="0"
                spellcheck="false"
            />
        </div>
        <div>
            <div class="hint invalid-start-url" v-if="!isStartUrlValid && this.startUrl !== ''">Invalid Start URL format</div>
        </div>
        <br/>
        <div>
            <div class="title no-bold">Region</div>
            <div class="hint">AWS Region that hosts identity directory</div>
            <div class="region-select" :class="{ 'is-open': isOpen }">
                <div class="select-trigger"
                     @click="toggleDropdown"
                     tabindex="0"
                     @keydown.space.prevent="toggleDropdown"
                     @keydown.enter.prevent="toggleDropdown">
                    <template v-for="region in regions" :key="region.id">
                        <span v-if="region.id === selectedRegion">{{ region.name }} ({{ region.id }})</span>
                    </template>
                    <span v-if="!selectedRegion">Select a Region</span>
                </div>
                <div class="options-container" v-if="isOpen">
                    <div class="option"
                         v-for="region in regions"
                         :key="region.id"
                         @click="selectRegion({regionId : region.id})"
                         :class="{ 'selected': region.id === selectedRegion }">
                        {{ `${region.name} (${region.id})` }}
                    </div>
                </div>
            </div>
        </div>
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
import {Feature, Region, IdC, BuilderId} from "../../model";

export default defineComponent({
    name: "ssoForm",
    props: {
        app: String
    },
    data() {
        return {
            startUrlRegex: /^https:\/\/(([\w-]+(?:\.gamma)?\.awsapps\.com\/start(?:-beta|-alpha)?[\/#]?)|(start\.(?:us-gov-home|us-gov-east-1\.us-gov-home|us-gov-west-1\.us-gov-home)\.awsapps\.com|start\.(?:home|cn-north-1\.home|cn-northwest-1\.home)\.awsapps\.cn)\/directory\/[\w-]+[\/#]?)$/,
            issueUrlRegex: /^https:\/\/([\w-]+\.)?identitycenter\.(amazonaws\.com|amazonaws\.com\.cn|us-gov\.amazonaws\.com)\/[\w\/-]+[\/#]?$/,
            isOpen: false,
        }
    },
    computed: {
        regions(): Region[] {
            return this.$store.state.ssoRegions
        },
        feature(): Feature {
            return this.$store.state.feature
        },
        startUrl: {
            get() {
                return this.$store.state.lastLoginIdcInfo.startUrl;
            },
            set(value: string) {
                window.ideClient.updateLastLoginIdcInfo({
                    ...this.$store.state.lastLoginIdcInfo,
                    startUrl: value
                })
            }
        },
        selectedRegion: {
            get() {
                return this.$store.state.lastLoginIdcInfo.region;
            },
            set(value: string) {
                window.ideClient.updateLastLoginIdcInfo({
                    ...this.$store.state.lastLoginIdcInfo,
                    region: value
                })
            }
        },
        isStartUrlValid: {
            get() {
                return this.startUrlRegex.test(this.startUrl) || this.issueUrlRegex.test(this.startUrl)
            },
            set() {}
        },
        isRegionValid: {
            get() {
                return this.selectedRegion != "";
            },
            set() {}
        },
        isInputValid: {
            get() {
                return this.isStartUrlValid && this.isRegionValid
            },
            set() {}
        }
    },
    methods: {
        handleUrlInput() {
            this.isStartUrlValid = this.startUrlRegex.test(this.startUrl) || this.issueUrlRegex.test(this.startUrl)
        },
        handleRegionInput() {
            this.isRegionValid = this.selectedRegion != "";
        },
        toggleDropdown() {
            this.isOpen = !this.isOpen;
        },
        selectRegion({regionId}: { regionId: any }){
            this.selectedRegion = regionId;
            this.isOpen = false;
            this.handleRegionInput();
        },
        async handleContinueClick() {
            if (!this.isInputValid) {
                return
            }

            // To make our lives easier with telemetry processing
            let processedUrl = this.startUrl;
            if (processedUrl.endsWith('/') || processedUrl.endsWith('#')) {
                processedUrl = processedUrl.slice(0, -1);
            }
            this.$emit('login', new IdC(processedUrl, this.selectedRegion))
        },
        handleCodeCatalystSignin() {
            this.$emit('login', new BuilderId())
        }
    },
    mounted() {
        document.getElementById("startUrl")?.focus()
    }
})
</script>

<style scoped lang="scss">
.region-select {
    position: relative;
    width: 100%;
}

.select-trigger {
    padding: 10px;
    padding-right: 20px;
    cursor: pointer;
    position: relative;
}

.select-trigger::after {
    content: '';
    position: absolute;
    right: 10px;
    top: 50%;
    transform: translateY(-50%);
    width: 0;
    height: 0;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    border-top: 5px solid currentColor;
}

.select-trigger-content {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.is-open .select-trigger::after {
    transform: translateY(-50%) rotate(180deg);
}


.options-container {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    max-height: 200px;
    overflow-y: auto;
    z-index: 1000;
}

.option {
    padding: 10px;
    cursor: pointer;
}

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
    .url-input, .region-select, .sso-profile, .select-trigger, .options-container {
        background-color: #252526;
        color: white;
        border: 1px solid #3c3c3c;
    }

    .option:hover, .option.selected {
        background-color: #3c3c3c;
    }
}

body.jb-light {
    .url-input, .region-select, .sso-profile, .select-trigger, .options-container {
        background-color: white;
        color: black;
        border: 1px solid #c9ccd6;
    }

    .option:hover, .option.selected {
        background-color: #f0f0f0;
    }
}
</style>
