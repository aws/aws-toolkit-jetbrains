package software.aws.toolkits.jetbrains.services.s3.bucketEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeTableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;
import software.aws.toolkits.jetbrains.services.s3.S3TreeCellRenderer;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualFile;
import software.aws.toolkits.jetbrains.services.s3.objectActions.DeleteObjectAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.DownloadObjectAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.RenameObjectAction;
import software.aws.toolkits.jetbrains.services.s3.objectActions.UploadObjectAction;
import software.aws.toolkits.jetbrains.ui.tree.AsyncTreeModel;
import software.aws.toolkits.jetbrains.ui.tree.StructureTreeModel;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPopupMenu;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.RowFilter;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings("unchecked")
public class S3ViewerPanel {
    private JPanel content;
    private JPanel bucketName;
    private JLabel name;
    private JLabel creationDate;
    private JTextField date;
    private JPanel mainPanel;
    private JTextField arnText;
    private JLabel bucketArn;
    private JPanel searchPanel;
    private JPanel paginationPanel;
    private JButton searchButton;
    private JTextField searchTextField;
    private S3VirtualBucket bucketVirtual;
    private S3TreeTable treeTable;
    private AnActionButton uploadObjectButton;
    private AnActionButton deleteObjectButton;
    private AnActionButton renameObjectButton;
    private AnActionButton downloadObjectButton;
    private S3KeyNode s3Node;
    private S3TreeTableModel model;
    private final int MIN_SIZE = 0;
    private final int UPDATE_LIMIT = 10;

    public S3ViewerPanel(S3VirtualBucket bucketVirtual) {
        TitledBorder border = new TitledBorder("Bucket Details");
        border.setTitleJustification(TitledBorder.CENTER);
        border.setTitlePosition(TitledBorder.TOP);
        this.content.setBorder(border);
        this.bucketVirtual = bucketVirtual;
        this.name.setText(bucketVirtual.getVirtualBucketName());
        this.date.setText(bucketVirtual.formatDate(bucketVirtual.getS3Bucket().getCreationDate()));

        this.paginationPanel.setLayout(new FlowLayout((FlowLayout.RIGHT)));
        this.searchButton.setText("Search");
        this.searchTextField.setText("");

        this.arnText.setText("arn:aws:s3:::" + bucketVirtual.getVirtualBucketName());
        this.bucketArn.setText("Bucket Arn:");
        this.creationDate.setText("Creation Date:");
        this.date.setEditable(false);
        this.arnText.setEditable(false);
        JPopupMenu menu = new JPopupMenu();
        Action copyAction = new AbstractAction("Copy") {
            @Override
            public void actionPerformed(ActionEvent ae) {
                arnText.selectAll();
                arnText.copy();
            }
        };
        copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("command C"));
        menu.add(copyAction);
        arnText.setComponentPopupMenu(menu);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            s3Node = new S3KeyNode(bucketVirtual);
            ColumnInfo key = new ColumnInfo<Object, Object>("Key") {
                @Override
                @Nullable
                public Object valueOf(Object obj) {
                    VirtualFile file = getVirtualFileFromNode(obj);
                    if (file instanceof S3VirtualFile)
                        return ((S3VirtualFile) file).getFile().getKey();
                    else if (file instanceof S3VirtualDirectory)
                        return file.getName();
                    else return null;
                }

                public Class getColumnClass() {
                    return TreeTableModel.class;
                }

                public boolean isCellEditable(Object o) {
                    return true;
                }
            };

            ColumnInfo size = new ColumnInfo<Object, Object>("Size") {
                @Override
                @Nullable
                public Object valueOf(Object obj) {
                    VirtualFile file = getVirtualFileFromNode(obj);
                    return (file instanceof S3VirtualFile) ?
                            ((S3VirtualFile) file).formatSize() : "";
                }

                public boolean isCellEditable(Object o) {
                    return true;
                }


            };

            ColumnInfo modified = new ColumnInfo<Object, Object>("Last-Modified") {
                @Override
                @Nullable
                public Object valueOf(Object obj) {
                    VirtualFile file = getVirtualFileFromNode(obj);
                    return (file instanceof S3VirtualFile) ? ((S3VirtualFile) file).formatDate(
                            ((S3VirtualFile) file).getFile().getLastModified()) : "";
                }

                public boolean isCellEditable(Object o) {
                    return true;
                }
            };

            ColumnInfo eTag = new ColumnInfo<Object, Object>("Etag") {
                @Override
                @Nullable
                public Object valueOf(Object obj) {
                    VirtualFile file = getVirtualFileFromNode(obj);
                    return (file instanceof S3VirtualFile) ? ((S3VirtualFile) file).getFile().getETag().replace("\"", "") : "";
                }

                public boolean isCellEditable(Object o) {
                    return true;
                }
            };

