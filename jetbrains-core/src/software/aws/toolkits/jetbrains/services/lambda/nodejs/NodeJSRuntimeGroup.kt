package software.aws.toolkits.jetbrains.services.lambda.nodejs

import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.projectRoots.Sdk
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroupInformation

class NodeJSRuntimeGroup : RuntimeGroupInformation {
    override val runtimes: Set<Runtime> = setOf(
            Runtime.NODEJS,
            Runtime.NODEJS8_10,
            Runtime.NODEJS6_10,
            Runtime.NODEJS4_3,
            Runtime.NODEJS4_3_EDGE
    )

    override val languageIds: Set<String> = setOf(
            JavascriptLanguage.INSTANCE.id,
            JavaScriptSupportLoader.TYPESCRIPT.id,
            JavaScriptSupportLoader.FLOW_JS.id,
            JavaScriptSupportLoader.ECMA_SCRIPT_6.id
    )

    // TODO we need module here, NodeJS doesn't use Sdk concept
    override fun runtimeForSdk(sdk: Sdk): Runtime? = Runtime.NODEJS
}