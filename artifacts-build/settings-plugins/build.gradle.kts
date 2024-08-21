import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
}

//java {
////    val kotlinSrcDir = "../project-plugins/src/main/kotlin"
//    val kotlinSrcDir = "../project-plugins/common-src/main/kotlin"
//    val mainJavaSourceSet: SourceDirectorySet = sourceSets.main.get().java
//    mainJavaSourceSet.srcDir(kotlinSrcDir)
//
//    println( "\n\n **** mainJavaSourceSet.srcDirs: ${ mainJavaSourceSet.srcDirs }")
//    println( "\n\n **** kotlin sourcedirs: ${ sourceSets["main"].kotlin.srcDirs }")
//}

if ( project.hasProperty("blog") )
    println(  "--- loading ${ project.buildFile.parentFile.name } from: \n\t${ project.buildFile.absolutePath }\n"  )


dependencies {
    implementation( project(":utilities-for-plugins") )
}

gradlePlugin {
    plugins {
        create("findBuilds") {
            id = "csap.plugins.scan-and-include-builds"
            implementationClass = "csap.plugins.ScanAndIncludeSettingsPlugin"
        }
    }
}

//sourceSets.main {
//
//    java.srcDirs.add( file("/Users/peter.nightingale/IdeaProjects/wcsap/artifacts-build/project-plugins/src/main/kotlin"))
//    java.srcDirs.add( file("src"))
//
//    println( "\n\n **** sourceSets99: ${ kotlin.srcDirs }")
//}

//val parentProperties = Properties().apply {
//    load(FileInputStream(File(rootDir, "../gradle.properties")))
//}
//
//
//parentProperties.forEach { entry ->
//    println("${entry.key} : ${entry.value}")
////    if ( entry.key == "version" ) {
////        version = entry.value
////    }
//    when ( entry.key ) {
//        "version" -> version = entry.value
//        "group" -> group = entry.value
//    }
//}