            final ColumnInfo[] COLUMNS = new ColumnInfo[]{key, size, modified, eTag};
            createTreeTable(COLUMNS);

            DefaultActionGroup actionGroup = new DefaultActionGroup();
            S3TreeCellRenderer treeRenderer = new S3TreeCellRenderer();
            DefaultTableCellRenderer tableRenderer = new DefaultTableCellRenderer();
            tableRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
            /**
             *  Navigation buttons for pages
             */
            JButton next = new JButton(">");
            JButton previous = new JButton("<");
            ActionListener listener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    next.setEnabled(true);
                    previous.setEnabled(true);

                    if (e.getSource() == next) {
                        s3Node.updateLimitsOnButtonClick(true);
                        if (s3Node.getNext() == s3Node.getCurrSize()) next.setEnabled(false);

                    } else if (e.getSource() == previous) {
                        s3Node.updateLimitsOnButtonClick(false);
                        if (s3Node.getPrev() == 0) previous.setEnabled(false);
                    }
                    treeTable.refresh();
                }
            };

            ApplicationManager.getApplication().invokeLater(() -> {
                next.addActionListener(listener);
                previous.addActionListener(listener);
                paginationPanel.add(previous);
                paginationPanel.add(next);

                treeTable = new S3TreeTable(model);
                new TreeTableSpeedSearch(treeTable).setComparator(new SpeedSearchComparator(false));
                treeTable.setRootVisible(false);
                treeTable.setDefaultRenderer(Object.class, tableRenderer);
                treeTable.setTreeCellRenderer(treeRenderer);

                JBScrollPane scrollPane = new JBScrollPane(treeTable, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

                treeTable.setRowSelectionAllowed(true);
                int width = treeTable.getPreferredSize().width;
                final int scrollPaneSize = 11;
                scrollPane.setPreferredSize(new Dimension(width, treeTable.getRowHeight() * scrollPaneSize));

                treeTable.setAutoCreateRowSorter(true);
                searchAndSortTable();

                deleteObjectButton = new DeleteObjectAction(treeTable, bucketVirtual, searchButton, searchTextField);
                downloadObjectButton = new DownloadObjectAction(treeTable, bucketVirtual);
                renameObjectButton = new RenameObjectAction(treeTable, bucketVirtual);
                uploadObjectButton = new UploadObjectAction(bucketVirtual, treeTable, searchButton, searchTextField);
                actionGroup.add(deleteObjectButton);
                actionGroup.add(downloadObjectButton);
                actionGroup.add(renameObjectButton);
                actionGroup.add(uploadObjectButton);
                PopupHandler.installPopupHandler(treeTable, actionGroup, ActionPlaces.EDITOR_POPUP, ActionManager.getInstance());
                treeTable.getColumnModel().getColumn(1).setMaxWidth(120);
                mainPanel.add(scrollPane, BorderLayout.CENTER);
            }, ModalityState.defaultModalityState());
        });
    }

    private void createUIComponents() {
    }

    public JComponent getComponent() {
        return content;
    }

    public JLabel getName() {
        return name;
    }

    private VirtualFile getVirtualFileFromNode(Object obj) {
        if (obj instanceof DefaultMutableTreeNode) {
            final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
            if (userObject instanceof S3KeyNode) {
                VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                return file;
            }
        }
        return null;
    }

    private void createTreeTable(ColumnInfo[] columns) {
        Disposable myTreeModelDisposable = Disposer.newDisposable();
        SimpleTreeStructure treeStructure = new SimpleTreeStructure.Impl(s3Node);
        StructureTreeModel<SimpleTreeStructure> myTreeModel = new StructureTreeModel(treeStructure, myTreeModelDisposable);
        model = new S3TreeTableModel(new AsyncTreeModel(myTreeModel, true
                , myTreeModelDisposable), columns, myTreeModel);
    }

    /**
     * Search and sort TreeTable based on text in TextField
     */
    private void searchAndSortTable() {
        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(treeTable.getModel());
        treeTable.setRowSorter(sorter);
        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String text = searchTextField.getText();
                if (text.isEmpty()) {
                    s3Node.setPrev(MIN_SIZE);
                    s3Node.setNext(Math.min(UPDATE_LIMIT, s3Node.getCurrSize()));
                    sorter.setRowFilter(null);
                } else {
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        s3Node.resetLimitsForSearch();
                    });
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                }
                sorter.setSortKeys(null);
                treeTable.refresh();
            }
        });
    }
}