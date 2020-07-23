// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import org.jdesktop.swingx.JXDatePicker;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class Queryeditor {
    public JRadioButton absoluteTimeRadioButton;
    public JRadioButton relativeTimeRadioButton;
    public JRadioButton searchterm;
    public JTextField querySearchTerm;
    public JRadioButton queryLogGroupsRadioButton;
    private JButton saveQueryButton;
    private JButton retrieveSavedQueriesButton;
    private SimpleToolWindowPanel tablePanel;
    public JTextArea queryBox;
    private JLabel Loggrouplabel;
    public JXDatePicker qstartDate;
    public JXDatePicker qendDate;
    public JPanel qpanel;
    public JComboBox RelativeTimeUnit;
    public JTextField RelativeTimeNumber;
    private final Project project;
    public ButtonGroup TimeRange;
    AddRemoveLogGroupTable a;

    Queryeditor(Project project){
        this.project=project;
        RelativeTimeUnit.addItem("Minutes");
        RelativeTimeUnit.addItem("Hours");
        RelativeTimeUnit.addItem("Days");
        RelativeTimeUnit.addItem("Weeks");
        qstartDate.setEnabled(false);
        qendDate.setEnabled(false);
        RelativeTimeNumber.setEnabled(false);
        RelativeTimeUnit.setEnabled(false);
        querySearchTerm.setEnabled(false);
        queryBox.setEnabled(false);
    }
    private void initArLogGroupTable(){
        a.getTableView().getListTableModel();
        a.getSelLogGroups();
    }
    private void createUIComponents() {
        // TODO: place custom component creation code here
        tablePanel=new SimpleToolWindowPanel(false,true);
        this.a=new AddRemoveLogGroupTable(project);
        initArLogGroupTable();
        tablePanel.setContent(a.getComponent());
    }
}
