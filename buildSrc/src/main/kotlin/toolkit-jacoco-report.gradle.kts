import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.kotlin.dsl.support.serviceOf

// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// Taken from https://docs.gradle.org/current/userguide/structuring_software_products.html

plugins {
    id("java-base")
    id("jacoco")
}
// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
jacoco {
    // need to probe resolved dependencies directly if moved to rich version declaration
    toolVersion = versionCatalog.findVersion("jacoco").get().toString()
}

// Configurations to declare dependencies
val aggregateCoverage by configurations.creating {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = false
}

val aggregateCoverageReportResults by configurations.creating {
    isVisible = false
    extendsFrom(aggregateCoverage)
}
// magic to resolve all project dependencies transitively
serviceOf<JvmPluginServices>().configureAsRuntimeClasspath(aggregateCoverageReportResults)


// view to resolve the classes of all dependencies
val classPath = aggregateCoverageReportResults.incoming.artifactView {
    componentFilter { it is ProjectComponentIdentifier }
    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }
}


// view to collect source code
val sourcesPath = aggregateCoverageReportResults.incoming.artifactView {
    withVariantReselection()
    componentFilter { it is ProjectComponentIdentifier }
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.MAIN_SOURCES))
    }
}

// A resolvable configuration to collect JaCoCo coverage data
val coverageDataPath = aggregateCoverageReportResults.incoming.artifactView {
    withVariantReselection()
    componentFilter { it is ProjectComponentIdentifier }
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.JACOCO_RESULTS))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.BINARY_DATA_TYPE)
    }
}

// Register a code coverage report task to generate the aggregated report
tasks.register<JacocoReport>("coverageReport") {
    additionalClassDirs(
        classPath.files.filter { it.isDirectory }.asFileTree.matching {
            include("**/software/aws/toolkits/**")
            exclude("**/software/aws/toolkits/telemetry/**")
        }
    )

    additionalSourceDirs(sourcesPath.files)
    executionData(coverageDataPath.files)

    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}
