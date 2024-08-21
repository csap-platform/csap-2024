import com.fasterxml.jackson.databind.ObjectMapper
import csap.BuildUtils


csap.BuildUtils.print_location( "loading ${ javaClass.simpleName }", project.buildFile)

description = "-->  host installation package for csap"

plugins {
    id( "java-conventions" )
    id( "package-conventions" )
    `war`
}

group = "${group}.bin"

//
// For build: push binaries to repo
//
val areBinariesAvailable = rootProject.hasProperty("binariesInReport")
if ( areBinariesAvailable ) {
    dependencies {
        implementation("org.csap.bin:maven:3.8.6")
        implementation("org.csap.bin:helm:3.7.0")
        implementation("org.csap.bin:govc:0.21.0")
        implementation("org.csap.bin:java:17.0.6")
        implementation("org.csap.bin:tomcat:9.0.20")
//    implementation "org.codehaus.groovy:groovy-json:3.0.9"

//    instrumentedClasspath(project(path: ":csap-core-service", configuration: "instrumentedJars"))
//    implementation project(":csap-package-linux")
//    implementation project(":csap-core-service")
    }
}

val installerGroup = "installer-group"

var buildFile=layout.buildDirectory.get().asFile ;
//val binDependencies="${ buildFile }/bin-dependencies"
val binDependencies=File(buildFile , "bin-dependencies" )

val configurationFolder="../../artifacts-configurations"
val serviceFolder="../../artifacts-java-services"
val dependencyPaths = sourceSets["main"].runtimeClasspath // sourceSets.main.runtimeClasspath
//configurations.de
val copyLinuxDependencies = tasks.register<Copy>("copyLinuxDependencies")  {
    group = installerGroup
    from (dependencyPaths)
    into (binDependencies)

    doFirst {
//        csap.BuildUtils.print_two_columns ("binDependencies", "${ binDependencies } " )
        var parentFolderDesc = csap.BuildUtils.getPathLevels( binDependencies , 3)
        csap.BuildUtils.print_indent( "Copying \n\t\t ${ dependencyPaths.asPath } \n\t\t to  ${  parentFolderDesc }")
    }
}

//tasks.distZip.dependsOn(copyLinuxDependencies)

val bootServiceMappings = mapOf(
    "csap-core-service"  to "csap-agent.jar",
    "csap-events"        to "events-service.jar",
    "csap-starter-tester" to "csap-verify-service.jar"
)

val zipServiceMappings = mapOf (
    "csap-package-java"   to "csap-package-java.zip",
    "csap-package-linux"  to "csap-package-linux.zip",
    "csap-package-tomcat" to "csap-package-tomcat.zip",
    "csap-package-docker" to "docker.zip",
    "csap-package-httpd"  to "httpd.zip",
    "csap-package-kubelet" to "kubelet.zip",
    "csap-package-podman" to "podman-system-service.zip",
)


//val csapHostBuilder = tasks.register("csapHostBuilder") {
//
////    inputs.file("peter.is.missing")
//    doLast {
//
//        csap.BuildUtils.print_indent ("Creating build manifest: " )
//
//        val versionMappings = jsonMapper.createObjectNode()
//        versionMappings.put("source", project.buildFile.absolutePath)
//
//        bootServiceMappings.forEach { (gitFolderName) ->
//            versionMappings.put(gitFolderName, "$version" )
//        }
//        zipServiceMappings.forEach { (gitFolderName) ->
//            versionMappings.put(gitFolderName, "$version" )
//        }
//
//        val versionReport =  versionMappings.toPrettyString()
//        csap.BuildUtils.print( "versionReport: $versionReport")
//
//        val buildManifestFile =  File(layout.buildDirectory.asFile.get(), "installer-conf.json")
//        buildManifestFile.writeText( versionReport )
//
//    }
//}

