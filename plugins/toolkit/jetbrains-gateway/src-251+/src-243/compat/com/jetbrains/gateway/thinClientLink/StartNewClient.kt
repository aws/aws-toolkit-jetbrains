// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package compat.com.jetbrains.gateway.thinClientLink

import com.jetbrains.gateway.thinClientLink.LinkedClientManager
import com.jetbrains.rd.util.lifetime.Lifetime
import java.net.URI

fun startNewClient(lifetime: Lifetime, initialLink: URI, remoteIdentity: String?, onStarted: () -> Unit) =
    LinkedClientManager.getInstance().startNewClient(lifetime, initialLink, remoteIdentity, null, onStarted)
