import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("toolkit-intellij-plugin")

    id("org.jetbrains.intellij.platform")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

val testPlugins by configurations.registering

dependencies {
    testImplementation(platform("com.jetbrains.intellij.tools:ide-starter-squashed"))
    // should really be set by the BOM, but too much work to figure out right now
    testImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    intellijPlatform {
        intellijIdeaCommunity(IdeVersions.ideProfile(providers).map { it.name })

        testFramework(TestFrameworkType.Starter)
    }

    testPlugins(project(":plugin-amazonq", "pluginZip"))
    testPlugins(project(":plugin-core", "pluginZip"))
}

tasks.test {
    dependsOn(testPlugins)

    useJUnitPlatform()

    systemProperty("ui.test.plugins", testPlugins.map { it.asPath })
}
