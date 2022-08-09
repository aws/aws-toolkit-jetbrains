// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("org.jetbrains.intellij")
    id("toolkit-testing") // Needed so the coverage configurations are present
    id("toolkit-detekt")
}

val ideProfile = IdeVersions.ideProfile(project)

val toolkitVersion: String by project
val publishToken: String by project
val publishChannel: String by project

// please check changelog generation logic if this format is changed
version = "$toolkitVersion-${ideProfile.shortName}"

val resharperDlls = configurations.create("resharperDlls") {
    isCanBeConsumed = false
}

intellij {
    pluginName.set("aws-toolkit-jetbrains")

    version.set(ideProfile.community.version())
    localPath.set(ideProfile.community.localPath())

    updateSinceUntilBuild.set(false)
    instrumentCode.set(false)
}

tasks.prepareSandbox {
    from(resharperDlls) {
        into("aws-toolkit-jetbrains/dotnet")
    }
}

tasks.publishPlugin {
    token.set(publishToken)
    channels.set(publishChannel.split(",").map { it.trim() })
}

tasks.check {
    dependsOn(tasks.verifyPlugin)
}

// We have no source in this project, so skip test task
tasks.test {
    enabled = false
}

dependencies {
    implementation(project(":jetbrains-core"))
    implementation(project(":jetbrains-ultimate"))
    project.findProject(":jetbrains-rider")?.let {
        implementation(it)
        resharperDlls(project(":jetbrains-rider", configuration = "resharperDlls"))
    }
}

configurations {
    // Make sure we exclude stuff we either A) ships with IDE, B) we don't use to cut down on size
    runtimeClasspath {
        exclude(group = "org.slf4j")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
}


val artifactType = Attribute.of("artifactType", String::class.java)
val minified = Attribute.of("minified", Boolean::class.javaObjectType)

dependencies {
    attributesSchema {
        attribute(minified)
    }
    artifactTypes.getByName("jar") {
        attributes.attribute(minified, false)
    }
}

configurations.compileClasspath {
    afterEvaluate {
        if (isCanBeResolved) {
            attributes.attribute(minified, true)
        }
    }
}

configurations.runtimeClasspath {
    afterEvaluate {
        if (isCanBeResolved) {
            attributes.attribute(minified, true)
        }
    }
}

dependencies {
    registerTransform(Minify::class) {
        from.attribute(minified, false).attribute(artifactType, "jar")
        to.attribute(minified, true).attribute(artifactType, "jar")
    }
}

@CacheableTransform
abstract class Minify @Inject constructor(): TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    @get:Classpath
    @get:InputArtifactDependencies
    abstract val runtimeClasspath: FileCollection

    override
    fun transform(outputs: TransformOutputs) {
        val file = inputArtifact.get().asFile
        val fileName = file.nameWithoutExtension
        if (file.absolutePath.contains("software.amazon.awssdk")) {
            minify(file, outputs.file("$fileName-min.jar"))
            return
        }

        if (file.exists()) {
            // maybe something modeled incorrectly, but fails because toolkit-core jars arent in build/libs yet until buildSearchableOptions
            // but the transform runs before that
            // we only want proguard to run on the AWS SDK anyways so this should be fine
            outputs.file(inputArtifact)
        }
    }

    private fun minify(artifact: File, jarFile: File) {
        println("Minifying ${artifact.name} -> $jarFile")
        val pgc = proguard.Configuration()

        val libJars = runtimeClasspath.asFileTree.joinToString(separator = File.pathSeparator)
        val args = arrayOf(
            "-injars ${artifact}",
            "-outjars ${jarFile}",
            "-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)",
            "-libraryjars <java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)",
        ) + let {
            if (libJars.isNotEmpty()) {
                arrayOf("-libraryjars $libJars")
            } else {
                emptyArray()
            }
        } + arrayOf(
            "-dontobfuscate",
            "-dontwarn org.slf4j.**,javax.**",
            "-dontwarn software.amazon.awssdk.crt.**",
            "-keepattributes *",
            // globally: drop async and ec2
            // drop async paginators and async classes
            "-keep class !**.paginators.*Publisher*,!**.*Async*,!**.services.ec2.** { *; }",
            // drop async interfaces
            "-keep interface !**.*Async*,!**.services.ec2.** { *; }",

            // ec2
            "-keep class software.amazon.awssdk.services.ec2.model.Ec2* { *; }",
            "-keep class software.amazon.awssdk.services.ec2.model.DescribeInstances* { *; }",
            "-keep class software.amazon.awssdk.services.ec2.model.Reservation* { *; }",
            "-keep class software.amazon.awssdk.services.ec2.model.Instance* { *; }",
            "-keep class software.amazon.awssdk.services.ec2.model.IamInstanceProfile* { *; }",
            """
                -keep interface software.amazon.awssdk.services.ec2.Ec2Client* {
                    <init>(...);
                    <fields>;
                    *** describeInstances*(...);
                }
            """.trimIndent(),
            """
                -keep class * implements software.amazon.awssdk.services.ec2.Ec2Client* {
                    <init>(...);
                    <fields>;
                    *** describeInstances*(...);
                }
            """.trimIndent()
        )
        proguard.ConfigurationParser(args, System.getProperties()).parse(pgc)
        val pg = proguard.ProGuard(pgc)
        pg.execute()
    }
}
