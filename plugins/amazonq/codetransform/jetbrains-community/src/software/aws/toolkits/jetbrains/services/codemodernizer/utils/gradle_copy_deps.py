import os
import sys
import subprocess
import shutil
import re
from pathlib import Path

use_offline_dependency = """
final addDownloadedDependenciesRepository(rooted, receiver) {
  receiver.repositories.maven {
    url uri("${rooted.rootDir}/qct-gradle/configuration")
    metadataSources {
      mavenPom()
      artifact()
    }
  }
}
 
settingsEvaluated { settings ->
  addDownloadedDependenciesRepository settings, settings.buildscript
  addDownloadedDependenciesRepository settings, settings.pluginManagement
}
 
allprojects { project ->
  addDownloadedDependenciesRepository project, project.buildscript
  addDownloadedDependenciesRepository project, project
}
"""


run_build_env_copy_content = '''
import java.nio.file.Files
import java.nio.file.StandardCopyOption
 
gradle.rootProject {
    // Task to run buildEnvironment and capture its output
    task runAndParseBuildEnvironment {
        doLast {
            try {
                def buildEnvironmentOutput = new ByteArrayOutputStream()
                exec {
                    // Use the gradlew wrapper from the project's directory
                    commandLine "${project.projectDir}/gradlew", 'buildEnvironment'
                    standardOutput = buildEnvironmentOutput
                }

                def outputString = buildEnvironmentOutput.toString('UTF-8')
                def localM2Dir = new File(System.getProperty("user.home"), ".m2/repository")
                def gradleCacheDir = new File("${project.projectDir}/qct-gradle/START/caches/modules-2/files-2.1")
                def destinationDir = new File("${project.projectDir}/qct-gradle/configuration")

                // Helper method to copy files to m2 format
                def copyToM2 = { File file, String group, String name, String version ->
                    try {
                        def m2Path = "${group.replace('.', '/')}/${name}/${version}"
                        def m2Dir = new File(destinationDir, m2Path)
                        m2Dir.mkdirs()
                        def m2File = new File(m2Dir, file.name)
                        println "this is the m2 path ${m2Path}"
                        Files.copy(file.toPath(), m2File.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } catch (Exception e) {
                        println "Failed to copy file ${file.name} to M2 format: ${e.message}"
                    }
                }

                // Helper method to search and copy artifact in m2 directory
                def searchAndCopyArtifactInM2 = { String group, String name, String version ->
                    try {
                        def m2Path = "${group.replace('.', '/')}/${name}/${version}"
                        def artifactDir = new File(localM2Dir, m2Path)
                        if (artifactDir.exists() && artifactDir.isDirectory()) {
                            println "Found artifact in local m2: ${artifactDir.path}"
                            artifactDir.listFiles().each { file ->
                                try {
                                    println "  Copying File: ${file.name}"
                                    copyToM2(file, group, name, version)
                                } catch (Exception e) {
                                    println "Error copying file ${file.name}: ${e.message}"
                                }
                            }
                            return true
                        }
                    } catch (Exception e) {
                        println "Error searching artifact in local m2: ${e.message}"
                    }
                    return false
                }

                // Helper method to search and copy artifact in Gradle cache directory
                def searchAndCopyArtifactInGradleCache = { String group, String name, String version ->
                    try {
                        def cachePath = "${group}/${name}/${version}"  // Path as is for Gradle cache
                        def artifactDir = new File(gradleCacheDir, cachePath)
                        if (artifactDir.exists() && artifactDir.isDirectory()) {
                            println "Found artifact in Gradle cache: ${artifactDir.path}"
                            artifactDir.listFiles().each { file ->
                                try {
                                    println "  Copying File: ${file.name}"
                                    // Change path to m2 structure
                                    copyToM2(file, group, name, version)
                                } catch (Exception e) {
                                    println "Error copying file ${file.name}: ${e.message}"
                                }
                            }
                            return true
                        }
                    } catch (Exception e) {
                        println "Error searching artifact in Gradle cache: ${e.message}"
                    }
                    return false
                }

                // Helper method to search and copy artifact in local m2 or Gradle cache
                def searchAndCopyArtifact = { String group, String name, String version ->
                    try {
                        if (!searchAndCopyArtifactInM2(group, name, version)) {
                            if (!searchAndCopyArtifactInGradleCache(group, name, version)) {
                                println "Artifact not found: ${group}:${name}:${version}"
                            }
                        }
                    } catch (Exception e) {
                        println "Error searching and copying artifact: ${e.message}"
                    }
                }

                // Parse the buildEnvironment output
                println "=== Parsing buildEnvironment Output ==="
                def pattern = ~/(\S+:\S+:\S+)/
                outputString.eachLine { line ->
                    try {
                        def matcher = pattern.matcher(line)
                        if (matcher.find()) {
                            def artifact = matcher.group(1)
                            def (group, name, version) = artifact.split(':')
                            searchAndCopyArtifact(group, name, version)
                        }
                    } catch (Exception e) {
                        println "Error parsing line: ${line}, ${e.message}"
                    }
                }
            } catch (Exception e) {
                println "Error running buildEnvironment task: ${e.message}"
            }
        }
    }
}
'''

