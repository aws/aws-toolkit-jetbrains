// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.intellij.util.messages.MessageBus
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TelemetryServiceTest {
    private val batcher: TelemetryBatcher = mock()
    private val messageBusService: MessageBusService = MockMessageBusService()

    @Test
    fun testInitialChangeEvent() {
        val changeCaptor = argumentCaptor<Boolean>()
        batcher.stub {
            on(batcher.onTelemetryEnabledChanged(changeCaptor.capture()))
                .doAnswer { }
        }

        DefaultTelemetryService(
                mock(),
                mock(),
                messageBusService,
                1,
                TimeUnit.HOURS,
                mock(),
                batcher
        )

        assert(changeCaptor.allValues).hasSize(0)
    }

    @Test
    fun testTriggeredChangeEvent() {
        val changeCountDown = CountDownLatch(1)
        val changeCaptor = argumentCaptor<Boolean>()
        batcher.stub {
            on(batcher.onTelemetryEnabledChanged(changeCaptor.capture()))
                .doAnswer {
                    changeCountDown.countDown()
                }
        }

        DefaultTelemetryService(
                mock(),
                mock(),
                messageBusService,
                1,
                TimeUnit.HOURS,
                mock(),
                batcher
        )

        val messageBus: MessageBus = messageBusService.messageBus
        val messageBusPublisher: TelemetryEnabledChangedNotifier =
                messageBus.syncPublisher(messageBusService.telemetryEnabledTopic)
        messageBusPublisher.notify(true)

        changeCountDown.await(5, TimeUnit.SECONDS)
        verify(batcher).onTelemetryEnabledChanged(true)
        assert(changeCaptor.allValues).hasSize(1)
        assert(changeCaptor.firstValue).isEqualTo(true)
    }
}