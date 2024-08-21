package csap

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create


interface CsapMessagePluginExtension {
    val message: Property<String>
    val greeter: Property<String>
}

class ProgressMessagesPlugin  : Plugin<Project> {
    override fun apply(project: Project) {

        val extension = project.extensions.create<CsapMessagePluginExtension>("greeting")
        extension.message.convention("processed")

//        val zipTask: TaskProvider<Task> = project.tasks.named("distZip")
        val zipTask = project.tasks.findByName("distZip")
        if ( zipTask != null ) {
            zipTask.apply {
                zipTask.doLast {
                    BuildUtils.print_indent_debug( "zipped ${ extension.message.get() }" )
                }
            }
        }

        val publishTask = project.tasks.findByName("publish")
        if ( publishTask != null ) {
            publishTask.apply {
                publishTask.doFirst {
                    BuildUtils.print_indent_debug( "publishing ${ extension.message.get() }" )
                }
            }
        }

        val jarTask = project.tasks.findByName("jar")
        if ( jarTask != null ) {
            jarTask.apply {
                jarTask.doFirst {
                    BuildUtils.print_indent_debug( "jarring ${ extension.message.get() }" )
                }
            }
        }

//        val assembleTask: TaskProvider<Task> = project.tasks.named("assemble")
//        assembleTask.isPresent.apply {
//            assembleTask.get().doFirst {
//                BuildUtils.print_section("Starting ${assembleTask.name} :  ${extension.message.get()}")
//            }
//            assembleTask.get().doLast {
//                BuildUtils.print_section("Completed ${assembleTask.name} :  ${extension.message.get()}")
//            }
//        }
        val assembleTask = project.tasks.findByName("assemble")
        if ( assembleTask != null ) {
            assembleTask.apply {
                assembleTask.doFirst {
                    BuildUtils.print_header_debug("Starting ${assembleTask.name} :  ${extension.message.get()}")
                }
                assembleTask.doLast {
                    BuildUtils.print_indent_debug("Completed ${assembleTask.name} :  ${extension.message.get()}")
                }
            }
        }

        val buildTask = project.tasks.findByName("build")
        if ( buildTask != null ) {
            buildTask.apply {
                buildTask.doFirst {
                    BuildUtils.print_header_debug("Starting ${buildTask.name} :  ${extension.message.get()}")
                }
                buildTask.doLast {
                    BuildUtils.print_indent_debug("Completed ${buildTask.name} :  ${extension.message.get()}")
                }
            }
        }


        val cleanTask = project.tasks.findByName("clean")
        if ( cleanTask != null ) {
            cleanTask.apply {
                cleanTask.doFirst {
                    BuildUtils.print_header_debug("Starting ${cleanTask.name} :  ${extension.message.get()}")
                }
                cleanTask.doLast {
                    BuildUtils.print_indent_debug("Completed ${cleanTask.name} :  ${extension.message.get()}")
                }
            }
        }

    }
}