package software.aws.toolkits.jetbrains.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.ui.ComboBox;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackager;

public class SamInitRuntimeSelectionPanel extends ModuleWizardStep {
    private JPanel mainPanel;
    private ComboBox<Runtime> runtime;

    private SamInitModuleBuilder builder;
    private WizardContext context;

    SamInitRuntimeSelectionPanel(SamInitModuleBuilder builder, WizardContext context) {
        this.builder = builder;
        this.context = context;

        LambdaPackager.Companion.getSupportedRuntimeGroups()
                .stream()
                .flatMap(x -> x.getRuntimes().stream())
                .sorted()
                .forEach(y -> runtime.addItem(y));
    }

    @Override
    public void updateDataModel() {
        context.setProjectBuilder(builder);
        builder.setRuntime((Runtime) runtime.getSelectedItem());
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }
}
