package software.aws.toolkits.jetbrains.services.lambda.nodejs

import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.JSAssignmentExpression
import com.intellij.lang.javascript.psi.JSExpressionStatement
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSParameter
import com.intellij.lang.javascript.psi.resolve.JSClassResolver
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerResolver

class NodeJSLambdaHandlerResolver : LambdaHandlerResolver {
    override fun findPsiElements(project: Project, handler: String, searchScope: GlobalSearchScope): Array<NavigatablePsiElement> {
        val dot = handler.indexOf(".")
        if (dot <= 0) return emptyArray()
        val elements = JSClassResolver.findElementsByNameIncludingImplicit(handler.substring(dot + 1), searchScope, false)
        val filename = handler.substring(0, dot)
        return elements.filter { it is NavigatablePsiElement && FileUtilRt.getNameWithoutExtension(it.containingFile.name) == filename }.toTypedArray()
    }

    override fun determineHandler(element: PsiElement): String? {
        if (element.node?.elementType != JSTokenTypes.IDENTIFIER) {
            return null
        }
        val parameter = element.parent as? JSParameter ?: return null
        val function = parameter.declaringFunction ?: return null
        if (function.parent is JSAssignmentExpression && function.parent.parent is JSExpressionStatement) {
            val fileCandidate = function.parent.parent.parent
            if (fileCandidate is JSFile &&
                function.parameters.size == 2 &&
                function.parameters[0] == parameter) {
                return FileUtilRt.getNameWithoutExtension(fileCandidate.name) + "." + function.qualifiedName
            }
        }
        return null
    }
}