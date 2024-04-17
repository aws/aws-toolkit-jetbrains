<!-- Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div>
        <!-- Body -->
        <div class="body">
            <!-- Functionality -->
            <Login v-if="stage !== 'REAUTH'" :disabled="false" :app="app"></Login>
            <Reauth v-else :app="app"/>

        </div>
    </div>
</template>
<script lang="ts">
import {computed, defineComponent} from 'vue'
import Login from './login.vue'
import Reauth from "./reauth.vue";
import {Stage} from "../../model";
export default defineComponent({
    name: 'auth',
    components: {
        Login,
        Reauth
    },
    props: {
        app: String
    },
    data() {
        return {}
    },
    computed: {
        stage(): Stage {
            return this.$store.state.stage
        },
    },
    mounted() {},
    methods: {},
})
</script>
<style>
.body {
    margin: 0 10px;
}
</style>
