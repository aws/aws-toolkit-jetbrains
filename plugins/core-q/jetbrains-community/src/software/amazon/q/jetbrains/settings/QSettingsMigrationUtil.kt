// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.settings

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.input.DOMBuilder
import org.w3c.dom.Document
import java.nio.file.Files
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Generic utility for migrating component state from aws.xml to q.xml.
 * Handles all XML parsing internally and returns properly typed state objects.
 */
object QSettingsMigrationUtil {
    private val LOG = Logger.getInstance(QSettingsMigrationUtil::class.java)

    // Mapping of old component names to new component names
    private val COMPONENT_NAME_MAPPINGS = mapOf(
        "aws" to "q",
        "accountSettings" to "qAccountSettings",
        "authManager" to "qAuthManager",
        "connectionManager" to "qConnectionManager",
        "connectionPinningManager" to "qConnectionPinningManager",
        "notificationDismissals" to "qNotificationDismissals",
        "notificationEtag" to "qNotificationEtag"
    )

    /**
     * Migrates state from aws.xml to the specified state class.
     * Uses IntelliJ's XmlSerializer to automatically deserialize the XML into your state object.
     *
     * @param newComponentName The new component name (e.g., "qAccountSettings")
     * @param stateClass The class of your state object
     * @return The migrated state object, or null if no migration data exists
     */
    fun <T : Any> migrateState(newComponentName: String, stateClass: Class<T>): T? {
        val w3cElement = getMigratedComponentElement(newComponentName) ?: return null

        return try {
            val domBuilder = DOMBuilder()
            val jdomElement = domBuilder.build(w3cElement)

            // Use IntelliJ's XmlSerializer to deserialize the element into the state object
            XmlSerializer.deserialize(jdomElement, stateClass)
        } catch (e: Exception) {
            LOG.error("Failed to deserialize migrated state for $newComponentName", e)
            null
        }
    }

    /**
     * Retrieves the component element from aws.xml for the given new component name.
     * Returns a W3C DOM Element.
     */
    private fun getMigratedComponentElement(newComponentName: String): org.w3c.dom.Element? {
        val configPath = PathManager.getConfigPath()
        val oldFile = Paths.get(configPath, "options", "aws.xml")

        if (!Files.exists(oldFile)) {
            LOG.info("No aws.xml file found for migration")
            return null
        }

        try {
            // Find the old component name that maps to this new name
            val oldComponentName = COMPONENT_NAME_MAPPINGS.entries
                .firstOrNull { it.value == newComponentName }
                ?.key

            if (oldComponentName == null) {
                LOG.warn("No mapping found for component: $newComponentName")
                return null
            }

            // Parse the old XML file
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val document: Document = documentBuilder.parse(oldFile.toFile())
            document.documentElement.normalize()

            // Find the component with the old name
            val componentElements = document.getElementsByTagName("component")
            for (i in 0 until componentElements.length) {
                val element = componentElements.item(i) as org.w3c.dom.Element
                val componentName = element.getAttribute("name")

                if (componentName == oldComponentName) {
                    LOG.info("Found migration data for $oldComponentName -> $newComponentName")
                    return element
                }
            }

            LOG.info("No component found with name: $oldComponentName")
            return null
        } catch (e: Exception) {
            LOG.error("Failed to migrate component $newComponentName from aws.xml", e)
            return null
        }
    }
}
