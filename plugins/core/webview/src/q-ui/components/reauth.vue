<!-- Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <Logo :app="app"/>

    <div class="font-amazon bottom-small-gap title centered">Connection to Amazon Q Expired</div>
    <div class="font-amazon bottom-small-gap title centered">Please re-authenticate to continue</div>

    <button
        class="login-flow-button continue-button font-amazon"
        v-on:click="handleContinueClick()"
    >
        Continue
    </button>

    <button
        class="login-flow-button cancel-button font-amazon"
        v-on:click="handleCancel()"
    >
        Sign out
    </button>
</template>


<script lang="ts">
import {defineComponent} from 'vue'
import Logo from "./logo.vue";
import {Stage} from "../../model";

export default defineComponent({
    name: "reauth",
    components: {Logo},
    props: {
        app: String
    },
    computed: {
        stage(): Stage {
            return this.$store.state.stage
        }
    },
    data() {

    },
    methods: {
        handleContinueClick() {
            console.log('reauthenticating.......')
        },
        handleCancel() {
            console.log('signout')
        }
    }
})
</script>

<style scoped lang="scss">
.centered {
    display: flex;
    justify-content: center;
}
</style>
