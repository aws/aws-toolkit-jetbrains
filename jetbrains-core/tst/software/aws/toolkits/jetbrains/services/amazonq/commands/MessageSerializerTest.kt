// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.commands

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.messages.UnknownMessageType
import software.aws.toolkits.jetbrains.utils.isInstanceOf

class MessageSerializerTest {

    private val messageTypeRegistry = mockk<MessageTypeRegistry>()

    private val serializer = MessageSerializer()

    @Test
    fun `serializes message`() {
        val message = TestMessage(id = "hello", count = 2)
        val result = serializer.serialize(message)
        assertThat(result).isEqualTo("""{"id":"hello","count":2}""")
    }

    @Test
    fun `nulls are ignored during serialization`() {
        val message = TestMessage(id = "hello", count = null)
        val result = serializer.serialize(message)
        assertThat(result).isEqualTo("""{"id":"hello"}""")
    }

    @Test
    fun `enums are serialized`() {
        val message = TestMessageWithEnum(enum = TestEnum.Two)
        val result = serializer.serialize(message)
        assertThat(result).isEqualTo("""{"enum":"Two"}""")
    }

    @Test
    fun `deserializes message`() {
        every { messageTypeRegistry.get("test") } returns TestMessage::class
        val json = """{"command":"test","id":"aws","count":4}"""
        val node = serializer.toNode(json)
        val result = serializer.deserialize(node, messageTypeRegistry) as? TestMessage
        assertThat(result?.id).isEqualTo("aws")
        assertThat(result?.count).isEqualTo(4)
    }

    @Test
    fun `unknown properties are ignored during deserialization`() {
        every { messageTypeRegistry.get("test") } returns TestMessage::class
        val json = """{"command":"test","id":"aws","count":4,"unknown":"ok"}"""
        val node = serializer.toNode(json)
        val result = serializer.deserialize(node, messageTypeRegistry) as? TestMessage
        assertThat(result?.id).isEqualTo("aws")
        assertThat(result?.count).isEqualTo(4)
    }

    @Test
    fun `UnknownMessageType is returned if command is not registered`() {
        every { messageTypeRegistry.get(any()) } returns null
        val json = """{"command":"test","id":"aws","count":4}"""
        val node = serializer.toNode(json)
        val result = serializer.deserialize(node, messageTypeRegistry)
        assertThat(result).isInstanceOf<UnknownMessageType>()
    }

    @Test
    fun `enums are deserialized case-insensitive`() {
        every { messageTypeRegistry.get("enum") } returns TestMessageWithEnum::class
        val json = """{"command":"enum","enum":"two"}"""
        val node = serializer.toNode(json)
        val result = serializer.deserialize(node, messageTypeRegistry) as? TestMessageWithEnum
        assertThat(result?.enum).isEqualTo(TestEnum.Two)
    }
}

private enum class TestEnum { One, Two }
private class TestMessage(val id: String, val count: Int?) : AmazonQMessage
private class TestMessageWithEnum(val enum: TestEnum): AmazonQMessage
