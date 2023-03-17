// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.extensions

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import software.aws.toolkits.core.utils.outputStream
import java.nio.file.Paths
import javax.imageio.ImageIO

class TestRecorder : TestWatcher {
    private companion object {
        val TEST_REPORTS_LOCATION by lazy {
            System.getProperty("testReportPath")?.let {
                Paths.get(it)
            }
        }
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        val testReport = TEST_REPORTS_LOCATION?.resolve(context.displayName) ?: return

        uiTest {
            testReport.resolve("screenshot.png").outputStream().use {
                ImageIO.write(getScreenshot(), "png", it)
            }
            // TODO: fix on failure for 231
//            testReport.resolve("uiHierarchy.html").outputStream().use {
//                URL("http://127.0.0.1:$robotPort/").openStream().copyTo(it)
//            }
//
//            listOf("scripts.js", "xpathEditor.js", "updateButton.js", "styles.css", "img/locator.png").forEach { file ->
//                testReport.resolve(Paths.get(file)).outputStream().use {
//                    URL("http://127.0.0.1:$robotPort/$file").openStream().copyTo(it)
//                }
//            }
        }
    }
}
