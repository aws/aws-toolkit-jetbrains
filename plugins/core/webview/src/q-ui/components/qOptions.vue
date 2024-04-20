<!-- Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div @keydown.enter="handleContinueClick">
        <button v-if="cancellable" class="back-button" @click="handleBackButtonClick" tabindex="-1">
            <svg width="24" height="24" viewBox="0 -3 13 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                    d="M4.98667 0.0933332L5.73333 0.786666L1.57333 4.94667H12.0267V5.96H1.57333L5.73333 10.0667L4.98667 10.8133L0.0266666 5.8V5.10667L4.98667 0.0933332Z"
                    fill="#21A2FF"
                />
            </svg>
        </button>
        <div class="title font-amazon bottom-small-gap" v-if="existingLogin.id === -1">Choose a sign-in option:</div>
        <SelectableItem
            @toggle="toggleItemSelection"
            :isSelected="selectedLoginOption === LoginOption.BUILDER_ID"
            :itemId="LoginOption.BUILDER_ID"
            :itemTitle="'Use for free'"
            :itemText="'No AWS account required'"
            class="font-amazon bottom-small-gap"
        ></SelectableItem>
        <!-- TODO: IdC description undecided -->
        <SelectableItem
            @toggle="toggleItemSelection"
            :isSelected="selectedLoginOption === LoginOption.ENTERPRISE_SSO"
            :itemId="LoginOption.ENTERPRISE_SSO"
            :itemTitle="'Use professional license'"
            :itemText="'Sign in to AWS with single sign-on'"
            class="font-amazon bottom-small-gap"
        ></SelectableItem>
        <button
            class="login-flow-button continue-button font-amazon"
            :disabled="selectedLoginOption === 0"
            v-on:click="handleContinueClick()"
            tabindex="-1"
        >
            Continue
        </button>
    </div>
</template>

<script lang="ts">
import {defineComponent} from 'vue'
import SelectableItem from "./selectableItem.vue";
import {Feature, Stage, LoginIdentifier, BuilderId} from "../../model";

export default defineComponent({
    name: "loginOptions",
    components: {SelectableItem},
    props: {
        app: String
    },
    computed: {
        stage(): Stage {
            return this.$store.state.stage
        },
        cancellable(): boolean {
            return this.$store.state.cancellable
        },
        feature(): Feature {
            return this.$store.state.feature
        }
    },
    data() {
        return {
            app: this.app,
            existingLogin: { id: -1, text: '', title: '' },
            selectedLoginOption: LoginIdentifier.NONE,
            LoginOption: LoginIdentifier
        }
    },
    methods: {
        toggleItemSelection(itemId: number) {
            this.selectedLoginOption = itemId
        },
        handleBackButtonClick() {
            this.$emit('backToMenu')
        },
        async handleContinueClick() {
            if (this.selectedLoginOption === LoginIdentifier.BUILDER_ID) {
                this.$emit('login', new BuilderId())
            } else if (this.selectedLoginOption === LoginIdentifier.ENTERPRISE_SSO) {
                this.$emit('stageChanged', 'SSO_FORM')
            }
        },
    }
})
</script>

<style scoped lang="scss">
</style>
