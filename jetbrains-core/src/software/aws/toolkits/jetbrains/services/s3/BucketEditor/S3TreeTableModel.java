package software.aws.toolkits.jetbrains.services.s3.BucketEditor;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.concurrency.Promise;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualBucket;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualDirectory;
import software.aws.toolkits.jetbrains.services.s3.S3VirtualFile;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;

public class S3TreeTableModel implements TreeTableModel, TreeVisitor.Acceptor {

    String[] columnNames = {"Key", "ETag", "Size", "Last Modified"};
    Class[] classTypes = {TreeTableModel.class, String.class, String.class, String.class};

    VirtualFile file;
    AsyncTreeModel asyncTreeModel;
    private Disposable myTreeModelDisposable = Disposer.newDisposable();


    S3TreeTableModel(VirtualFile vFile) {
        super();
        file = vFile;
        asyncTreeModel = new AsyncTreeModel(this, false, myTreeModelDisposable);

    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public String getColumnName(int column) {
        return columnNames[column];
    }

    public Class getColumnClass(int column) {
        return classTypes[column];
    }


    @Override
    public Object getValueAt(Object node, int column) {

        switch (column) {
            case 0:
                return "";
            case 1:
                return (node instanceof S3VirtualFile) ? ((S3VirtualFile) node).getFile().getETag() : "";
            case 2:
                return (node instanceof S3VirtualFile) ? ((S3VirtualFile) node).getFile().getSize() : "";
            case 3:
                return (node instanceof S3VirtualFile) ? ((S3VirtualFile) node).getFile().getLastModified() : "";

        }
        return null;
    }


    @Override
    public int getChildCount(Object parent) {
        VirtualFile vFile = (VirtualFile) parent;
        if (vFile instanceof S3VirtualFile) {
            return 0;
        } else if (vFile instanceof S3VirtualBucket) {
            return getChildren(vFile).length;
        } else if (vFile instanceof S3VirtualDirectory) {
            return getChildren(vFile).length;
        }
        return 0;
    }


    public Object[] getChildren(Object parent) {
        VirtualFile vFile = (VirtualFile) parent;


        TreeVisitor visitor = path -> {
            AbstractTreeNode node = TreeUtil.getLastUserObject(AbstractTreeNode.class, path);
            if (node != null) node.update();
            return TreeVisitor.Action.CONTINUE;
        };

        accept(visitor);

        if (vFile instanceof S3VirtualBucket) {
            return ((S3VirtualBucket) parent).getChildren();
        } else if (vFile instanceof S3VirtualFile) {
            return null;
        } else {
            return ((S3VirtualDirectory) parent).getChildren();
        }
    }

    @Override
    public Object getChild(Object parent, int index) {
        return getChildren(parent)[index];
    }

    public boolean isS3VirtualFile() {
        if (file instanceof S3VirtualFile) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
        return false;
    }

    @Override
    public void setValueAt(Object aValue, Object node, int column) {
        return;
    }

    @Override
    public void setTree(JTree tree) {
        return;
    }

    @Override
    public Object getRoot() {
        if (file instanceof S3VirtualBucket) {
            return file;
        }
        return null;
    }

    @Override
    public boolean isLeaf(Object node) {
        VirtualFile vFile = (VirtualFile) node;
        if (vFile instanceof S3VirtualFile) {
            return true;
        }
        return false;

//        return getChildCount(node) ==0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        return;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        for (int i = 0; i < getChildCount(parent); i++) {
            if (getChild(parent, i).equals(child)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        return;
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        return;
    }

    @Override
    public Promise<TreePath> accept(TreeVisitor visitor) {
        Promise<TreePath> treePath = asyncTreeModel.accept(visitor);
        return treePath;
    }



}


