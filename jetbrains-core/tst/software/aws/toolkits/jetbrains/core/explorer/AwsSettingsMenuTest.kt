// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import software.aws.toolkits.jetbrains.core.credentials.MockProjectAccountSettingsManager
// import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager.AccountSettingsChangedNotifier.AccountSettingsEvent

class AwsSettingsMenuTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private val mockSettingsManager by lazy { MockProjectAccountSettingsManager.getInstance(projectRule.project) }

//    @Test
//    fun itemsAreRefreshedWhenSettingsChange() {
//        val sut = AwsSettingsMenu(projectRule.project)
//
//        mockSettingsManager.changeRegion(AwsRegion.GLOBAL)
//        val accountSettingsEvent = AccountSettingsEvent(false, null, AwsRegion.GLOBAL)
//        projectRule.project.messageBus.syncPublisher(ProjectAccountSettingsManager.ACCOUNT_SETTINGS_CHANGED).settingsChanged(accountSettingsEvent)
//
//        val actionGroup = sut.getChildren(null).first() as DefaultActionGroup
//        val separators = actionGroup.getChildren(null).filterIsInstance<Separator>()
//
//        assertThat(separators).anySatisfy { assertThat(it.text).isEqualTo("Recent Regions") }
//    }
//
//    @Test
//    fun credentialsListIsRefreshed() {
//        val sut = AwsSettingsMenu(projectRule.project)
//        val credentialsManager = MockCredentialsManager.getInstance()
//
//        val actionGroup = sut.getChildren(null).first() as DefaultActionGroup
//        val credentialsActionGroup = actionGroup.getChildren(null).first { it.templatePresentation.text == "All Credentials" } as ActionGroup
//
//        credentialsManager.addCredentials("Creds", AwsBasicCredentials.create("Access", "Secret"))
//        val providers = credentialsManager.getCredentialProviders().map { it.displayName }.toList()
//        assertThat(credentialsActionGroup.getChildren(null).map { it.templatePresentation.text }).containsAll(providers)
//
//        credentialsManager.addCredentials("New Creds", AwsBasicCredentials.create("Access", "Secret"))
//        val updatedProviders = credentialsManager.getCredentialProviders().map { it.displayName }.toList()
//        assertThat(credentialsActionGroup.getChildren(null).map { it.templatePresentation.text }).containsAll(updatedProviders)
//    }
}
