// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.utils

/*
 * Replace with [kotlin.collections.buildMap] when experimental is removed
 */
fun <K, V> buildMap(builder: MutableMap<K, V>.() -> Unit): Map<K, V> = mutableMapOf<K, V>().apply(builder).toMap()