print_contents = '''
 
    import java.nio.file.Files
    import java.nio.file.Path
    import java.nio.file.StandardCopyOption
 
gradle.rootProject {
    task printResolvedDependenciesAndTransformToM2 {
        doLast {
            def destinationDir = new File("${project.projectDir}/qct-gradle/configuration")
    
            // Helper method to copy files to m2 format
            def copyToM2 = { File file, String group, String name, String version ->
                try {
                    def m2Path = "${group.replace('.', '/')}/${name}"
                    def m2Dir = new File(destinationDir, m2Path)
                    m2Dir.mkdirs()
                    def m2File = new File(m2Dir, file.name)
                    Files.copy(file.toPath(), m2File.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (Exception e) {
                    println "Failed to copy file ${file.name} to M2 format: ${e.message}"
                }
            }
    
            // Print buildscript configurations (plugins)
            println "=== Plugins ==="
            buildscript.configurations.each { config ->
                try {
                    if (config.canBeResolved) {
                        println "Configuration: ${config.name}"
                        config.incoming.artifactView { viewConfig ->
                            viewConfig.lenient(true)
                        }.artifacts.each { artifact ->
                            def artifactPath = artifact.file.path
                            if (!artifactPath.startsWith(destinationDir.path)) {
                                try {
                                    println "  Transforming Dependency: ${artifact.id.componentIdentifier.displayName}, File: ${artifact.file}"
                                    def parts = artifact.id.componentIdentifier.displayName.split(':')
                                    if (parts.length == 3) {
                                        def (group, name, version) = parts
                                        copyToM2(artifact.file, group, name, version)
                                    } else {
                                        println "Unexpected format: ${artifact.id.componentIdentifier.displayName}"
                                    }
                                } catch (Exception e) {
                                    println "Error processing artifact ${artifact.file}: ${e.message}"
                                }
                            }
                        }
                        println ""
                    } else {
                        println "Configuration: ${config.name} cannot be resolved."
                        println ""
                    }
                } catch (Exception e) {
                    println "Error processing configuration ${config.name}: ${e.message}"
                }
            }
    
            // Print regular project dependencies
            println "=== Dependencies ==="
            configurations.each { config ->
                try {
                    if (config.canBeResolved) {
                        println "Configuration: ${config.name}"
                        config.incoming.artifactView { viewConfig ->
                            viewConfig.lenient(true)
                        }.artifacts.each { artifact ->
                            def artifactPath = artifact.file.path
                            if (!artifactPath.startsWith(destinationDir.path)) {
                                try {
                                    println "  Transforming Dependency: ${artifact.id.componentIdentifier.displayName}, File: ${artifact.file}"
                                    def (group, name, version) = artifact.id.componentIdentifier.displayName.split(':')
                                    copyToM2(artifact.file, group, name, version)
                                } catch (Exception e) {
                                    println "Error processing artifact ${artifact.file}: ${e.message}"
                                }
                            }
                        }
                        println ""
                    } else {
                        println "Configuration: ${config.name} cannot be resolved."
                        println ""
                    }
                } catch (Exception e) {
                    println "Error processing configuration ${config.name}: ${e.message}"
                }
            }
    
            // Resolve and print plugin marker artifacts
            println "=== Plugin Marker Artifacts ==="
            def pluginMarkerConfiguration = configurations.detachedConfiguration()
    
            // Access plugin dependencies from the buildscript block
            try {
                buildscript.configurations.classpath.resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
                    dependency.children.each { transitiveDependency ->
                        def pluginArtifact = "${transitiveDependency.moduleGroup}:${transitiveDependency.moduleName}:${transitiveDependency.moduleVersion}"
                        pluginMarkerConfiguration.dependencies.add(dependencies.create(pluginArtifact))
                    }
                }
    
                pluginMarkerConfiguration.incoming.artifactView { viewConfig ->
                    viewConfig.lenient(true)
                }.artifacts.each { artifact ->
                    def artifactPath = artifact.file.path
                    if (!artifactPath.startsWith(destinationDir.path)) {
                        try {
                            println "  Transforming Plugin Marker: ${artifact.id.componentIdentifier.displayName}, File: ${artifact.file}"
                            def (group, name, version) = artifact.id.componentIdentifier.displayName.split(':')
                            copyToM2(artifact.file, group, name, version)
                        } catch (Exception e) {
                            println "Error processing plugin marker artifact ${artifact.file}: ${e.message}"
                        }
                    }
                }
            } catch (Exception e) {
                println "Error resolving plugin marker artifacts: ${e.message}"
            }
        }
    }
}
'''

