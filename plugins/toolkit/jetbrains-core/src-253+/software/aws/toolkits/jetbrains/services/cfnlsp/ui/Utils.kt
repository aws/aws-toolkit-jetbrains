// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import software.amazon.awssdk.arns.Arn
import java.awt.AlphaComposite
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.net.URLEncoder
import javax.swing.Icon
import javax.swing.ImageIcon

internal object ConsoleUrlGenerator {
    fun generateUrl(arn: String): String =
        "https://console.aws.amazon.com/go/view?arn=${URLEncoder.encode(arn, "UTF-8")}"

    fun generateStackResourcesUrl(stackArn: String): String =
        arnToConsoleTabUrl(stackArn, "resources")

    fun generateStackOutputsUrl(stackArn: String): String =
        arnToConsoleTabUrl(stackArn, "outputs")

    fun generateStackEventsUrl(stackArn: String): String =
        arnToConsoleTabUrl(stackArn, "events")

    fun generateOperationUrl(arn: String, operationId: String): String {
        val region = try {
            Arn.fromString(arn).region().orElse("us-east-1")
        } catch (e: Exception) {
            "us-east-1"
        }
        return "https://$region.console.aws.amazon.com/cloudformation/home?region=$region#/stacks/operations/info?stackId=${URLEncoder.encode(
            arn,
            "UTF-8"
        )}&operationId=$operationId"
    }

    private fun arnToConsoleTabUrl(arn: String, tab: String): String {
        val region = try {
            Arn.fromString(arn).region().orElse("us-east-1")
        } catch (e: Exception) {
            "us-east-1"
        }
        return "https://$region.console.aws.amazon.com/cloudformation/home?region=$region#/stacks/$tab?stackId=${URLEncoder.encode(arn, "UTF-8")}"
    }
}

internal object IconUtils {
    private fun createBlueIcon(): Icon {
        val size = 16
        val image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        AllIcons.Ide.External_link_arrow.paintIcon(null, g2d, 0, 0)

        g2d.color = JBColor(0x0366D6, 0x58A6FF)
        g2d.composite = AlphaComposite.SrcAtop
        g2d.fillRect(0, 0, size, size)

        g2d.dispose()
        return ImageIcon(image)
    }

    fun createConsoleLinkIcon(urlProvider: () -> String?): JBLabel =
        JBLabel(createBlueIcon()).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isVisible = false
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    urlProvider()?.let { url ->
                        BrowserUtil.browse(url)
                    }
                }
            })
        }
}
