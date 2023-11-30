// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.commands

import org.assertj.core.api.Assertions.assertThat
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import kotlin.test.Test

class MessageTypeRegistryTest {

    private val registry = MessageTypeRegistry()

    @Test
    fun `returns registered types`() {
        registry.register("one", MessageTypeOne::class)
        registry.register("two", MessageTypeTwo::class)
        assertThat(registry.get("one")).isEqualTo(MessageTypeOne::class)
        assertThat(registry.get("two")).isEqualTo(MessageTypeTwo::class)
    }

    @Test
    fun `multiple types can be registered at once`() {
        registry.register("one" to MessageTypeOne::class, "two" to MessageTypeTwo::class)
        assertThat(registry.get("one")).isEqualTo(MessageTypeOne::class)
        assertThat(registry.get("two")).isEqualTo(MessageTypeTwo::class)
    }

    @Test
    fun `registration overwrites previous entries`() {
        registry.register("one", MessageTypeOne::class)
        registry.register("two", MessageTypeTwo::class)
        assertThat(registry.get("one")).isEqualTo(MessageTypeTwo::class)
    }

    @Test
    fun `registration can be removed`() {
        registry.register("one", MessageTypeOne::class)
        registry.register("two", MessageTypeTwo::class)
        registry.remove("one")
        assertThat(registry.get("one")).isNull()
        assertThat(registry.get("two")).isEqualTo(MessageTypeTwo::class)
    }
}

private class MessageTypeOne : AmazonQMessage
private class MessageTypeTwo : AmazonQMessage
