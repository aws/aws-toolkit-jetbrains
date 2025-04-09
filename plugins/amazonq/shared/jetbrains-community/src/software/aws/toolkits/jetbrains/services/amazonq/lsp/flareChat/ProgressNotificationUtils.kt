// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.google.gson.Gson
import com.google.gson.JsonElement
import org.eclipse.lsp4j.ProgressParams

object ProgressNotificationUtils {
    fun getToken(params: ProgressParams): String {
        val token = if (params.token.isLeft) {
            params.token.left
        } else {
            params.token.right.toString()
        }

        return token
    }

    fun <T> getObject(params: ProgressParams, cls: Class<T>?): T? {
        val objct = params.value.right as? JsonElement ?: return null

        val gson = Gson()
        val element: JsonElement = objct
        val obj: T = gson.fromJson(element, cls)

        return obj
    }
}