val jsonMapper = ObjectMapper()
fun buildManifest() {
    csap.BuildUtils.print_indent ("Creating build manifest" )

    val versionMappings = jsonMapper.createObjectNode()
    versionMappings.put("source", project.buildFile.absolutePath)

    bootServiceMappings.forEach { (gitFolderName) ->
        versionMappings.put(gitFolderName, "$version" )
    }
    zipServiceMappings.forEach { (gitFolderName) ->
        versionMappings.put(gitFolderName, "$version" )
    }

    val versionReport =  versionMappings.toPrettyString()
    var spa=versionReport.replace("\n", "\n\t\t")
    csap.BuildUtils.print_indent( "versionReport: $spa")

    val buildManifestFile =  File(layout.buildDirectory.asFile.get(), "installer-conf.json")
    buildManifestFile.writeText( versionReport )
}

tasks.jar {
    enabled =  false
}
tasks.war {
    enabled = false
}

tasks.distZip {
    dependsOn( copyLinuxDependencies )

    if ( areBinariesAvailable ) {
        enabled = false
    }
//    dependsOn( copyLinuxDependencies, csapHostBuilder )
//    mustRunAfter( "clean")
//    copyLinuxDependencies
//    csapHostBuilder

    doFirst {
        BuildUtils.print_header( "Starting zip ${ project.buildFile.parentFile.name  }" )
        BuildUtils.print_indent(project.buildFile.absolutePath )
        
        buildManifest()
        csap.BuildUtils.print_list( messages , "Items to be copied")
    }
    doLast {
        BuildUtils.print_location( "zip completed", project.buildFile )
    }
}

var messages = mutableListOf<String>()

distributions {
    main {
        contents {
            into("/version/" + project.version) {  // Contents of this directory are
                from ("build.gradle" )
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }


            into("/csap-platform/packages") {   // Contents of this
                from (layout.buildDirectory.asFile.get())
                include ("*json")
            }

            bootServiceMappings.forEach { (gitFolderName, packageName) ->

                evaluationDependsOn(":artifacts-java-services:$gitFolderName")

//                csap.BuildUtils.print_two_columns("registered - copy", "$gitFolderName to $packageName")
                messages.add("copying $gitFolderName to $packageName") ;


                into("/csap-platform/packages") {

                    from (
                        //"$serviceFolder/$gitFolderName/build/libs"
                        project(":artifacts-java-services:$gitFolderName").tasks.getByName( "bootJar" )
                    )
                    rename { packageName }

                    //
                    // NASTY: octal is required, no warnings give on: dirMode = 755
                    //
                    dirMode = "755".toInt( 8 )
                    fileMode = "755".toInt( 8 )

                }
            }

            zipServiceMappings.forEach { (gitFolderName, packageName) ->

                evaluationDependsOn(":artifacts-configurations:$gitFolderName")

//                csap.BuildUtils.print_two_columns("registered - copy", "$gitFolderName to $packageName")
                messages.add("copying $gitFolderName to $packageName") ;

                into("/csap-platform/packages") {

                    from (
                        project(":artifacts-configurations:$gitFolderName").tasks.distZip
                    )

                    rename { packageName }
                    dirMode = "755".toInt( 8 )
                    fileMode = "755".toInt( 8 )

                }
            }

            into("/csap-platform/packages") {
                from (
                    project(":artifacts-configurations:csap-demo-tomcat" ).tasks.war
                )
                rename {  "csap-demo-tomcat.war" }

                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

            into("/csap-platform/bin") {
                from (
                    project(":artifacts-configurations:csap-package-linux").file( "platform-bin" ),
                    project(":artifacts-configurations:csap-package-linux").file( "environment" )
                )
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

            into("/csap-platform/packages/csap-package-linux.secondary") {
                from ( binDependencies )
                include ("*maven*")
                include ("*helm*")
                include ("*govc*")
            }

            into("/csap-platform/packages/csap-package-java.secondary") {
                from ( binDependencies )
                include ( "*java*" )
                rename ( "java", "jdk")
            }

            into("/csap-platform/packages/csap-package-tomcat.secondary") {
                // package contains various versions of tomcat. e.g. tom9
                from ("$configurationFolder/csap-package-tomcat/package" )
            }

            into("/csap-platform/packages/csap-package-tomcat.secondary/tom9") {
                from ( binDependencies )
                include ("*tomcat*" )
            }
        }
    }
}
