package software.aws.toolkits.jetbrains.services.lambda.execution.sam

import org.junit.Assert.assertTrue
import org.junit.Test

class SamVersionTest {
    @Test
    fun compatableSamVersion() {
        val versions = arrayOf(
            "SAM CLI, version 0.6.0",
            "SAM CLI, version 1.2.3",
            "SAM CLI, version 1.0.0-beta",
            "SAM CLI, version 1.0.0-beta+build"
        )
        for (version in versions) {
            assertTrue(SamInitRunner.checkVersion(version) == null)
        }
    }

    @Test
    fun unparsableVersion() {
        val versions = arrayOf(
            "GNU bash, version 3.2.57(1)-release (x86_64-apple-darwin16)",
            "GNU bash, version 3.2.57(1)-release",
            "12312312.123123131221"
        )
        for (version in versions) {
            val message = SamInitRunner.checkVersion(version)
            assertTrue(message != null && message.contains("Could not parse SAM executable version from"))
        }
    }

    @Test
    fun incompatableSamVersion() {
        val versions = arrayOf(
                "SAM CLI, version 0.5.9",
                "SAM CLI, version 0.0.1",
                "SAM CLI, version 0.5.9-dev"
        )
        for (version in versions) {
            val message = SamInitRunner.checkVersion(version)
            assertTrue(message != null && message.contains("Bad SAM executable version. Expected"))
        }
    }
}
