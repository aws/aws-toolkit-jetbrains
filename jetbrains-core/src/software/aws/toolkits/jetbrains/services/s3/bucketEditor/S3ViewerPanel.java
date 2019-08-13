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
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JPopupMenu;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.ActionEvent;

@SuppressWarnings("unchecked")
public class S3ViewerPanel {
    private JPanel content;
    private JPanel bucketName;
    private JLabel name;
    private JLabel creationDate;
    private JTextField date;
    private JPanel checkPanel;
    private JTextField arnText;
    private JLabel bucketArn;
    private S3VirtualBucket bucketVirtual;
    private S3TreeTable treeTable;
    private AnActionButton uploadObjectButton;
    private AnActionButton deleteObjectButton;
    private AnActionButton renameObjectButton;
    private AnActionButton downloadObjectButton;

    public S3ViewerPanel(S3VirtualBucket bucketVirtual) {
        TitledBorder border = new TitledBorder("Bucket Details");
        border.setTitleJustification(TitledBorder.CENTER);
        border.setTitlePosition(TitledBorder.TOP);
        this.content.setBorder(border);
        this.bucketVirtual = bucketVirtual;
        this.name.setText(bucketVirtual.getVirtualBucketName());
        this.date.setText(bucketVirtual.formatDate(bucketVirtual.getS3Bucket().getCreationDate()));

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

        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> {

                    S3KeyNode s3Node = new S3KeyNode(bucketVirtual);
                    ColumnInfo key = new ColumnInfo<Object, Object>("Key") {
                        @Override
                        @Nullable
                        public Object valueOf(Object obj) {
                            if (obj instanceof DefaultMutableTreeNode) {
                                final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
                                if (userObject instanceof S3KeyNode) {
                                    VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                                    if (file instanceof S3VirtualFile)
                                        return ((S3VirtualFile) file).getFile().getKey();
                                    else if (file instanceof S3VirtualDirectory)
                                        return file.getName();
                                    else return null;
                                }
                            }
                            return null;
                        }

                        public Class getColumnClass() {
                            return TreeTableModel.class;
                        }

                        public boolean isCellEditable(Object o) {
                            return true;
                        }
                    };


                    ColumnInfo eTag = new ColumnInfo<Object, Object>("Etag") {
                        @Override
                        @Nullable
                        public Object valueOf(Object obj) {
                            if (obj instanceof DefaultMutableTreeNode) {
                                final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
                                if (userObject instanceof S3KeyNode) {
                                    VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                                    return (file instanceof S3VirtualFile) ? ((S3VirtualFile) file).getFile().getETag() : "";
                                }
                            }
                            return null;
                        }

                        public boolean isCellEditable(Object o) {
                            return true;
                        }
                    };

                    ColumnInfo size = new ColumnInfo<Object, Object>("Size") {
                        @Override
                        @Nullable
                        public Object valueOf(Object obj) {
                            if (obj instanceof DefaultMutableTreeNode) {
                                final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
                                if (userObject instanceof S3KeyNode) {
                                    VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                                    return (file instanceof S3VirtualFile) ?
                                            ((S3VirtualFile) file).formatSize() : "";
                                }
                            }
                            return null;
                        }

                        public boolean isCellEditable(Object o) {
                            return true;
                        }
                    };

                    ColumnInfo modified = new ColumnInfo<Object, Object>("Last-Modified") {
                        @Override
                        @Nullable
                        public Object valueOf(Object obj) {
                            if (obj instanceof DefaultMutableTreeNode) {
                                final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
                                if (userObject instanceof S3KeyNode) {
                                    VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                                    return (file instanceof S3VirtualFile) ? ((S3VirtualFile) file).formatDate(
                                            ((S3VirtualFile) file).getFile().getLastModified()) : "";
                                }
                            }
                            return null;
                        }

                        public boolean isCellEditable(Object o) {
                            return true;
                        }
                    };

                    final ColumnInfo[] COLUMNS = new ColumnInfo[]{key, eTag, size, modified};

                    Disposable myTreeModelDisposable = Disposer.newDisposable();
                    SimpleTreeStructure treeStructure = new SimpleTreeStructure.Impl(s3Node);
                    StructureTreeModel<SimpleTreeStructure> myTreeModel = new StructureTreeModel(treeStructure, myTreeModelDisposable);
                    S3TreeTableModel model = new S3TreeTableModel(new AsyncTreeModel(myTreeModel, true
                            , myTreeModelDisposable), COLUMNS, myTreeModel);

                    S3TreeCellRenderer renderer = new S3TreeCellRenderer();
                    DefaultTableCellRenderer tableRenderer = new DefaultTableCellRenderer();
                    tableRenderer.setHorizontalAlignment(SwingConstants.CENTER);
                    DefaultActionGroup actionGroup = new DefaultActionGroup();

                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            treeTable = new S3TreeTable(model);
                            treeTable.setRootVisible(false);
                            treeTable.setDragEnabled(true);
                            treeTable.setDefaultRenderer(Object.class, tableRenderer);
                            treeTable.setTreeCellRenderer(renderer);

                            deleteObjectButton = new DeleteObjectAction(treeTable, bucketVirtual);
                            downloadObjectButton = new DownloadObjectAction(treeTable, bucketVirtual);
                            renameObjectButton = new RenameObjectAction(treeTable, bucketVirtual);
                            uploadObjectButton = new UploadObjectAction(bucketVirtual, treeTable);

                            actionGroup.add(deleteObjectButton);
                            actionGroup.add(downloadObjectButton);
                            actionGroup.add(renameObjectButton);
                            actionGroup.add(uploadObjectButton);
                            PopupHandler.installPopupHandler(treeTable, actionGroup, ActionPlaces.EDITOR_POPUP, ActionManager.getInstance());
                            treeTable.setRowSelectionAllowed(true);
                            checkPanel.add(new JScrollPane(treeTable));
                        }
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
}