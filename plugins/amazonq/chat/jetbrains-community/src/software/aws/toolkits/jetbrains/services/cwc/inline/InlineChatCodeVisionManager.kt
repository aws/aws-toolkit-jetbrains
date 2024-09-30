//import com.intellij.codeInsight.hints.ChangeListener
//import com.intellij.codeInsight.hints.ImmediateConfigurable
//import com.intellij.codeInsight.hints.InlayHintsCollector
//import com.intellij.codeInsight.hints.InlayHintsManager
//import com.intellij.codeInsight.hints.InlayHintsProvider
//import com.intellij.codeInsight.hints.InlayHintsSink
//import com.intellij.codeInsight.hints.NoSettings
//import com.intellij.codeInsight.hints.presentation.InlayPresentation
//import com.intellij.openapi.editor.Editor
//import com.intellij.openapi.editor.event.SelectionEvent
//import com.intellij.openapi.editor.event.SelectionListener
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiFile
//import javax.swing.JPanel
//
//class InlineChatCodeVisionManager : InlayHintsProvider<NoSettings> {
//    private var selectionListener: SelectionListener? = null
//
//    override fun createConfigurable(settings: NoSettings) = object : ImmediateConfigurable {
//        override fun createComponent(listener: ChangeListener) = JPanel()
//    }
//
//    override fun getCollectorFor(
//        file: PsiFile,
//        editor: Editor,
//        settings: NoSettings,
//        sink: InlayHintsSink
//    ): InlayHintsCollector? {
//        val project = file.project
//
//        // Remove any existing listener
//        selectionListener?.let { editor.selectionModel.removeSelectionListener(it) }
//
//        // Create a new listener
//        selectionListener = object : SelectionListener {
//            override fun selectionChanged(e: SelectionEvent) {
//                // Trigger a refresh of inlay hints
//                InlayHintsManager.getInstance(project).refreshInlayHints(editor)
//            }
//        }
//
//        // Add the new listener
//        editor.selectionModel.addSelectionListener(selectionListener!!)
//
//        return object : InlayHintsCollector {
//            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
//                val selectionModel = editor.selectionModel
//                val selectionStart = selectionModel.selectionStart
//                val selectionEnd = selectionModel.selectionEnd
//
//                if (selectionStart == selectionEnd) {
//                    return true // No selection, no hints
//                }
//
//                if (element.textRange.intersects(selectionStart, selectionEnd)) {
//                    val elementType = element.node.elementType.toString().toLowerCase()
//                    if (elementType.contains("function") || elementType.contains("method") || elementType.contains("class")) {
//                        val hint = createInlayPresentation(element, editor, project)
//                        sink.addInlineElement(element.textOffset, true, hint)
//                    }
//                }
//
//                return true
//            }
//        }
//    }
//
//    private fun createInlayPresentation(element: PsiElement, editor: Editor, project: Project): InlayPresentation {
//        // Create and return your inlay presentation here
//        // This could be a simple text presentation or a more complex clickable presentation
//    }
//
//    override fun getKey() = SettingsKey<NoSettings>("InlineChatCodeVision")
//    override fun getName() = "Inline Chat Code Vision"
//    override fun createSettings() = NoSettings()
//
//    override fun dispose() {
//        // Clean up the listener when the provider is disposed
//        selectionListener = null
//    }
//}
