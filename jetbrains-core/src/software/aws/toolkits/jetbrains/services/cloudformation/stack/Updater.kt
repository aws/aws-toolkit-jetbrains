// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.stack

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.DescribeStackResourcesRequest
import software.amazon.awssdk.services.cloudformation.model.Output
import software.amazon.awssdk.services.cloudformation.model.StackEvent
import software.amazon.awssdk.services.cloudformation.model.StackResource
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import javax.swing.SwingUtilities

/**
 * CallBacks for Updater. Called on EDT.
 */
interface UpdateListener {
    fun onError(message: String)
    fun onStackStatusChanged(stackStatus: StackStatus)
}

/**
 * Updates tree view on timer. Create on EDT.
 */
class Updater(
    private val treeView: TreeView,
    private val eventsTable: EventsTable,
    private val outputsTable: OutputsListener,
    private val resourceListener: ResourceListener,
    private val stackName: String,
    private val updateEveryMs: Int,
    private val listener: UpdateListener,
    private val client: CloudFormationClient,
    private val setPagesAvailable: (Set<Page>) -> Unit
) : Disposable {

    @Volatile
    private var previousStackStatusType: StatusType = StatusType.UNKNOWN
    private val eventsFetcher = EventsFetcher(stackName)

    private val app: Application
        get() = ApplicationManager.getApplication()

    /**
     * Alarm to me used to run [fetchData]. Do not use [Application.executeOnPooledThread] : it will lead to
     * races because of different threads
     */
    private val alarm: Alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)

    fun start() {
        alarm.addRequest({ fetchDataSafely() }, 0)
    }

    /**
     * Fetch and display data of another page
     */
    fun switchPage(page: Page) {
        alarm.addRequest({ fetchDataSafely(page) }, 0)
    }

    private fun fetchDataSafely(pageToSwitchTo: Page? = null) {
        try {
            fetchData(pageToSwitchTo)
        } catch (e: SdkException) {
            app.invokeLater {
                listener.onError(e.localizedMessage)
            }
        }
    }

    private fun fetchData(pageToSwitchTo: Page?) {
        assert(!SwingUtilities.isEventDispatchThread())
        val stackDetails = fetchStackDetails()
        val newStackStatus = stackDetails.status
        val newStackStatusType = newStackStatus.type
        val newStackStatusNotInProgress = newStackStatusType !in setOf(StatusType.UNKNOWN, StatusType.PROGRESS)

        // Stack changed to some "final" status just now, notify user
        val stackSwitchedToFinalStatus = previousStackStatusType != newStackStatusType && newStackStatusNotInProgress

        // Stack status is final and has not been changed
        val stackStatusFinalNotChanged = newStackStatusNotInProgress && newStackStatusType == previousStackStatusType

        previousStackStatusType = newStackStatusType

        // Only fetch events if stack is not in final state or page switched
        val eventsAndButtonStates = if (stackStatusFinalNotChanged && pageToSwitchTo == null) {
            null
        } else {
            eventsFetcher.fetchEvents(client, pageToSwitchTo)
        }

        app.invokeLater {
            outputsTable.updatedOutputs(stackDetails.outputs)
            resourceListener.updatedResources(stackDetails.resources)

            showData(
                stackStatus = newStackStatus,
                resources = stackDetails.resources,
                newEvents = eventsAndButtonStates?.first ?: emptyList(),
                pageChanged = pageToSwitchTo != null
            )

            eventsAndButtonStates?.second?.let { setPagesAvailable(it) }

            if (stackSwitchedToFinalStatus) {
                listener.onStackStatusChanged(newStackStatus)
            }

            // Reschedule next run
            if (!alarm.isDisposed) {
                alarm.addRequest({ fetchDataSafely() }, updateEveryMs)
            }
        }
    }

    private fun showData(
        stackStatus: StackStatus,
        resources: Collection<StackResource>,
        newEvents: List<StackEvent>,
        pageChanged: Boolean
    ) {
        assert(SwingUtilities.isEventDispatchThread())
        treeView.setStackStatus(stackStatus)
        treeView.fillResources(resources)
        eventsTable.insertEvents(newEvents, pageChanged)
    }

    private fun fetchStackDetails(): Stack {
        assert(!SwingUtilities.isEventDispatchThread())
        val resourcesRequest = DescribeStackResourcesRequest.builder().stackName(stackName).build()
        val resources = client.describeStackResources(resourcesRequest).stackResources()
        val stack = client.describeStacks { it.stackName(stackName) }.stacks().firstOrNull()
        val stackStatus = stack?.stackStatus() ?: StackStatus.UNKNOWN_TO_SDK_VERSION
        val outputs = stack?.outputs() ?: emptyList()
        return Stack(stackStatus, resources, outputs)
    }

    private data class Stack(val status: StackStatus, val resources: List<StackResource>, val outputs: List<Output>)

    override fun dispose() {}
}
