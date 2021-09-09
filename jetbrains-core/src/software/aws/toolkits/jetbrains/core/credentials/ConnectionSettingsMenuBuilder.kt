// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.core.utils.buildList
import software.aws.toolkits.resources.message

class ConnectionSettingsMenuBuilder private constructor() {
    private data class RegionSelectionSettings(val currentSelection: AwsRegion?, val onChange: (AwsRegion) -> Unit)
    private data class CredentialsSelectionSettings(val currentSelection: CredentialIdentifier?, val onChange: (CredentialIdentifier) -> Unit)

    private var regionSelectionSettings: RegionSelectionSettings? = null
    private var credentialsSelectionSettings: CredentialsSelectionSettings? = null
    private var accountSettingsManager: AwsConnectionManager? = null

    fun withRegions(currentSelection: AwsRegion?, onChange: (AwsRegion) -> Unit): ConnectionSettingsMenuBuilder {
        regionSelectionSettings = RegionSelectionSettings(currentSelection, onChange)
        return this
    }

    fun withCredentials(currentSelection: CredentialIdentifier?, onChange: (CredentialIdentifier) -> Unit): ConnectionSettingsMenuBuilder {
        credentialsSelectionSettings = CredentialsSelectionSettings(currentSelection, onChange)
        return this
    }

    fun withRecentChoices(project: Project): ConnectionSettingsMenuBuilder {
        accountSettingsManager = AwsConnectionManager.getInstance(project)
        return this
    }

    fun build(): DefaultActionGroup {
        val topLevelGroup = DefaultActionGroup()

        val regionActions = createRegionActions()
        val regionSettings = regionSelectionSettings
        val recentRegions = accountSettingsManager?.recentlyUsedRegions()
        if (recentRegions?.isNotEmpty() == true && regionSettings != null) {
            topLevelGroup.add(Separator.create(message("settings.regions.recent")))
            recentRegions.forEach {
                topLevelGroup.add(SwitchRegionAction(it, it == regionSettings.currentSelection, regionSettings.onChange))
            }

            val allRegionsGroup = DefaultActionGroup.createPopupGroup { message("settings.regions.region_sub_menu") }
            allRegionsGroup.addAll(regionActions)
            topLevelGroup.add(allRegionsGroup)
        } else {
            topLevelGroup.addAll(regionActions)
        }

        val profileActions = createProfileActions()
        val credentialsSettings = credentialsSelectionSettings
        val recentCredentials = accountSettingsManager?.recentlyUsedCredentials()
        if (recentCredentials?.isNotEmpty() == true && credentialsSettings != null) {
            topLevelGroup.add(Separator.create(message("settings.credentials.recent")))
            recentCredentials.forEach {
                topLevelGroup.add(SwitchCredentialsAction(it, it == credentialsSettings.currentSelection, credentialsSettings.onChange))
            }

            val allCredentialsGroup = DefaultActionGroup.createPopupGroup { message("settings.credentials.profile_sub_menu") }
            allCredentialsGroup.addAll(profileActions)
            topLevelGroup.add(allCredentialsGroup)
        } else {
            topLevelGroup.addAll(profileActions)
        }

        return topLevelGroup
    }

    private fun createRegionActions(): List<AnAction> = buildList {
        val (currentSelection, onChange) = regionSelectionSettings ?: return@buildList

        val regionProvider = AwsRegionProvider.getInstance()

        val primaryRegions = currentSelection?.partitionId?.let {
            regionProvider.regions(it).values
        } ?: regionProvider.allRegions().values

        addAll(createRegionGroupActions(primaryRegions, currentSelection, onChange))

        if (currentSelection != null && regionProvider.partitions().size > 1) {
            val otherPartitionActionGroup = DefaultActionGroup.createPopupGroup { message("settings.partitions") }
            val otherPartitions = regionProvider.partitions().values.filterNot { it.id == currentSelection.partitionId }.sortedBy { it.displayName }
            otherPartitions.forEach {
                val partitionGroup = DefaultActionGroup.createPopupGroup { it.displayName }
                partitionGroup.addAll(createRegionGroupActions(it.regions, currentSelection = null, onChange))

                otherPartitionActionGroup.add(partitionGroup)
            }

            add(Separator.create())
            add(otherPartitionActionGroup)
        }
    }

    private fun createRegionGroupActions(regions: Collection<AwsRegion>, currentSelection: AwsRegion?, onChange: (AwsRegion) -> Unit) = buildList<AnAction> {
        regions.groupBy { it.category }
            .forEach { (category, categoryRegions) ->
                add(Separator.create(category))
                categoryRegions.sortedBy { it.displayName }
                    .forEach { add(SwitchRegionAction(it, it == currentSelection, onChange)) }
            }
    }

    private fun createProfileActions(): List<AnAction> = buildList {
        val (currentSelection, onChange) = credentialsSelectionSettings ?: return@buildList

        add(Separator.create(message("settings.credentials")))

        val credentialManager = CredentialManager.getInstance()
        credentialManager.getCredentialIdentifiers().forEach {
            add(SwitchCredentialsAction(it, it == currentSelection, onChange))
        }

        add(Separator.create())
        add(ActionManager.getInstance().getAction("aws.settings.upsertCredentials"))
    }

    // Helper actions, note: these are public to help make tests easier by leveraging instanceOf checks

    abstract inner class DumbAwareToggleAction<T>(
        title: String,
        val value: T,
        private val selected: Boolean,
        private val onSelect: (T) -> Unit
    ) : ToggleAction(title), DumbAware {
        override fun isSelected(e: AnActionEvent): Boolean = selected

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!isSelected(e)) {
                onSelect.invoke(value)
            }
        }
    }

    inner class SwitchRegionAction(
        value: AwsRegion,
        selected: Boolean,
        onSelect: (AwsRegion) -> Unit
    ) : DumbAwareToggleAction<AwsRegion>(value.displayName, value, selected, onSelect)

    inner class SwitchCredentialsAction(
        value: CredentialIdentifier,
        selected: Boolean,
        onSelect: (CredentialIdentifier) -> Unit
    ) : DumbAwareToggleAction<CredentialIdentifier>(value.displayName, value, selected, onSelect)

    companion object {
        private const val ID = "ConnectionSettingsMenu"

        fun connectionSettingsMenuBuilder(): ConnectionSettingsMenuBuilder = ConnectionSettingsMenuBuilder()

        fun projectConnectionSettingsMenu(project: Project, dataContext: DataContext, showRegions: Boolean = true, showCredentials: Boolean = true): ListPopup {
            val accountSettingsManager = AwsConnectionManager.getInstance(project)

            val actionGroup = connectionSettingsMenuBuilder().apply {
                withRecentChoices(project)

                if (showRegions) {
                    withRegions(accountSettingsManager.selectedRegion) { accountSettingsManager.changeRegion(it) }
                }

                if (showCredentials) {
                    withCredentials(accountSettingsManager.selectedCredentialIdentifier) { accountSettingsManager.changeCredentialProvider(it) }
                }
            }.build()

            return JBPopupFactory.getInstance().createActionGroupPopup(
                message("settings.title"),
                actionGroup,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
                ActionPlaces.getActionGroupPopupPlace(ID)
            )
        }
    }
}
