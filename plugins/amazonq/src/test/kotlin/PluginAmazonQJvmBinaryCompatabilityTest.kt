// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PluginAmazonQJvmBinaryCompatabilityTest {
    @Test
    fun `AuthController is available`() {
        // v1.0.133.0 of internal plugin
        // $ javap -c -classpath aws-toolkit-amazonq-2024.1.jar <...>.amazonq.AmazonQConnectionService
        //   public final boolean isAuthenticated();
        //       0: new           #27                 // class software/aws/toolkits/jetbrains/services/amazonq/auth/AuthController
        //       3: dup
        //       4: invokespecial #28                 // Method software/aws/toolkits/jetbrains/services/amazonq/auth/AuthController."<init>":()V
        //       7: aload_0
        //       8: getfield      #21                 // Field project:Lcom/intellij/openapi/project/Project;
        //      11: invokevirtual #32                 // Method software/aws/toolkits/jetbrains/services/amazonq/auth/AuthController.getAuthNeededStates:(Lcom/intellij/openapi/project/Project;)Lsoftware/aws/toolkits/jetbrains/services/amazonq/auth/AuthNeededStates;
        //      14: astore_1
        //      15: aload_1
        //      16: invokevirtual #38                 // Method software/aws/toolkits/jetbrains/services/amazonq/auth/AuthNeededStates.getAmazonQ:()Lsoftware/aws/toolkits/jetbrains/services/amazonq/auth/AuthNeededState;

        // not really sure if they should be using AuthController to check this...
        val authControllerClazz = Class.forName("software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController")
        val authNeededStatesClazz = Class.forName("software.aws.toolkits.jetbrains.services.amazonq.auth.AuthNeededStates")

        assertThat(authControllerClazz.getConstructor().canAccess(null)).isTrue()
        // type erasure :/
        assertThat(authControllerClazz.getMethod("getAuthNeededStates", Class.forName("com.intellij.openapi.project.Project")).returnType).isEqualTo(authNeededStatesClazz)
        assertThat(authNeededStatesClazz.getMethod("getAmazonQ").returnType).isEqualTo(Class.forName("software.aws.toolkits.jetbrains.services.amazonq.auth.AuthNeededState"))
    }

    @Test
    fun `CodeWhisperer customization classes are available`() {
        // v1.0.133.0 of internal plugin
        // $ javap -c -classpath aws-toolkit-amazonq-2024.1.jar <...>.amazonq.AmazonQConfigurationServiceKt
        //   public static final software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization findCustomizationToUse(java.util.List<software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization>);
        //      45: getfield      #40                 // Field software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererCustomization.arn:Ljava/lang/String;
        //      50: invokestatic  #46                 // Method kotlin/jvm/internal/Intrinsics.areEqual:(Ljava/lang/Object;Ljava/lang/Object;)Z
        //      58: getfield      #49                 // Field software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererCustomization.name:Ljava/lang/String;
        //      61: ldc           #51                 // String Amazon-Internal

        //   public static final void setCustomization(com.intellij.openapi.project.Project);
        //       3: invokestatic  #18                 // Method kotlin/jvm/internal/Intrinsics.checkNotNullParameter:(Ljava/lang/Object;Ljava/lang/String;)V
        //       6: getstatic     #72                 // Field migration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator.Companion:Lmigration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator$Companion;
        //       9: invokevirtual #78                 // Method migration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator$Companion.getInstance:()Lmigration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator;
        //      19: invokestatic  #82                 // InterfaceMethod migration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator.listCustomizations$default:(Lmigration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator;Lcom/intellij/openapi/project/Project;ZILjava/lang/Object;)Ljava/util/List;
        //      140: invokeinterface #118,  3          // InterfaceMethod migration/software/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererModelConfigurator.switchCustomization:(Lcom/intellij/openapi/project/Project;Lsoftware/aws/toolkits/jetbrains/services/codewhisperer/customization/CodeWhispererCustomization;)V

        assertThat(Class.forName("migration.software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator\$Companion").getMethod("getInstance").returnType)
            .isEqualTo(Class.forName("migration.software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator"))

        val modelConfigurator = Class.forName("migration.software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator")

        // type erasure :/
        assertThat(
            modelConfigurator.getMethod(
                "listCustomizations\$default",
                modelConfigurator,
                Class.forName("com.intellij.openapi.project.Project"),
                // can't request primitive type using reflection
                java.lang.Boolean.TYPE,
                Integer.TYPE,
                Class.forName("java.lang.Object")
            ).returnType
        ).isEqualTo(Class.forName("java.util.List"))

        val customization = Class.forName("software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization")
        assertThat(customization.getField("arn").type).isEqualTo(Class.forName("java.lang.String"))
        assertThat(customization.getField("name").type).isEqualTo(Class.forName("java.lang.String"))
    }
}
