// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import software.aws.toolkits.jetbrains.services.cloudformation.IndexedFunction
import software.aws.toolkits.jetbrains.services.cloudformation.IndexedResource
import software.aws.toolkits.jetbrains.services.cloudformation.yaml.YamlCloudFormationTemplate
import java.io.DataInput
import java.io.DataOutput

class SamTemplateIndex : FileBasedIndexExtension<String, List<IndexedResource>>() {
    private val fileFilter by lazy {
        val supportedFiles = arrayOf(YAMLLanguage.INSTANCE.associatedFileType)

        object : DefaultFileTypeSpecificInputFilter(*supportedFiles) {
            override fun acceptInput(file: VirtualFile): Boolean = file.isInLocalFileSystem
        }
    }

    override fun getValueExternalizer(): DataExternalizer<List<IndexedResource>> = object : DataExternalizer<List<IndexedResource>> {
        override fun save(dataOutput: DataOutput, value: List<IndexedResource>) {
            dataOutput.writeInt(value.size)
            value.forEach { resource ->
                dataOutput.writeUTF(resource.logicalName)
                dataOutput.writeUTF(resource.type)
                dataOutput.writeInt(resource.indexedProperties.size)
                resource.indexedProperties.forEach { key, value ->
                    dataOutput.writeUTF(key)
                    dataOutput.writeUTF(value)
                }
            }
        }

        override fun read(dataInput: DataInput): List<IndexedResource> {
            val size = dataInput.readInt()
            val resources = mutableListOf<IndexedResource>()
            repeat(size) {
                val logicalName = dataInput.readUTF()
                val type = dataInput.readUTF()
                val mutableMap: MutableMap<String, String> = mutableMapOf()

                val propertySize = dataInput.readInt()
                repeat(propertySize) {
                    val key = dataInput.readUTF()
                    val value = dataInput.readUTF()
                    mutableMap[key] = value
                }
                resources.add(IndexedResource.from(logicalName, type, mutableMap))
            }
            return resources
        }
    }

    override fun getName(): ID<String, List<IndexedResource>> = NAME

    override fun getIndexer(): DataIndexer<String, List<IndexedResource>, FileContent> = DataIndexer { fileContent ->
        val indexedResources = mutableMapOf<String, List<IndexedResource>>()

        fileContent.psiFile.acceptNode(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                super.visitElement(element)
                element?.run {
                    val parent = element.parent as? YAMLKeyValue ?: return
                    if (parent.value != this) return

                    val indexedResource = YamlCloudFormationTemplate.convertPsiToResource(parent)?.let {
                        IndexedResource.fromResource(it)
                    } ?: return

                    (indexedResources.computeIfAbsent(indexedResource.type) { mutableListOf() } as MutableList)
                            .add(indexedResource)
                }
            }
        })

        indexedResources
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter = fileFilter

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        private val NAME: ID<String, List<IndexedResource>> = ID.create("SamTemplateIndex")

        private fun PsiElement.acceptNode(visitor: PsiElementVisitor) {
            accept(visitor)

            if (children.isNotEmpty()) {
                children.forEach { it.acceptNode(visitor) }
            }
        }

        private fun listAllResources(project: Project): Collection<IndexedResource> {
            val index = FileBasedIndex.getInstance()
            return index.getAllKeys(NAME, project)
                    .asSequence()
                    .mapNotNull { index.getValues(NAME, it, GlobalSearchScope.projectScope(project)) }
                    .filter { it.isNotEmpty() }
                    .flatten()
                    .flatten()
                    .toList()
        }

        fun listResourcesByType(project: Project, type: String): Collection<IndexedResource> = listAllResources(project).filter { it.type == type }

        fun listFunctions(project: Project): Collection<IndexedFunction> = listAllResources(project).filterIsInstance(IndexedFunction::class.java)
    }
}