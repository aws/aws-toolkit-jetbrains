<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div @keydown.enter="handleContinueClick">
        <div class="font-amazon">
            <template v-if="isWaitingResponse">
                <div class="title bottom-small-gap">Fetching Q Developer profiles...this may take a minute.</div>
            </template>

            <template v-else>
                <!-- Title & Subtitle -->
                <div id="profile-page" class="profile-header">
                    <h2 class="title bottom-small-gap">Choose a Q Developer profile</h2>
                    <div class="profile-subtitle">
                        Your administrator has given you access to Q from multiple profiles.
                        Choose the profile that meets your current working needs. You can change your profile at any time.
                        <a @click.prevent="openUrl">More info.</a>
                    </div>
                </div>
                <!-- Profile List -->
                <div class="profile-list">
                    <div
                        v-for="(profile, index) in availableProfiles"
                        :key="index"
                        class="profile-item bottom-small-gap"
                        :class="{ selected: selectedProfile?.arn === profile.arn }"
                        @click="toggleItemSelection(profile)"
                        tabindex="0"
                    >
                        <div class="text">
                            <div class="profile-name">{{ profile.profileName }} - <span class="profile-region">{{ profile.region }}</span></div>
                            <div class="profile-id">Account: {{ profile.accountId }}</div>
                        </div>
                    </div>
                </div>

                <div v-if="errorMessage" style="color: white; margin-bottom: 10px;">
                    {{ errorMessage }}
                </div>
                <div v-if="errorMessage" class="button-row">
                    <button
                        class="login-flow-button continue-button font-amazon"
                        :disabled="isRefreshing"
                        @click="handleRetryClick"
                    >
                        {{ isRefreshing ? 'Refreshing...' : 'Try Again' }}
                    </button>
                    <button
                        class="login-flow-button continue-button font-amazon"
                        @click="handleSignoutClick()"
                    >
                        Sign Out
                    </button>
                </div>
                <!-- Continue Button -->
                <div v-else>
                    <button
                        class="login-flow-button continue-button font-amazon"
                        :disabled="selectedProfile === null"
                        v-on:click="handleContinueClick()"
                        tabindex="-1"
                    >
                        Continue
                    </button>
                </div>
            </template>
        </div>
    </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { Profile, GENERIC_PROFILE_LOAD_ERROR, ListProfilePendingResult, ListProfileSuccessResult, ListProfileFailureResult } from '../../model'

export default defineComponent({
    name: 'ProfileSelection',
    props: {
        app: { type: String, default: '' }
    },
    data() {
        return {
            selectedProfile: undefined as Profile | undefined,
            availableProfiles: [] as Profile[],
            errorMessage: undefined as string | undefined,
            isRefreshing: false as boolean,
        }
    },
    computed: {
        isWaitingResponse() {
            const profileResult = this.$store.state.listProfilesResult
            if (profileResult instanceof ListProfilePendingResult) {
                return true
            }

            if (profileResult instanceof ListProfileSuccessResult) {
                this.availableProfiles = profileResult.profiles
            } else if (profileResult instanceof ListProfileFailureResult) {
                this.errorMessage = GENERIC_PROFILE_LOAD_ERROR
            } else {
                // should not be this path
                this.errorMessage = "Unexpected error happenede while loading Q Webview page"
            }
        }
    },
    mounted() {
        window.ideApi.postMessage({command: 'listProfiles'})
    },

    methods: {
        toggleItemSelection(profile: Profile) {
            this.selectedProfile = profile;
        },
        handleContinueClick() {
            if (this.selectedProfile) {
                this.$store.commit('setSelectedProfile', this.selectedProfile);
                const switchProfileMessage = {
                    command: 'switchProfile',
                    profileName: this.selectedProfile.profileName,
                    accountId: this.selectedProfile.accountId,
                    region: this.selectedProfile.region,
                    arn: this.selectedProfile.arn
                };
                window.ideApi.postMessage(switchProfileMessage);
            }
        },
        handleRetryClick() {
            this.isRefreshing = true
            window.ideApi.postMessage({command: 'prepareUi'})
        },
        handleSignoutClick() {
            window.ideApi.postMessage({command: 'signout'})
        },
        openUrl() {
            window.ideApi.postMessage({
                command: 'openUrl',
                externalLink: 'https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/subscribe-understanding-profile.html'
            })
        }
    }
})
</script>
<style scoped lang="scss">
.profile-header {
    margin-bottom: 16px;
}

.profile-subtitle {
    font-size: 12px;
    color: #bbbbbb;
    margin-bottom: 12px;
}

.profile-list {
    display: flex;
    flex-direction: column;
}

.profile-item {
    padding: 15px;
    display: flex;
    align-items: flex-start;
    border: 1px solid #cccccc;
    border-radius: 4px;
    margin-bottom: 10px;
    cursor: pointer;
    transition: background 0.2s ease-in-out;
}

.button-row :deep(.login-flow-button) {
    margin-bottom: 10px;
}

.button-row {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    margin-top: 20px;
}

.selected {
    user-select: none;
}

.text {
    display: flex;
    flex-direction: column;
    font-size: 15px;
}

.profile-name {
    font-weight: bold;
    margin-bottom: 2px;
}

.profile-region {
    font-style: italic;
    color: #bbbbbb;
}

.profile-description {
    font-size: 12px;
    color: #bbbbbb;
}

body.jb-dark {
    .profile-item {
        border: 1px solid white;
    }

    .selected {
        border: 1px solid #29a7ff;
    }
}

body.jb-light {
    .profile-item {
        border: 1px solid black;
    }

    .selected {
        border: 1px solid #3574f0;
    }
}
</style>
