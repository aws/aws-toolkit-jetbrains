// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.telemetry

import software.amazon.awssdk.services.toolkittelemetry.model.Unit
import software.aws.toolkits.core.utils.getLogger
import java.time.Instant

interface MetricEvent {
    val namespace: String
    val createTime: Instant
    val data: Iterable<Datum>

    interface Builder {
        fun namespace(namespace: String): Builder

        fun createTime(createTime: Instant): Builder

        fun datum(buildDatum: Datum.Builder.() -> kotlin.Unit): Builder

        fun build(): MetricEvent
    }

    interface Datum {
        val name: String
        val value: Double
        val unit: Unit
        val metadata: Map<String, String>

        interface Builder {
            fun name(name: String): Builder

            fun value(value: Double): Builder

            fun unit(unit: Unit): Builder

            fun metadata(key: String, value: String): Builder

            fun build(): Datum
        }
    }
}

class DefaultMetricEvent(
    override val namespace: String,
    override val createTime: Instant,
    override val data: Iterable<MetricEvent.Datum>
) : MetricEvent {
    class BuilderImpl : MetricEvent.Builder {
        private var namespace: String? = null
        private var createTime: Instant? = null
        private var data: MutableCollection<MetricEvent.Datum> = mutableListOf()

        override fun namespace(namespace: String): MetricEvent.Builder {
            this.namespace = namespace
            return this
        }

        override fun createTime(createTime: Instant): MetricEvent.Builder {
            this.createTime = createTime
            return this
        }

        override fun datum(buildDatum: MetricEvent.Datum.Builder.() -> kotlin.Unit): MetricEvent.Builder {
            val builder = DefaultDatum.builder()
            buildDatum(builder)
            data.add(builder.build())
            return this
        }

        override fun build(): MetricEvent {
            if (this.namespace == null) {
                LOG.error("Cannot build MetricEvent.Datum without a namespace", Throwable())
            }
            if (this.createTime == null) {
                LOG.error("Cannot build MetricEvent.Datum without a createTime", Throwable())
            }

            return DefaultMetricEvent(namespace!!, createTime!!, data)
        }
    }

    companion object {
        private val LOG = getLogger<DefaultMetricEvent>()

        fun builder(): MetricEvent.Builder = BuilderImpl()
    }

    class DefaultDatum(
        override val name: String,
        override val value: Double,
        override val unit: Unit,
        override val metadata: Map<String, String>
    ) : MetricEvent.Datum {
        class BuilderImpl : MetricEvent.Datum.Builder {
            private var name: String? = null
            private var value: Double? = null
            private var unit: Unit? = null
            private val metadata: MutableMap<String, String> = HashMap()

            override fun name(name: String): MetricEvent.Datum.Builder {
                this.name = name
                return this
            }

            override fun value(value: Double): MetricEvent.Datum.Builder {
                this.value = value
                return this
            }

            override fun unit(unit: Unit): MetricEvent.Datum.Builder {
                this.unit = unit
                return this
            }

            override fun metadata(key: String, value: String): MetricEvent.Datum.Builder {
                if (metadata.containsKey(key)) {
                    LOG.warn("Attempted to add multiple pieces of metadata with the same key")
                    return this
                }

                if (metadata.size > MAX_METADATA_ENTRIES) {
                    LOG.warn("Each metric datum may contain a maximum of $MAX_METADATA_ENTRIES metadata entries")
                    return this
                }

                metadata[key] = value
                return this
            }

            override fun build(): MetricEvent.Datum {
                if (this.name == null) {
                    LOG.error("Cannot build MetricEvent.Datum without a name", Throwable())
                }
                if (this.value == null) {
                    LOG.error("Cannot build MetricEvent.Datum without a value", Throwable())
                }
                if (this.unit == null) {
                    LOG.error("Cannot build MetricEvent.Datum without a unit", Throwable())
                }

                return DefaultDatum(
                        this.name!!,
                        this.value!!,
                        this.unit!!,
                        this.metadata
                )
            }
        }

        companion object {
            private val LOG = getLogger<DefaultDatum>()

            fun builder(): MetricEvent.Datum.Builder = BuilderImpl()

            const val MAX_METADATA_ENTRIES: Int = 10
        }
    }
}
