// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.storage

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.DocSession

class ChatSessionStorage {
    private val sessions = mutableMapOf<String, DocSession>()

    private fun createSession(tabId: String, project: Project): DocSession {
        val session = DocSession(tabId, project)
        sessions[tabId] = session
        return session
    }

    @Synchronized fun getSession(tabId: String, project: Project): DocSession = sessions[tabId] ?: createSession(tabId, project)

    fun deleteSession(tabId: String) {
        sessions.remove(tabId)
    }

    // Find all sessions that are currently waiting to be authenticated
    fun getAuthenticatingSessions(): List<DocSession> = this.sessions.values.filter { it.isAuthenticating }

    fun deleteAllSessions() {
        sessions.values.forEach { session ->
            session.sessionState.token?.cancel()
        }
        sessions.clear()
    }
}
