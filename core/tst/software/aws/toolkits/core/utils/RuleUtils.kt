// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.utils

import java.util.Random

object RuleUtils {
    fun randomName(prefix: String = "a", length: Int = 63): String {
        val characters = ('0'..'9') + ('A'..'Z') + ('a'..'Z')
        val userName = System.getProperty("user.name", "unknown")
        return "${prefix.toLowerCase()}-${userName.toLowerCase()}-${List(length) { characters.random() }.joinToString("")}".take(length)
    }

    fun prefixFromCallingClass(): String {
        val callingClass = Thread.currentThread().stackTrace[3].className
        return callingClass.substringAfterLast(".")
    }

    fun randomNumber(min: Int = 0, max: Int = 65535): Int = Random().nextInt(max - min + 1) + min
}
