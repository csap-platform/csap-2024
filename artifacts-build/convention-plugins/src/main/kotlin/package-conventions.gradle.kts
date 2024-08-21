import csap.BuildUtils
import csap.CsapMessagePluginExtension

plugins {
    distribution
    `maven-publish`
    id ("csap-message")
}
//defaultTasks 'distZip'

val conventionName = javaClass.simpleName
//val currentName = project.name
val description = BuildUtils.getPathLevels( project.buildFile.parentFile, 1 )
configure<CsapMessagePluginExtension> {
    message.set( "${ description } (${ conventionName }) " )
}

val distZip by tasks
distZip.enabled = true
val distTar by tasks
distTar.enabled = false

publishing {
    publications {

        create<MavenPublication>("maven") {
            artifact ( distZip )
        }

    }
}

distributions {

    main {

        distributionBaseName.set( project.name )
        contents {

            into("/scripts") {
                from ("scripts")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

            into("/configuration") {
                from ("configuration")
                from ("environment")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }


            into("/") {
                from ("csap-api.sh")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

            into("/version/"  + project.version ) {
                from ("build.gradle.kts")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

        }
    }
}
//distributions {
//
////    println ( " running dist" )
////    print_section( "hi" )
//
////    println("\n\n\n adding distribution ${project}")
////
//
////    doLast {
////        println("\n\n\n completed ${project}")
////    }
//
//}