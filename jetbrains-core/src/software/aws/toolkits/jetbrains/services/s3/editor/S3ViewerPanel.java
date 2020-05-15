// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.editor;

import static software.aws.toolkits.resources.Localization.message;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.util.ui.ColumnInfo;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import org.jetbrains.annotations.NotNull;
import software.aws.toolkits.jetbrains.services.s3.objectActions.CopyPathAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.DeleteObjectAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.DownloadObjectAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.NewFolderAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.RenameObjectAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.UploadObjectAction;
import software.aws.toolkits.jetbrains.ui.tree.AsyncTreeModel;
import software.aws.toolkits.jetbrains.ui.tree.StructureTreeModel;

@SuppressWarnings("unchecked")
public class S3ViewerPanel {
    private final JComponent component;
    private final Disposable disposable;
    private final S3TreeTable treeTable;
    private final Project project;
    private final ColumnInfo<Object, Object>[] columns;
    private final S3VirtualBucket virtualBucket;
    private final StructureTreeModel<SimpleTreeStructure> structureTreeModel;
    private final S3TreeDirectoryNode rootNode;

    public S3ViewerPanel(Disposable disposable, Project project, S3VirtualBucket bucketVirtual) {
        this.project = project;
        this.disposable = disposable;
        this.virtualBucket = bucketVirtual;

        ColumnInfo<Object, String> key = new S3Column(S3ColumnType.NAME);
        ColumnInfo<Object, String> size = new S3Column(S3ColumnType.SIZE);
        ColumnInfo<Object, String> modified = new S3Column(S3ColumnType.LAST_MODIFIED);
        columns = new ColumnInfo[] {key, size, modified};

        rootNode = new S3TreeDirectoryNode(bucketVirtual, null, "");
        structureTreeModel = new StructureTreeModel<>(new SimpleTreeStructure.Impl(rootNode), disposable);
        S3TreeTableModel model = new S3TreeTableModel(new AsyncTreeModel(structureTreeModel, true, disposable), columns, structureTreeModel);
        treeTable = new S3TreeTable(model, bucketVirtual, project);
        applyTreeStyle(treeTable);
        addTreeActions(treeTable);

        ToolbarDecorator panel = addToolbar(treeTable);
        component = panel.createPanel();
    }

    private void createUIComponents() {
    }

    private ToolbarDecorator addToolbar(S3TreeTable table) {
        DefaultActionGroup group = makeActionGroup(table);
        group.addAction(new AnAction(message("explorer.refresh.title"), null, AllIcons.Actions.Refresh) {
            @Override
            public boolean isDumbAware() {
                return true;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                rootNode.removeAllChildren();
                structureTreeModel.invalidate();
            }
        });
        return ToolbarDecorator
            .createDecorator(table)
            .setActionGroup(group);
    }

    private void applyTreeStyle(S3TreeTable table) {
        DefaultTableCellRenderer tableRenderer = new DefaultTableCellRenderer();
        tableRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        table.setDefaultRenderer(Object.class, tableRenderer);
        table.setRootVisible(false);
        S3TreeCellRenderer treeRenderer = new S3TreeCellRenderer(table);
        table.setTreeCellRenderer(treeRenderer);
        table.setCellSelectionEnabled(false);
        table.setRowSelectionAllowed(true);
        table.setRowSorter(new S3RowSorter(table.getModel()));
        // prevent accidentally moving the columns around. We don't account for the ability
        // to do this anywhere so better be safe than sorry. TODO audit logic to allow this
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(1).setMaxWidth(120);
    }

    public JComponent getComponent() {
        return component;
    }

    public JComponent getFocusComponent() {
        return treeTable;
    }

    private void addTreeActions(S3TreeTable table) {
        PopupHandler.installPopupHandler(table, makeActionGroup(table), ActionPlaces.EDITOR_POPUP, ActionManager.getInstance());
    }

    private DefaultActionGroup makeActionGroup(S3TreeTable table) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new DownloadObjectAction(project, table));
        actionGroup.add(new UploadObjectAction(project, table));
        actionGroup.add(new Separator());
        actionGroup.add(new NewFolderAction(project, table));
        actionGroup.add(new RenameObjectAction(project, table));
        actionGroup.add(new CopyPathAction(project, table));
        actionGroup.add(new Separator());
        actionGroup.add(new DeleteObjectAction(project, table));
        return actionGroup;
    }
}
