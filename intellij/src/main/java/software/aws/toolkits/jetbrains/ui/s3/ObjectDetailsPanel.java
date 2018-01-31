package software.aws.toolkits.jetbrains.ui.s3;

import static software.amazon.awssdk.utils.FunctionalUtils.invokeSafely;

import com.amazonaws.intellij.utils.DateUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import java.awt.Insets;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.aws.toolkits.jetbrains.aws.s3.S3VirtualFile;
import software.aws.toolkits.jetbrains.ui.KeyValue;
import software.aws.toolkits.jetbrains.ui.KeyValueTableEditor;
import software.aws.toolkits.jetbrains.ui.MessageUtils;

public class ObjectDetailsPanel {
    private final S3VirtualFile s3File;
    private JPanel contentPanel;
    private JBLabel objectNameLabel;
    private JBLabel size;
    private JBLabel modifiedDate;
    private JButton copyArnButton;
    private JButton applyButton;
    private JButton cancelButton;
    private JTabbedPane tabbedPanel;
    private KeyValueTableEditor tags;
    private KeyValueTableEditor metadata;
    private JBLabel eTag;

    public ObjectDetailsPanel(S3VirtualFile s3File) {
        this.s3File = s3File;

        this.contentPanel.setBorder(IdeBorderFactory.createTitledBorder("Object Details", false));

        this.objectNameLabel.setText(s3File.getName());

        this.size.setText(StringUtil.formatFileSize(s3File.getLength()));
        this.modifiedDate.setText(DateUtils.formatDate(s3File.getTimeStamp()));
        this.eTag.setText(s3File.getETag());

        this.copyArnButton.addActionListener(e -> {
            String arn = "arn:aws:s3:::" + s3File.getPath();
            CopyPasteManager.getInstance().setContents(new TextTransferable(arn));
        });

        this.applyButton.addActionListener(actionEvent -> applyChanges());
        this.cancelButton.addActionListener(actionEvent -> cancelChanges());

        this.tags.refresh();
        this.metadata.refresh();
    }

    private void createUIComponents() {
        tags = new KeyValueTableEditor(this::loadTags, null, null, this::onValueChanged);
        metadata = new KeyValueTableEditor(this::loadMetadata, null, null, this::onValueChanged);

        tabbedPanel = new JBTabbedPane(SwingConstants.TOP) {
            @NotNull
            @Override
            protected Insets getInsetsForTabComponent() {
                return JBUI.emptyInsets();
            }
        };
    }

    //TODO: Move this out of here to some sort of DAL - Java files should only contain view code
    private List<KeyValue> loadTags() {
        S3Client s3Client = s3File.getFileSystem().getS3Client();
        GetObjectTaggingResponse objectTags = s3Client.getObjectTagging(it -> it.bucket(s3File.getBucketName()).key(s3File.getKey()));
        if (objectTags == null) {
            return Collections.emptyList();
        }

        return objectTags.tagSet()
                         .stream()
                         .map(entry -> new KeyValue(entry.key(), entry.value()))
                         .collect(Collectors.toList());
    }

    private void updateTags(List<KeyValue> newTags) {
        List<Tag> tags = newTags.stream()
                                .map(keyValue -> Tag.builder().key(keyValue.getKey()).value(keyValue.getValue()).build())
                                .collect(Collectors.toList());

        S3Client s3Client = s3File.getFileSystem().getS3Client();
        s3Client.putObjectTagging(it -> it.bucket(s3File.getBucketName()).key(s3File.getKey()).tagging(ts -> ts.tagSet(tags)));
    }

    private List<KeyValue> loadMetadata() {
        S3Client s3Client = s3File.getFileSystem().getS3Client();
        return s3Client.headObject(it -> it.bucket(s3File.getBucketName()).key(s3File.getKey()))
                       .metadata()
                       .entrySet()
                       .stream()
                       .map(entry -> new KeyValue(entry.getKey(), entry.getValue()))
                       .collect(Collectors.toList());
    }

    private void onValueChanged() {
        if (tags.isModified() || metadata.isModified()) {
            applyButton.setEnabled(true);
            cancelButton.setEnabled(true);
        } else {
            applyButton.setEnabled(false);
            cancelButton.setEnabled(false);
        }
    }

    private void applyChanges() {
        metadata.setBusy(true);
        tags.setBusy(true);
        applyButton.setEnabled(false);
        cancelButton.setEnabled(false);

        // To update metadata, we need to issue a copy
        if (metadata.isModified()) {
            CopyObjectRequest.Builder copyObjectRequest = CopyObjectRequest.builder().copySource(invokeSafely(() -> URLEncoder.encode(s3File.getBucketName() + "/" + s3File.getKey(), Charset.forName("UTF-8").toString())))
                .bucket(s3File.getBucketName())
                .key(s3File.getKey())
                .metadata(metadata.getItems().stream().collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue)));



            if (tags.isModified()) {
                copyObjectRequest.tagging(getObjectTagging());
            }

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                s3File.getFileSystem().getS3Client().copyObject(copyObjectRequest.build());
                metadata.refresh();
                tags.refresh();
            });
        } else {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                s3File.getFileSystem().getS3Client().putObjectTagging(it -> it.bucket(s3File.getBucketName())
                                                                              .key(s3File.getKey())
                                                                              .tagging(getObjectTagging()));
                tags.refresh();
            });
        }
    }

    private Tagging getObjectTagging() {
        return Tagging.builder().tagSet(tags.getItems()
                                     .stream()
                                     .map(keyValue -> Tag.builder().key(keyValue.getKey()).value(keyValue.getValue()).build())
                                     .collect(Collectors.toList())).build();
    }

    private void cancelChanges() {
        if (!MessageUtils.verifyLossOfChanges(contentPanel)) {
            return;
        }

        if (tags.isModified()) {
            tags.reset();
        }

        if (metadata.isModified()) {
            metadata.reset();
        }
    }

    public JComponent getComponent() {
        return contentPanel;
    }
}
