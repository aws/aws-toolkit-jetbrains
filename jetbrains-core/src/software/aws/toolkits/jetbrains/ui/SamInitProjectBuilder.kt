package software.aws.toolkits.jetbrains.ui

import com.intellij.ide.projectWizard.ProjectTemplateList
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.platform.ProjectTemplate
import com.intellij.platform.ProjectTemplatesFactory
import com.intellij.platform.templates.BuilderBasedTemplate
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import javax.swing.Icon
import javax.swing.JComponent

class SamModuleType : ModuleType<SamInitModuleBuilder>(ID) {
    override fun getNodeIcon(p0: Boolean): Icon {
        return AwsIcons.Resources.LAMBDA_FUNCTION
    }

    override fun createModuleBuilder(): SamInitModuleBuilder {
        return SamInitModuleBuilder()
    }

    override fun getName(): String {
        return "SAM Name"
    }

    override fun getDescription(): String {
        return "SAM Module Type Description"
    }

    companion object {
        val ID = "SAM"
        val instance = SamModuleType()
    }
}

class SamInitModuleBuilder : ModuleBuilder() {
    lateinit var runtime: Runtime

    override fun getModuleType(): ModuleType<*> {
        return SamModuleType.instance
    }

    override fun setupRootModel(p0: ModifiableRootModel?) {
    }

    override fun getGroupName(): String {
        return "SAM"
    }

    override fun getCustomOptionsStep(context: WizardContext?, parentDisposable: Disposable?): ModuleWizardStep? {
        return SamInitRuntimeSelectionPanel(this, context)
    }

    override fun createWizardSteps(wizardContext: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
        return arrayOf(SamInitTemplateSelectionStep(this, wizardContext))
    }
}

class SamInitTemplateSelectionStep(
    val builder: SamInitModuleBuilder,
    val context: WizardContext
): ModuleWizardStep() {
    val templateSelectionPanel = ProjectTemplateList()

    init {
        templateSelectionPanel.setTemplates(listOf(
                object : ProjectTemplate {
                    override fun validateSettings() = null

                    override fun getIcon() = SAM_TEMPLATE_ICON

                    override fun createModuleBuilder() = EmptyModuleBuilder()

                    override fun getName() = "SAM Hello World"

                    override fun getDescription() = "Hello World Description"
                }
        ), true)
    }

    override fun updateDataModel() {
        context.projectBuilder = builder
    }

    override fun getComponent(): JComponent {
        return templateSelectionPanel
    }

    companion object {
        val SAM_TEMPLATE_ICON = AwsIcons.Resources.LAMBDA_FUNCTION

    }
}