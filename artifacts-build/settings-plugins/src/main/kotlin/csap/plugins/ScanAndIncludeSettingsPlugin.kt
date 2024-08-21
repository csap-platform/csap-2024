package csap.plugins

import csap.BuildUtils
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class ScanAndIncludeSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {

        BuildUtils.myLevel = settings.providers.gradleProperty("blog").orNull ?: "off"

        BuildUtils.print_header( "${ this.javaClass.name }")

        //
        //  Configuration: Plugin Extensions cannot be used (yet). Use gradleProperties
        //
        val artifactPrefixProvider = settings.providers.gradleProperty("csap.finder.artifactPrefix")
        val projectPrefixProvider = settings.providers.gradleProperty("csap.finder.projectPrefix")
        val projectSuffixProvider = settings.providers.gradleProperty("csap.finder.extensionScanner")

        val artifactPrefix = artifactPrefixProvider.orNull ?: "artifact"
        val projectPrefix = projectPrefixProvider.orNull ?: "csap"


//        BuildUtils.print_two_columns("artifactPrefix", artifactPrefix )
//        BuildUtils.print_two_columns("projectPrefix", projectPrefix )

        val extensionUsed = projectSuffixProvider.orNull ?: ".gradle.kts"

        BuildUtils.print_header( "Scanning for projects using <parent-folder-name>${ extensionUsed }")
//        var projects_to_build = csap.BuildUtils.findProjectFoldersUsingPrefix( settings.rootDir, artifactPrefix, projectPrefix )

        var projects_to_build = csap.BuildUtils.findProjectsUsingExtension( settings.rootDir, extensionUsed ) ;

        csap.BuildUtils.print_list( projects_to_build, "projects found" )

        settings.include(
            projects_to_build
        )

        for (projectPath in projects_to_build) {
//            BuildUtils.print_two_columns("projectPath", projectPath)
            var project = settings.findProject( projectPath )
            if ( project != null ) {
                project.buildFileName = project.buildFile.parentFile.name + extensionUsed
                BuildUtils.debug_print_two_columns("child", project.buildFile)

                require(project.projectDir.isDirectory) {
                    "Project directory ${project.projectDir} for project ${project.name} does not exist."
                }
                require(project.buildFile.isFile) {
                    "Build file ${project.buildFile} for project ${project.name} does not exist."
                }
            } else {
                BuildUtils.debug_print_two_columns("projectPath", "nofound")
            }
        }




    }
}