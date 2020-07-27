// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.michaelbaranov.microba.calendar.DatePicker;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;


public class QueryEditor {
    public JRadioButton absoluteTimeRadioButton;
    public JRadioButton relativeTimeRadioButton;
    public JRadioButton searchTerm;
    public JTextField querySearchTerm;
    public JRadioButton queryLogGroupsRadioButton;
    public JButton saveQueryButton;
    private JButton retrieveSavedQueriesButton;
    private SimpleToolWindowPanel tablePanel;
    public JTextArea queryBox;
    private JLabel LogGroupLabel;
    public DatePicker EndDate;
    public JPanel queryEditorBasePanel;
    public JComboBox relativeTimeUnit;
    public JTextField relativeTimeNumber;
    public DatePicker StartDate;
    private final Project project;
    public ButtonGroup TimeRange;
    AddRemoveLogGroupTable showLogGroupTable;

    QueryEditor(Project project) {
        this.project = project;
        StartDate.setEnabled(false);
        EndDate.setEnabled(false);
        relativeTimeNumber.setEnabled(false);
        relativeTimeUnit.setEnabled(false);
        querySearchTerm.setEnabled(false);
        queryBox.setEnabled(false);
    }
    private void initArLogGroupTable(){
        showLogGroupTable.getTableView().getListTableModel();
        showLogGroupTable.getSelLogGroups();
    }
    private void createUIComponents() {
        // TODO: place custom component creation code here
        tablePanel = new SimpleToolWindowPanel(false,true);
        this.showLogGroupTable = new AddRemoveLogGroupTable(project);
        initArLogGroupTable();
        tablePanel.setContent(showLogGroupTable.getComponent());
        String[] TimeUnits = new String[] {"Minutes", "Hours", "Days", "Weeks"};
        relativeTimeUnit = new ComboBox(TimeUnits);
    }
}
