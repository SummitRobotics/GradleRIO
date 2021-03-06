package edu.wpi.first.gradlerio.test

import edu.wpi.first.gradlerio.GradleRIOPlugin
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.model.Validate
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.internal.BinarySpecInternal

@CompileStatic
class NativeTestPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // Squash all the libraries into the actual library search path (same directory as
        // executable)
        // This really just protects against dumb zip extractions
        project.tasks.withType(InstallExecutable).all { InstallExecutable ietask ->
            def dest = new File(ietask.getInstallDirectory().get().asFile, "lib")
            ietask.doLast('extractLibsGrio') {
                project.copy { CopySpec copy ->
                    copy.into(dest)
                    copy.from(ietask.libs.files)
                }
            }
        }

        project.tasks.register("simulateExternalCpp", NativeExternalSimulationTask, { NativeExternalSimulationTask task ->
            task.group = "GradleRIO"
            task.description = "Simulate External Task for native executable. Exports a JSON file for use by editors / tools"
        } as Action<NativeExternalSimulationTask>)
    }

    static class NativeTestRules extends RuleSource {
        @Mutate
        @CompileDynamic
        void addBinaryFlags(BinaryContainer binaries) {
            binaries.withType(GoogleTestTestSuiteBinarySpec) { GoogleTestTestSuiteBinarySpec bin ->
                if (!bin.targetPlatform.name.equals('desktop'))
                    bin.buildable = false

                bin.cppCompiler.define('RUNNING_FRC_TESTS')
            }
        }

        @Mutate
        void addSimulationTasks(ModelMap<Task> tasks, ComponentSpecContainer components) {
            components.withType(NativeExecutableSpec).each { NativeExecutableSpec component ->
                component.binaries.withType(NativeExecutableBinarySpec).each { NativeExecutableBinarySpec bin ->
                    if (bin.targetPlatform.operatingSystem.current && !bin.targetPlatform.name.equals('roborio')) {
                        def name = "simulate${((BinarySpecInternal) bin).getProjectScopedName().capitalize()}".toString()
                        tasks.create(name, NativeSimulationTask, { NativeSimulationTask task ->
                            task.group = "GradleRIO"
                            task.description = "Launch simulation for native component ${component.name}"
                            task.binary = bin
                            task.dependsOn(bin.tasks.install)
                        } as Action<NativeSimulationTask>)
                    }
                }
            }
        }

        @Validate
        void populateExternalSimBinaries(ModelMap<Task> tasks, ComponentSpecContainer components, ExtensionContainer extCont) {
            NativeExternalSimulationTask mainTask = (NativeExternalSimulationTask)tasks.get('simulateExternalCpp')
            def project = extCont.getByType(GradleRIOPlugin.ProjectWrapper).project
            components.withType(NativeExecutableSpec).each { NativeExecutableSpec spec ->
                spec.binaries.withType(NativeExecutableBinarySpec).each { NativeExecutableBinarySpec bin ->
                    if (bin.targetPlatform.operatingSystem.current && !bin.targetPlatform.name.equals('roborio')) {
                        mainTask.binaries << bin
                        mainTask.dependsOn bin.tasks.install
                    }
                }
            }
        }
    }
}
