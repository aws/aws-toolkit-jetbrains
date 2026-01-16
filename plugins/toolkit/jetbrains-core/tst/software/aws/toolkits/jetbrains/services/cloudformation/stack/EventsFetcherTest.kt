// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.stack

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.cloudformation.model.StackEvent
import software.aws.toolkit.jetbrains.core.MockClientManagerRule

private fun expectRange(from: String, to: String, events: List<StackEvent>, expectedSize: Int = 1024) {
    assertThat(events).withFailMessage("Wrong number of items").hasSize(expectedSize)
    assertThat(events.first().eventId()).withFailMessage("Wrong page start").isEqualTo(from)
    assertThat(events.last().eventId()).withFailMessage("Wrong page end").isEqualTo(to)
}

private const val nonEmptyMessage = "Second call on the same page must not return anything"
private const val wrongPageMessage = "Wrong list of available pages"

@Suppress("UnnecessaryApply")
class EventsFetcherTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule()

    @Test
    fun onlyNewEvents() {
        val generator = MockEventsGenerator()
        val client = mockClientManagerRule.createMock(generator)

        val fetcher = EventsFetcher("myStack")
        fetcher.fetchEvents(client, null).apply {
            expectRange("4096", "3073", first)
        }
        fetcher.fetchEvents(client, null).apply {
            assertThat(first).withFailMessage(nonEmptyMessage).isEmpty()
        }
        generator.addEvent() // New event arrived
        fetcher.fetchEvents(client, null).apply {
            expectRange("4097", "4097", first, expectedSize = 1)
        }
        fetcher.fetchEvents(client, null).apply {
            assertThat(first).withFailMessage(nonEmptyMessage).isEmpty()
        }
    }

    @Test
    fun paging() {
        val client = mockClientManagerRule.createMock(MockEventsGenerator())
        val fetcher = EventsFetcher("myStack")
        fetcher.fetchEvents(client, null).apply {
            expectRange("4096", "3073", first)
            assertThat(second).withFailMessage(wrongPageMessage).isEqualTo(setOf(Page.NEXT))
        }
        fetcher.fetchEvents(client, null).apply {
            assertThat(first).withFailMessage(nonEmptyMessage).isEmpty()
            assertThat(second).withFailMessage(wrongPageMessage).isEqualTo(setOf(Page.NEXT))
        }
        fetcher.fetchEvents(client, Page.NEXT).apply {
            expectRange("3072", "2049", first)
            assertThat(second).withFailMessage(wrongPageMessage).isEqualTo(setOf(Page.PREVIOUS, Page.NEXT))
        }
        fetcher.fetchEvents(client, null).apply {
            assertThat(first).withFailMessage(nonEmptyMessage).isEmpty()
            assertThat(second).withFailMessage(wrongPageMessage).isEqualTo(setOf(Page.PREVIOUS, Page.NEXT))
        }
        fetcher.fetchEvents(client, Page.NEXT) // Scroll to the end
        fetcher.fetchEvents(client, Page.NEXT).apply {
            expectRange("1024", "1", first)
            assertThat(second).withFailMessage(wrongPageMessage).isEqualTo(setOf(Page.PREVIOUS))
        }
        fetcher.fetchEvents(client, Page.PREVIOUS).apply {
            expectRange("2048", "1025", first)
            assertThat(second).withFailMessage(wrongPageMessage).isEqualTo(setOf(Page.PREVIOUS, Page.NEXT))
        }
    }
}
