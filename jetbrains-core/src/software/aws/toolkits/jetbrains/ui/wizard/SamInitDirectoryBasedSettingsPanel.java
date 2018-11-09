package software.aws.toolkits.jetbrains.ui.wizard;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.util.List;
import com.intellij.openapi.ui.ComboBox;

public class SamInitDirectoryBasedSettingsPanel {
    javax.swing.JTextField samExecutableField;
    ComboBox<SamProjectTemplate> templateField;
    private JPanel mainPanel;
    private JButton editSamExecutableButton;

    SamInitDirectoryBasedSettingsPanel(List<SamProjectTemplate> templateList) {
        for (SamProjectTemplate template : templateList) {
            templateField.addItem(template);
        }

        SamInitWizardUtilsKt.setupSamSelectionElements(samExecutableField, editSamExecutableButton);
    }

    public JPanel getComponent() {
        return mainPanel;
    }
}
