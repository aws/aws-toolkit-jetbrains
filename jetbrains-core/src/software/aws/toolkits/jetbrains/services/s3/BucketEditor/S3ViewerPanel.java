package software.aws.toolkits.jetbrains.services.s3.BucketEditor;

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
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.treeTable.TreeTableModelWithColumns;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.DeleteObjectAction;
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.DownloadObjectAction;
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.RenameObjectAction;
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.UploadObjectAction;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualFile;
import software.aws.toolkits.jetbrains.ui.ProgressPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;

//import com.intellij.openapi.application.runInEdt;
//import com.intellij.openapi.roots.ui.configuration.classpath.ChangeLibraryLevelInClasspathAction;
//import com.intellij.ui.tree.treeTable.TreeTableModelWithColumns;

@SuppressWarnings("unchecked")

public class S3ViewerPanel {
    private JPanel content;
    private JPanel bucketName;
    private JLabel name;
    private JLabel creationDate;
    private JTextField date;
    private JPanel checkPanel;
    private JScrollPane pane;
    private S3TreeTable treeTable;
    private S3VirtualBucket bucketVirtual;
    private AnActionButton uploadObjectButton;
    private AnActionButton deleteObjectButton;
    private AnActionButton renameObjectButton;
    private AnActionButton downloadObjectButton;
    ProgressPanel progressPanel;

    public S3ViewerPanel(S3VirtualBucket bucketVirtual) {

        TitledBorder border = new TitledBorder("Bucket Details");
        border.setTitleJustification(TitledBorder.CENTER);
        border.setTitlePosition(TitledBorder.TOP);
        this.content.setBorder(border);
        this.bucketVirtual = bucketVirtual;
        this.name.setText(bucketVirtual.getVirtualBucketName());
        this.date.setText(bucketVirtual.getCreationDate().toString());
        this.creationDate.setText("Date");

        //Background thread
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
                                    else if(file instanceof S3VirtualDirectory)
                                        return ((S3VirtualDirectory) file).getName();
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
                        //
                        @Override
                        @Nullable
                        public Object valueOf(Object obj) {
                            if (obj instanceof DefaultMutableTreeNode) {
                                final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
                                if (userObject instanceof S3KeyNode) {
                                    VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                                    return (file instanceof S3VirtualFile) ? ((S3VirtualFile) file).getFile().getSize() : "";

                                }

                            }
                            return null;
                        }


                        public boolean isCellEditable(Object o) {
                            return true;
                        }
                    };

                    ColumnInfo modified = new ColumnInfo<Object, Object>("Last-Modified") {
                        //
                        @Override
                        @Nullable
                        public Object valueOf(Object obj) {

                            if (obj instanceof DefaultMutableTreeNode) {
                                final Object userObject = ((DefaultMutableTreeNode) obj).getUserObject();
                                if (userObject instanceof S3KeyNode) {
                                    VirtualFile file = ((S3KeyNode) userObject).getVirtualFile();
                                    return (file instanceof S3VirtualFile) ? ((S3VirtualFile) file).getFile().getLastModified() : "";

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
                    StructureTreeModel<SimpleTreeStructure> myTreeModel = new StructureTreeModel<>(treeStructure);

                    TreeTableModel model = new TreeTableModelWithColumns(new AsyncTreeModel(myTreeModel, true
                            , myTreeModelDisposable), COLUMNS);


                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {

                            S3TreeTable treeTable = new S3TreeTable(model);
                            treeTable.setRootVisible(false);

                            DefaultActionGroup actionGroup = new DefaultActionGroup();
                            uploadObjectButton = new UploadObjectAction(bucketVirtual, treeTable);
                            deleteObjectButton = new DeleteObjectAction(treeTable, bucketVirtual);
                            renameObjectButton = new RenameObjectAction(treeTable, bucketVirtual);
                            downloadObjectButton = new DownloadObjectAction(treeTable, bucketVirtual);

                            actionGroup.add(uploadObjectButton);
                            actionGroup.add(deleteObjectButton);
                            actionGroup.add(renameObjectButton);
                            actionGroup.add(downloadObjectButton);
                            PopupHandler.installPopupHandler(treeTable, actionGroup, ActionPlaces.EDITOR_POPUP, ActionManager.getInstance());
                            treeTable.setRowSelectionAllowed(true);

                            checkPanel.add(new JScrollPane(treeTable));

                        }
                    }, ModalityState.defaultModalityState());

                });


    }

    private void createUIComponents() { }


    public JComponent getComponent() {
        return content;

    }

    public JLabel getName() {
        return name;
    }


}
