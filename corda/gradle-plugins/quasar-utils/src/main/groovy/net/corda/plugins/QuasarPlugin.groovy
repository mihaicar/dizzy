package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {
    void apply(Project project) {

        project.repositories {
            mavenCentral()
        }

        project.configurations.create("quasar")
//        To add a local .jar dependency:
//        project.dependencies.add("quasar", project.files("${project.rootProject.projectDir}/lib/quasar.jar"))
        project.dependencies.add("quasar", "co.paralleluniverse:quasar-core:${project.rootProject.ext.quasar_version}:jdk8@jar")
        project.dependencies.add("compile", project.configurations.getByName("quasar"))

        project.tasks.withType(Test) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
        project.tasks.withType(JavaExec) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }
}
