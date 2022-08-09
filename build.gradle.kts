// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog

plugins {
    id("base")
    id("java")
    id("toolkit-changelog")
    id("toolkit-jacoco-report")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

allprojects {
    repositories {
        val codeArtifactUrl: Provider<String> = providers.environmentVariable("CODEARTIFACT_URL")
        val codeArtifactToken: Provider<String> = providers.environmentVariable("CODEARTIFACT_AUTH_TOKEN")
        if (codeArtifactUrl.isPresent && codeArtifactToken.isPresent) {
            maven {
                url = uri(codeArtifactUrl.get())
                credentials {
                    username = "aws"
                    password = codeArtifactToken.get()
                }
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

tasks.register<GenerateGithubChangeLog>("generateChangeLog") {
    changeLogFile.set(project.file("CHANGELOG.md"))
}

tasks.createRelease.configure {
    releaseVersion.set(providers.gradleProperty("toolkitVersion"))
}

dependencies {
    aggregateCoverage(project(":intellij"))
    aggregateCoverage(project(":ui-tests"))
}

tasks.register("runIde") {
    doFirst {
        throw GradleException("Use project specific runIde command, i.e. :jetbrains-core:runIde, :intellij:runIde")
    }
}

if (idea.project != null) { // may be null during script compilation
    idea {
        project {
            settings {
                taskTriggers {
                    afterSync(":sdk-codegen:generateSdks")
                    afterSync(":jetbrains-core:generateTelemetry")
                }
            }
        }
    }
}

fun org.gradle.plugins.ide.idea.model.IdeaProject.settings(configuration: ProjectSettings.() -> Unit) = (this as ExtensionAware).configure(configuration)
fun ProjectSettings.taskTriggers(action: TaskTriggersConfig.() -> Unit, ) = (this as ExtensionAware).extensions.configure("taskTriggers", action)


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
