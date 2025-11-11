// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.remoteDev.caws

// TODO: Re-enable when RD platform APIs are available in 2025.3
// The com.jetbrains.rd.platform.codeWithMe APIs are not available in 2025.3 EAP
// import com.jetbrains.rd.platform.codeWithMe.unattendedHost.metrics.Metric
// import com.jetbrains.rd.platform.codeWithMe.unattendedHost.metrics.MetricType
// import com.jetbrains.rd.platform.codeWithMe.unattendedHost.metrics.MetricsStatus
// import com.jetbrains.rd.platform.codeWithMe.unattendedHost.metrics.providers.MetricProvider
import software.aws.toolkits.resources.message

/*
class RebuildDevfileRequiredNotification : MetricProvider {
    override val id: String
        get() = "devfileRebuildRequired"

    override fun getMetrics(): List<Metric> = listOf(
        object : Metric {
            override val id: String
                get() = "devfileRebuildRequired"
            override val type: MetricType
                get() = MetricType.PERFORMANCE
            override val status: MetricsStatus
                get() = MetricsStatus.RED
        }
    )

    inner class DevfileRebuildRequiredMetric : Metric {
        override fun toString(): String = message("caws.rebuild.workspace.notification")
    }
}
*/
