// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName

class PollMessageUtilsTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Test
    fun `Message mapped to columns as expected`() {
        val table = MessagesTable().apply {
            tableModel.addRow(message1)
        }

        assertThat(table.tableModel.items.size).isOne()
        assertThat(table.tableModel.items.first().body()).isEqualTo("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        assertThat(table.tableModel.items.first().messageId()).isEqualTo("AAAAAAAAAAA")
        assertThat(table.tableModel.items.first().attributes().getValue(MessageSystemAttributeName.SENDER_ID)).isEqualTo("1234567890:test1")
        assertThat(table.tableModel.items.first().attributes().getValue(MessageSystemAttributeName.SENT_TIMESTAMP)).isEqualTo("111111111")
    }

    @Test
    fun `Message body truncated as expected`() {
        // Message 1 has length below 1024
        val column1 = MessageBodyColumn().valueOf(message1)
        // Message 2 has length of 1024
        val column2 = MessageBodyColumn().valueOf(message2)
        // Message 3 has length of 1025
        val column3 = MessageBodyColumn().valueOf(message3)

        assertThat(column1?.length).isEqualTo(message1.body().length)
        assertThat(column2?.length).isEqualTo(MAX_LENGTH)
        assertThat(column3?.length).isEqualTo(MAX_LENGTH)
    }

    private companion object {
        val MAX_LENGTH = 1024
        val message1: Message = Message.builder()
            .body("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .messageId("AAAAAAAAAAA")
            .attributes(mapOf(Pair(MessageSystemAttributeName.SENDER_ID, "1234567890:test1"), Pair(MessageSystemAttributeName.SENT_TIMESTAMP, "111111111")))
            .build()
        val message2: Message = Message.builder()
            .body("VJBIGiHnyegmcoGOEqS7EfHB8xU2CTyUHuJOPDap1ThwWFJ3WbdBlj73YGJtWQM8FWYeBxxUzeZOq7kW9WuYQGoFZ923Gm1jz78N9RnX8Z7V8mLMbA32CjBopsmUYKACH3" +
                "rOFr4bGwduwTJNfbVrmOy1hENDXNf1j2sHU6XtP9SQ5hDbk6AFp4CRTVnvShYH8n9zEZ3Y02pjNUSuW4kasF3PG9It8uanGHkjjUzNQIBifk4GH0JLkXJPAK3ecn3snKRaZzeVoCQ9" +
                "rhJishhmXiG9nsPCN98lwcaNJs5BCcVlOrqpzvSi7xS6k2XTzVoF22itZ571QBecnv2Nlz2GC4W88hdnzX51SZs034yxOqCIFRNldqhXh7JDl5hAzkQ99CqgRnJbCszJsstPkUF0Ro" +
                "5ZNHVXncJxkzW8Std9r5BxdET9QUw0nUCEQvElOWJhb9EDZtMHB3EtQMFJqsVIoYz79tW2exBP6ZRPMeemXXE2kdCj3aQBdlPTMF1aH6FBim6xJOFPuUVJIq6lKJEe3Gzk4Ssw9OSL" +
                "uPN2xkvg5Hr8V4CfI2su9l4sgKKmXa4MYrzKkBXbvsEqBvUW6gVYNowoVczA1rkPoRsi9VEeIly3TQYXoLPcZVUY0ViUFKFsSYMlgLlv8wLqW2hGeqEiEc8EN4YnuZDO1Nc4drBf0Q" +
                "Mjj8DvyN6otEUXBGJ7Xj0cBQ7VnRXxv4WNe37Nlsrvi7tgCAmBok3Zf318D2m7tfaszh0onSNP4sCyXj1J9srlwMI5rzh2UELnR32wSUa43UX31e1aXLuMxlY5QGONanQyui2IQgT8" +
                "qS21pHQTVAzbjbuabB2PE0AaipvsH0O8o4y5TrSfqqmPibJpUDT0RclJDTnkUPIss3aiKv38B3ISjnGXqWlLu6YRXlFMGY7biI3dtsILCHPnIEoAlrfunrJeQS3lgfr82ls7p3teaR" +
                "L66yPTZ8CLFxw2tX2dAAAeDflNA3g9i2TskT86esi9jAW91JaLjgoWbHpGlwvAfE7M")
            .messageId("BBBBBBBBBBB")
            .attributes(mapOf(Pair(MessageSystemAttributeName.SENDER_ID, "1234567890:test2"), Pair(MessageSystemAttributeName.SENT_TIMESTAMP, "222222222")))
            .build()
        val message3: Message = Message.builder()
            .body("FVJBIGiHnyegmcoGOEqS7EfHB8xU2CTyUHuJOPDap1ThwWFJ3WbdBlj73YGJtWQM8FWYeBxxUzeZOq7kW9WuYQGoFZ923Gm1jz78N9RnX8Z7V8mLMbA32CjBopsmUYKACH" +
                "3rOFr4bGwduwTJNfbVrmOy1hENDXNf1j2sHU6XtP9SQ5hDbk6AFp4CRTVnvShYH8n9zEZ3Y02pjNUSuW4kasF3PG9It8uanGHkjjUzNQIBifk4GH0JLkXJPAK3ecn3snKRaZzeVoCQ" +
                "9rhJishhmXiG9nsPCN98lwcaNJs5BCcVlOrqpzvSi7xS6k2XTzVoF22itZ571QBecnv2Nlz2GC4W88hdnzX51SZs034yxOqCIFRNldqhXh7JDl5hAzkQ99CqgRnJbCszJsstPkUF0R" +
                "o5ZNHVXncJxkzW8Std9r5BxdET9QUw0nUCEQvElOWJhb9EDZtMHB3EtQMFJqsVIoYz79tW2exBP6ZRPMeemXXE2kdCj3aQBdlPTMF1aH6FBim6xJOFPuUVJIq6lKJEe3Gzk4Ssw9OS" +
                "LuPN2xkvg5Hr8V4CfI2su9l4sgKKmXa4MYrzKkBXbvsEqBvUW6gVYNowoVczA1rkPoRsi9VEeIly3TQYXoLPcZVUY0ViUFKFsSYMlgLlv8wLqW2hGeqEiEc8EN4YnuZDO1Nc4drBf0" +
                "QMjj8DvyN6otEUXBGJ7Xj0cBQ7VnRXxv4WNe37Nlsrvi7tgCAmBok3Zf318D2m7tfaszh0onSNP4sCyXj1J9srlwMI5rzh2UELnR32wSUa43UX31e1aXLuMxlY5QGONanQyui2IQgT" +
                "8qS21pHQTVAzbjbuabB2PE0AaipvsH0O8o4y5TrSfqqmPibJpUDT0RclJDTnkUPIss3aiKv38B3ISjnGXqWlLu6YRXlFMGY7biI3dtsILCHPnIEoAlrfunrJeQS3lgfr82ls7p3tea" +
                "RL66yPTZ8CLFxw2tX2dAAAeDflNA3g9i2TskT86esi9jAW91JaLjgoWbHpGlwvAfE7M")
            .messageId("CCCCCCCCCC")
            .attributes(mapOf(Pair(MessageSystemAttributeName.SENDER_ID, "1234567890:test3"), Pair(MessageSystemAttributeName.SENT_TIMESTAMP, "333333333")))
            .build()
    }
}
