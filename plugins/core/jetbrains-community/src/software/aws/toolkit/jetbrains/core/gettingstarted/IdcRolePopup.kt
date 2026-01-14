// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.gettingstarted

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import org.jetbrains.annotations.VisibleForTesting
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.sso.model.RoleInfo
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import software.aws.toolkit.jetbrains.core.AwsClientManager
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManagerConnection
import software.aws.toolkit.jetbrains.core.credentials.ConfigFilesFacade
import software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import software.aws.toolkit.jetbrains.core.credentials.DefaultConfigFilesFacade
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkit.jetbrains.core.credentials.profiles.ProfileWatcher
import software.aws.toolkit.jetbrains.ui.AsyncComboBox
import software.aws.toolkit.jetbrains.utils.ui.selected
import software.aws.toolkit.resources.AwsCoreBundle

data class IdcRolePopupState(
    var roleInfo: RoleInfo? = null,
)

class IdcRolePopup(
    private val project: Project,
    private val region: String,
    private val sessionName: String,
    private val tokenProvider: SdkTokenProvider,
    val state: IdcRolePopupState = IdcRolePopupState(),
    private val configFilesFacade: ConfigFilesFacade = DefaultConfigFilesFacade(),
) : DialogWrapper(project) {
    init {
        title = AwsCoreBundle.message("gettingstarted.setup.idc.role.title")
        init()
    }

    override fun showAndGet(): Boolean {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return false
        }

        return super.showAndGet()
    }

    private val client = AwsClientManager.Companion.getInstance().createUnmanagedClient<SsoClient>(
        AnonymousCredentialsProvider.create(),
        Region.of(region)
    )

    override fun dispose() {
        client.close()
        super.dispose()
    }

    override fun createCenterPanel() = panel {
        row {
            label(AwsCoreBundle.message("gettingstarted.setup.idc.roleLabel"))
        }

        row {
            val combo = AsyncComboBox<RoleInfo> { label, value, _ ->
                value ?: return@AsyncComboBox
                label.text = "${value.roleName()} (${value.accountId()})"
            }

            Disposer.register(myDisposable, combo)
            combo.proposeModelUpdate { model ->
                val token = tokenProvider.resolveToken().token()

                val rolesList = client.listAccountsPaginator { it.accessToken(token) }
                    .accountList()
                    .flatMap { account ->
                        client.listAccountRolesPaginator {
                            it.accessToken(token)
                            it.accountId(account.accountId())
                        }.roleList()
                    }

                rolesList.sortedBy { it.roleName() }
                    .forEach {
                        model.addElement(it)
                    }

                state.roleInfo?.let {
                    model.selectedItem = it
                }
            }

            cell(combo)
                .align(AlignX.FILL)
                .errorOnApply(AwsCoreBundle.message("gettingstarted.setup.error.not_selected")) { it.selected() == null }
                .bindItem(state::roleInfo.toNullableProperty())
        }
    }

    @VisibleForTesting
    public override fun doOKAction() {
        if (!okAction.isEnabled) {
            return
        }
        applyFields()

        val roleInfo = state.roleInfo
        checkNotNull(roleInfo)

        doOkActionWithRoleInfo(roleInfo)

        close(OK_EXIT_CODE)
    }

    @VisibleForTesting
    fun doOkActionWithRoleInfo(roleInfo: RoleInfo) {
        val profileName = "$sessionName-${roleInfo.accountId()}-${roleInfo.roleName()}"
        if (profileName !in configFilesFacade.readAllProfiles().keys) {
            configFilesFacade.appendProfileToConfig(
                Profile.builder()
                    .name(profileName)
                    .properties(
                        mapOf(
                            "sso_session" to sessionName,
                            "sso_account_id" to roleInfo.accountId(),
                            "sso_role_name" to roleInfo.roleName()
                        )
                    )
                    .build()
            )
        }

        // force CredentialManager to pick up change
        ProfileWatcher.getInstance().forceRefresh()

        CredentialManager.getInstance().getCredentialIdentifierById("profile:$profileName")?.let {
            ToolkitConnectionManager.Companion.getInstance(project).switchConnection(
                AwsConnectionManagerConnection(
                    project
                )
            )
            AwsConnectionManager.Companion.getInstance(project).changeCredentialProvider(it)
        } ?: let {
            LOG.warn { "Could not autoswitch to profile $profileName" }
        }
    }

    companion object {
        private val LOG = getLogger<IdcRolePopup>()
    }
}