copy_modules_script_content = '''
gradle.rootProject {
    ext.destDir = "$projectDir"
    ext.startDir = "$destDir/qct-gradle/START"
    ext.finalDir = "$destDir/qct-gradle/FINAL"
 
    task buildProject(type: Exec) {
        commandLine "$destDir/gradlew", "build", "-p", destDir, "-g", startDir
    }
 
    task copyModules2 {
        dependsOn buildProject
        doLast {
            def srcDir = file("$startDir/caches/modules-2/files-2.1/")
            def destDir = file("$finalDir/caches/modules-2/files-2.1/")
            
            if (srcDir.exists()) {
                copy {
                    from srcDir
                    into destDir
                }
                println "modules-2/files-2.1 folder copied successfully."
            } else {
                throw new GradleException("Failed to copy the modules-2/files-2.1 folder: source directory does not exist.")
            }
        }
    }
}
 
'''

custom_init_script_content = '''

gradle.rootProject {
    task cacheToMavenLocal(type: Sync) {
        def destinationDirectory = "${project.projectDir}/qct-gradle/configuration"
        println(destinationDirectory)
        
        from new File("${project.projectDir}/qct-gradle/START", "caches/modules-2/files-2.1")
        into destinationDirectory
        
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Choose the strategy that suits your needs
        
        eachFile {
            List<String> parts = it.path.split('/')
            println(parts)
            it.path = [parts[0].replace('.', '/'), parts[1], parts[2], parts[4]].join('/')
        }
        
        includeEmptyDirs false
    }

}
'''

full_download = '''
// This is the init.gradle script

def resolveConfiguration = { config ->
    try {
        config.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            println "  - ${artifact.file.absolutePath}"
        }
        config.resolvedConfiguration.firstLevelModuleDependencies.each { dep ->
            resolveDependencies(dep)
        }
    } catch (Exception e) {
        println "  Failed to resolve configuration: ${config.name}, reason: ${e.message}"
    }
}

def resolveDependencies = { dep ->
    dep.children.each { childDep ->
        childDep.moduleArtifacts.each { artifact ->
            println "  - ${artifact.file.absolutePath}"
        }
        resolveDependencies(childDep)
    }
}

allprojects {
    afterEvaluate { project ->
        project.tasks.create("resolveAllConfigurations") {
            doLast {
                project.configurations.all { config ->
                    println "Resolving configuration: ${config.name}"
                    resolveConfiguration(config)
                }
            }
        }

        project.tasks.create("resolveDetachedConfigurations") {
            doLast {
                def detachedConfigs = project.configurations.findAll { it.name.startsWith('detached') }
                detachedConfigs.each { config ->
                    println "Resolving detached configuration: ${config.name}"
                    resolveConfiguration(config)
                }
            }
        }
    }
}

'''

