package software.aws.toolkits.jetbrains.core

import org.junit.Test
import software.amazon.awssdk.services.toolkittelemetrylambda.ToolkitTelemetryLambdaClient

class TelemetryGenerationTest {
    @Test
    fun generatedTelemetryClientIsAvailable() {
        //TODO: Probably can delete this once we're actually using the telemetry client elsewhere
        ToolkitTelemetryLambdaClient.builder().build()
    }
}