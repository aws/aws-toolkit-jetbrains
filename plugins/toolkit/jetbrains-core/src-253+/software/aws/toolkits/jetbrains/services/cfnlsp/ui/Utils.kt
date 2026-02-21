// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.net.URLEncoder
import javax.swing.Icon
import javax.swing.ImageIcon

internal object ConsoleUrlGenerator {
    fun generateUrl(arn: String): String =
        "https://console.aws.amazon.com/go/view?arn=${URLEncoder.encode(arn, "UTF-8")}"
}

internal object IconUtils {
    fun createBlueIcon(originalIcon: Icon): Icon {
        val size = 16
        val image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        originalIcon.paintIcon(null, g2d, 0, 0)

        g2d.color = JBColor(0x0366D6, 0x58A6FF)
        g2d.composite = AlphaComposite.SrcAtop
        g2d.fillRect(0, 0, size, size)

        g2d.dispose()
        return ImageIcon(image)
    }
}