def run_gradlew(project_dir):
    try:
        subprocess.run(['./gradlew', '-p', project_dir], check=True, cwd=project_dir)
        print("gradlew command executed successfully.")
    except subprocess.CalledProcessError as e:
        print(f"An error occurred while running gradlew: {e}")
        sys.exit(1)

class GradleWrapperPropertiesManager:
    def __init__(self, project_dir):
        self.project_dir = project_dir
        self.properties_file = os.path.join(project_dir, 'gradle', 'wrapper', 'gradle-wrapper.properties')
        self.original_values = {}

    def _read_properties(self):
        properties = {}
        with open(self.properties_file, 'r') as file:
            for line in file:
                if "=" in line:
                    key, value = line.strip().split("=", 1)
                    properties[key] = value
        return properties

    def _write_properties(self, properties):
        with open(self.properties_file, 'w') as file:
            for key, value in properties.items():
                file.write(f"{key}={value}\n")

    def set_custom_distribution(self, distributionBase, distributionPath):
        properties = self._read_properties()
        # Store original values if they exist
        self.original_values['distributionBase'] = properties.get('distributionBase', '')
        self.original_values['distributionPath'] = properties.get('distributionPath', '')

        # Set custom values (create them if they don't exist)
        properties['distributionBase'] = distributionBase
        properties['distributionPath'] = distributionPath
        properties['zipStoreBase'] =distributionBase
        properties['zipStorePath'] = distributionPath

        self._write_properties(properties)
        print(f"Set custom distributionBase={distributionBase} and distributionPath={distributionPath}")

    def reset_distribution(self):
        properties = self._read_properties()
        # Reset to original values
        if 'distributionBase' in self.original_values:
            properties['distributionBase'] = self.original_values.get('distributionBase', '')
        if 'distributionPath' in self.original_values:
            properties['distributionPath'] = self.original_values.get('distributionPath', '')

        self._write_properties(properties)
        print("Reset distributionBase and distributionPath to original values")

    def modify_init_scripts(self, project_dir, distributionPath):
        distribution_path_full = os.path.join(project_dir, distributionPath)
        if not os.path.exists(distribution_path_full):
            print(f"The distribution path {distribution_path_full} does not exist.")
            return

        for root, dirs, files in os.walk(distribution_path_full):
            # Limit the depth to two levels
            if root[len(distribution_path_full):].count(os.sep) < 4:
                print("this is the root when we are walking: " + root)
                print("this is the dirs when we are walking: " + str(dirs))
                if 'init.d' in dirs:
                    init_d_path = os.path.join(root, 'init.d')
                    print(init_d_path)
                    for script_name in os.listdir(init_d_path):
                        print(script_name)
                        if script_name.endswith('.gradle'):
                            script_path = os.path.join(init_d_path, script_name)
                            ScriptModifier(self.project_dir).modify_script(script_path)

    def zip_distribution(self, distributionPath, zip_file_name):
        # Determine the full path to the distribution directory
        distribution_dir = os.path.join(self.project_dir, distributionPath)

        # Make sure the directory exists
        if not os.path.exists(distribution_dir):
            print(f"The distribution directory {distribution_dir} does not exist.")
            return

        print(f"Starting traversal of {distribution_dir}...")

        parent_dir_to_zip = None

        # Traverse the directories under the distribution_dir
        for root, dirs, files in os.walk(distribution_dir):
            print(f"Checking directory: {root}")
            if 'init.d' in dirs and 'bin' in dirs:
                parent_dir_to_zip = root
                print(f"Found 'init.d' and 'bin' under: {root}")
                break  # Stop further traversal once the parent directory is found
        print(root)
        print(parent_dir_to_zip)
        if not parent_dir_to_zip:
            print("Could not find a parent directory containing both 'init.d' and 'bin'.")
            return

        # Define the output zip file path in gradle/wrapper directory
        output_zip = os.path.join(self.project_dir, 'gradle', 'wrapper', zip_file_name)

        # Create a temporary directory to hold the parent directory as a subdir
        temp_dir = os.path.join(self.project_dir, 'temp_zip_dir')
        if os.path.exists(temp_dir):
            shutil.rmtree(temp_dir)  # Remove if it already exists
        os.makedirs(temp_dir)

        # Move the parent directory into the temp directory
        subdir_name = os.path.basename(parent_dir_to_zip)
        shutil.copytree(parent_dir_to_zip, os.path.join(temp_dir, subdir_name))

        # Zip the temp directory, which now contains only the parent directory as a subdir
        shutil.make_archive(output_zip.replace('.zip', ''), 'zip', temp_dir)
        print(f"Zipped distribution directory {parent_dir_to_zip} as subdir to {output_zip}")

        # Clean up the temporary directory
        shutil.rmtree(temp_dir)
        print("Cleaned up temporary directory.")
    def update_distribution_url(self, zip_file_name):
        properties = self._read_properties()
        properties['distributionUrl'] = zip_file_name
        self._write_properties(properties)
        print(f"Updated distributionUrl to point to {zip_file_name}")


