
plugins {
    `project-report`
}


csap.BuildUtils.print_location( "loading ${ javaClass.simpleName }", project.buildFile)

tasks.register("quick") {
    dependsOn ("artifacts-java-services:csap-core-service:quick")
}
tasks.htmlDependencyReport {
//    projects = allprojects
//    output
}

subprojects {
//    csap.BuildUtils.print_two_columns("evaluating", "'$name', '$path'")
    tasks {
        if (  path.contains("artifacts-java-libs:")
            || path.contains("artifacts-java-services:")  ) {
            csap.BuildUtils.debug_print_two_columns("registering depends", "'$name', '$path' ")
            var myDepends = register("depends", DependencyInsightReportTask::class) ;
            myDepends {
                showingAllVariants = true
            }

            var myHtmlReports=register("htmlreport", HtmlDependencyReportTask::class) ;
            myHtmlReports {
                reports.html.outputLocation = file("build/reports/project/dependencies")
            }
        }
    }

}

//tasks.withType(JavaCompile) {
//    options.compilerArgs += ["-nowarn", "-XDenableSunApiLintControl"]
//}