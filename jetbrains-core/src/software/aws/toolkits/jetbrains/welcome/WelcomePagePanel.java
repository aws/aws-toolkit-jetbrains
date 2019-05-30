// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.welcome;

import com.intellij.ide.BrowserUtil;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent.EventType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

public class WelcomePagePanel {
    JPanel contentPanel;
    JEditorPane editorPane;
    private JScrollPane scrollPane;

    private void createUIComponents() {
        try {
            URL url = this.getClass().getResource("/html/welcome1.html");
            editorPane = new JEditorPane();
            editorPane.setEditable(false);
            editorPane.setPage(url);
            editorPane.addHyperlinkListener(e -> {
                if (e.getEventType() == EventType.ACTIVATED) {
                    BrowserUtil.browse(e.getURL());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