class ScriptModifier:
    def __init__(self, project_dir):
        self.project_dir = project_dir

    def modify_script(self, script_path):
        local_path = "${rootProject.rootDir}/qct-gradle/configuration"
        with open(script_path, 'r') as file:
            script_content = file.read()

        # Modify the script content to add new maven{} entries within repos{} block
        modified_content = self.add_maven_repos(script_content, local_path)
        print("this is the modified content")
        with open(script_path, 'w') as file:
            file.write(modified_content)

        print(f"Modified script at {script_path} to use local path {local_path}")

    def add_maven_repos(self, content, local_path):
        new_maven_repo = f"""
        maven {{
            url '{local_path}'
            metadataSources {{
            mavenPom()
            artifact()
            }}
        }}
        """

        # Find the repositories block manually and modify it
        start_idx = content.find('repositories {')
        if start_idx == -1:
            # If no repositories block is found, return the content as is
            return content

        # Find the matching closing brace for the repositories block
        open_braces = 1
        end_idx = start_idx + len('repositories {')
        while open_braces > 0 and end_idx < len(content):
            if content[end_idx] == '{':
                open_braces += 1
            elif content[end_idx] == '}':
                open_braces -= 1
            end_idx += 1

        # Insert the new maven repository before the closing brace
        modified_repositories_block = content[start_idx:end_idx-1].strip() + f"\n{new_maven_repo.strip()}\n" + content[end_idx-1:end_idx]

        # Replace the old block with the modified one
        return content[:start_idx] + modified_repositories_block + content[end_idx:]

def create_init_script(directory, init_name, content):
    qct_gradle_dir = os.path.join(directory, 'qct-gradle')
    os.makedirs(qct_gradle_dir, exist_ok=True)
    file_path = os.path.join(qct_gradle_dir, init_name)
    with open(file_path, 'w') as file:
        file.write(content)
    print(f'init.gradle file created successfully at {file_path}')
    return file_path

def make_gradlew_executable(gradlew_path):
    # check=True causes an Exception to be thrown on non-zero status code
    try:
        subprocess.run(['chmod', '+x', gradlew_path], check=True, text=True, capture_output=True)
        print(f'made gradlew executable at {gradlew_path}')
    except Exception as e:
        print(f'e.stdout = {e.stdout}')
        print(f'e.stderr = {e.stderr}')
        print(f'e.returncode = {e.returncode}')
        print(f'e.args = {e.args}')
        raise # re-throw exception to be caught below

