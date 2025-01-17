// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.storage

import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.Session

class ChatSessionStorage {
    private val sessions = mutableMapOf<String, Session>()

    @Synchronized
    fun getSession(tabId: String): Session = sessions.getOrPut(tabId) { Session(tabId) }

    fun deleteSession(tabId: String) {
        sessions.remove(tabId)
    }

    // Find all sessions that are currently waiting to be authenticated
    fun getAuthenticatingSessions(): List<Session> = this.sessions.values.filter { it.isAuthenticating }
}
