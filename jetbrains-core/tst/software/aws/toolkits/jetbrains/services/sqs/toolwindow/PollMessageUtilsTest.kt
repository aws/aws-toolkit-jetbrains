// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.aws.toolkits.jetbrains.services.sqs.MAX_LENGTH_OF_MESSAGES

class PollMessageUtilsTest {
    private val message1 = buildMessage("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
    private val message2 = buildMessage("""VJBIGiHnyegmcoGOEqS7EfHB8xU2CTyUHuJOPDap1ThwWFJ3WbdBlj73YGJtWQM8FWYeBxxUzeZOq7kW9WuYQGoFZ923Gm1jz78N9RnX
                                        bA32CjBopsmUYKACH3rOFr4bGwduwTJNfbVrmOy1hENDXNf1j2sHU6XtP9SQ5hDbk6AFp4CRTVnvShYH8n9zEZ3Y02pjNUSuW4kasF3PG9It8uanG
                                        HkjjUzNQIBifk4GH0JLkXJPAK3ecn3snKRaZzeVoCQ9rhJishhmXiG9nsPCN98lwcaNJs5BCcVlOrqpzvSi7xS6k2XTzVoF22itZ571QBecnv2Nlz
                                        2GC4W88hdnzX51SZs034yxOqCIFRNldqhXh7JDl5hAzkQ99CqgRnJbCszJsstPkUF0Ro5ZNHVXncJxkzW8Std9r5BxdET9QUw0nUCEQvElOWJhb9E
                                        DZtMHB3EtQMFJqsVIoYz79tW2exBP6ZRPMeemXXE2kdCj3aQBdlPTMF1aH6FBim6xJOFPuUVJIq6lKJEe3Gzk4Ssw9OSLuPN2xkvg5Hr8V4CfI2su
                                        9l4sgKKmXa4MYrzKkBXbvsEqBvUW6gVYNowoVczA1rkPoRsi9VEeIly3TQYXoLPcZVUY0ViUFKFsSYMlgLlv8wLqW2hGeqEiEc8EN4YnuZDO1Nc4d
                                        rBf0QMjj8DvyN6otEUXBGJ7Xj0cBQ7VnRXxv4WNe37Nlsrvi7tgCAmBok3Zf318D2m7tfaszh0onSNP4sCyXj1J9srlwMI5rzh2UE8Z7V8mLM""")
    private val message3 = buildMessage("""FVJBIGiHnyegmcoGOEqS7EfHB8xU2CTyUHuJOPDap1ThwWFJ3WbdBlj73YGJtWQM8FWYeBxxUzeZOq7kW9WuYQGoFZ923Gm1jz78N9Rn
                                        bA32CjBopsmUYKACH3rOFr4bGwduwTJNfbVrmOy1hENDXNf1j2sHU6XtP9SQ5hDbk6AFp4CRTVnvShYH8n9zEZ3Y02pjNUSuW4kasF3PG9It8uanG
                                        HkjjUzNQIBifk4GH0JLkXJPAK3ecn3snKRaZzeVoCQ9rhJishhmXiG9nsPCN98lwcaNJs5BCcVlOrqpzvSi7xS6k2XTzVoF22itZ571QBecnv2Nlz
                                        2GC4W88hdnzX51SZs034yxOqCIFRNldqhXh7JDl5hAzkQ99CqgRnJbCszJsstPkUF0Ro5ZNHVXncJxkzW8Std9r5BxdET9QUw0nUCEQvElOWJhb9E
                                        DZtMHB3EtQMFJqsVIoYz79tW2exBP6ZRPMeemXXE2kdCj3aQBdlPTMF1aH6FBim6xJOFPuUVJIq6lKJEe3Gzk4Ssw9OSLuPN2xkvg5Hr8V4CfI2su
                                        9l4sgKKmXa4MYrzKkBXbvsEqBvUW6gVYNowoVczA1rkPoRsi9VEeIly3TQYXoLPcZVUY0ViUFKFsSYMlgLlv8wLqW2hGeqEiEc8EN4YnuZDO1Nc4d
                                        rBf0QMjj8DvyN6otEUXBGJ7Xj0cBQ7VnRXxv4WNe37Nlsrvi7tgCAmBok3Zf318D2m7tfaszh0onSNP4sCyXj1J9srlwMI5rzh2UEX8Z7V8mLM""")

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Test
    fun `Message mapped to columns as expected`() {
        val table = MessagesTable().apply {
            tableModel.addRow(message1)
        }

        assertThat(table.tableModel.items.size).isOne()
        assertThat(table.tableModel.items.first().body()).isEqualTo(message1.body())
        assertThat(table.tableModel.items.first().messageId()).isEqualTo(message1.messageId())
        assertThat(table.tableModel.items.first().attributes().getValue(MessageSystemAttributeName.SENDER_ID)).isEqualTo("1234567890:test1")
        assertThat(table.tableModel.items.first().attributes().getValue(MessageSystemAttributeName.SENT_TIMESTAMP)).isEqualTo("111111111")
    }

    @Test
    fun `Long message body is truncated`() {
        // Message 1 has length below 1024
        val column1 = MessageBodyColumn().valueOf(message1)
        // Message 2 has length of 1024
        val column2 = MessageBodyColumn().valueOf(message2)
        // Message 3 has length of 1025
        val column3 = MessageBodyColumn().valueOf(message3)

        assertThat(column1?.length).isEqualTo(message1.body().length)
        assertThat(column2?.length).isEqualTo(MAX_LENGTH_OF_MESSAGES)
        assertThat(column3?.length).isEqualTo(MAX_LENGTH_OF_MESSAGES)
    }

    private fun buildMessage(body: String): Message {
        val message = Message.builder()
            .body(body)
            .messageId("ABC")
            .attributes(mapOf(Pair(MessageSystemAttributeName.SENDER_ID, "1234567890:test1"), Pair(MessageSystemAttributeName.SENT_TIMESTAMP, "111111111")))
            .build()
        return message
    }
}