def run_gradle_task(init_script_path, directory_path, task):
    try:
        result = subprocess.run([f"{directory_path}/gradlew", task, '--init-script', init_script_path, '-g', f"{directory_path}/qct-gradle/START", '-p', f"{directory_path}", '--info'], check=True, text=True, capture_output=True)
    except Exception as e:
        print(f'this was this task taht failed: {task}')
        print(f'e.stdout = {e.stdout}')
        print(f'e.stderr = {e.stderr}')
        print(f'e.returncode = {e.returncode}')
        print(f'e.args = {e.args}')
        raise # re-throw exception to be caught below

def run_offline_build(init_script_path, directory_path):
    try:
        result = subprocess.run(
            [f"{directory_path}/gradlew", 'build', '--init-script', init_script_path, '-g', f"{directory_path}/qct-gradle/FINAL", '-p', f"{directory_path}", '--offline'],
            check=True, text=True, capture_output=True
        )
        print("run_offline_build() succeeded:")
        print(result.stdout)
    except Exception as e:
        print(f'e.stdout = {e.stdout}')
        print(f'e.stderr = {e.stderr}')
        print(f'e.returncode = {e.returncode}')
        print(f'e.args = {e.args}')
        raise

def create_run_task(path, init_file_name, content, task_name):
    init_script_path = create_init_script(path, init_file_name, content)
    run_gradle_task(init_script_path, path, task_name)

def run(directory_path):
    gradlew_path = os.path.join(directory_path, 'gradlew')
    if os.path.exists(gradlew_path):
        print("gradlew executable found")
        try:
            make_gradlew_executable(gradlew_path)
        except Exception as e:
            print(f"Error making gradlew executable, going to continue anyway: {e}")
    else:
        # TO-DO: get text approved
        print("gradlew executable not found. Please ensure you have a Gradle wrapper at the root of your project. Run 'gradle wrapper' to generate one.")
        sys.exit(1)
    try:
        create_run_task(directory_path, 'copyModules-init.gradle', copy_modules_script_content, 'copyModules2')
        create_run_task(directory_path, 'custom-init.gradle', custom_init_script_content, 'cacheToMavenLocal')
        create_run_task(directory_path, 'resolved-paths-init.gradle', print_contents, 'printResolvedDependenciesAndTransformToM2')
        create_run_task(directory_path, 'custom-init.gradle', custom_init_script_content, 'cacheToMavenLocal')
        create_run_task(directory_path,'buildEnv-copy-init.gradle', run_build_env_copy_content, 'runAndParseBuildEnvironment')
        build_offline_dependencies = create_init_script(directory_path, 'use-downloaded-dependencies.gradle', use_offline_dependency)
        #run_offline_build(build_offline_dependencies, directory_path)
    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)

def run_properties(dir,action_1, distBase, distPath, zip_name):

    project_directory = dir
    action = action_1

    manager = GradleWrapperPropertiesManager(project_directory)

    if action == 'set':
        distributionBase = distBase
        distributionPath = distPath
        zip_file_name = zip_name

        #modify the properites of the gradle wrapper
        manager.set_custom_distribution(distributionBase, distributionPath)

        #do a gradlew to pull the artifacts to the specified destination
        # TODO: do a gradlew subprocess here before modifying the content of the distribution to pull the zip
        run_gradlew(project_directory)

        #modify the initialization scripts under init.d
        manager.modify_init_scripts(project_directory, distributionPath)

        # Zip the custom distribution
        manager.zip_distribution( distributionPath, zip_file_name)

        # Update the gradle-wrapper.properties file to point to the new zip file
        manager.update_distribution_url(zip_file_name)

        print("DONE")
    elif action == 'reset':
        manager.reset_distribution()
    else:
        print("Unknown action. Use 'set' to set custom distribution or 'reset' to reset to original values.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        # should never happen because script is invoked correctly from toolkit
        print("Usage: python3 copyDepsPythonScript.py <project_path>")
        print(f'Expected 2 arguments but got {len(sys.argv)} arguments: {sys.argv}')
        sys.exit(1) # set return code to non-zero value
    else:
        directory_path = sys.argv[1]
        distBase = 'PROJECT'
        disPath = 'custom-wrapper/dists'
        zipPath = 'customDist.zip'
        run(directory_path)
        run_properties(directory_path, "set", distBase, disPath, zipPath)
