<!-- Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div>
        <!-- Body -->
        <div class="body">
            <Logo
                :app="app"
                :is-connected="stage === 'CONNECTED'"
            />
            <!-- Functionality -->
            <Reauth v-if="stage === 'REAUTH'" :app="app"/>
            <Login v-else :app="app"></Login>

        </div>
    </div>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import Login from './login.vue'
import Reauth from "@/q-ui/components/reauth.vue"
import {Stage} from '../..//model'
import {WebviewTelemetry} from '../../webviewTelemetry'
import Logo from '@/q-ui/components/logo.vue'

window.onerror = function (message) {
    WebviewTelemetry.instance.didShowPage((window as any).uiState, message.toString())
}

export default defineComponent({
    name: 'auth',
    components: {
        Logo,
        Reauth,
        Login,
    },
    props: {
        app: String
    },
    computed: {
        stage(): Stage {
            return this.$store.state.stage
        }
    },
    data() {
        return {}
    },
    mounted() {
        window.changeTheme = this.changeTheme.bind(this)
        window.ideApi.postMessage({command: 'prepareUi'})
        // update() alone can't cover the very first rendering of the page as webview default page is 'start'
        WebviewTelemetry.instance.willShowPage(this.stage)
        handleUpdated(this.stage)
    },
    updated() {
        handleUpdated(this.stage)
    },
    methods: {
        changeTheme(darkMode: boolean) {
            const oldCssId = darkMode ? "jb-light" : "jb-dark"
            const newCssId = darkMode ? "jb-dark" : "jb-light"
            document.body.classList.add(newCssId);
            document.body.classList.remove(oldCssId);
        },
    },
})

function handleUpdated(page: Stage) {
    let elementIdToFound: string | undefined = undefined
    switch (page) {
        case 'START':
            elementIdToFound = 'login-page'
            break
        case 'PROFILE_SELECT':
            elementIdToFound = 'profile-page'
            break

        case 'REAUTH':
            elementIdToFound = 'reauth-page'
            break
        default:
            return
    }

    if (!elementIdToFound) return
    const domElement = document.getElementById(elementIdToFound)
    if (!domElement) {
        WebviewTelemetry.instance.didShowPage(page, `NOT found domElement ${elementIdToFound}`)
    } else {
        WebviewTelemetry.instance.didShowPage(page)
    }
}
</script>
<style>
.body {
    margin: 0 10px;
}
</style>
