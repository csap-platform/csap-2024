//
// @see https://docs.gradle.org/current/userguide/building_java_projects.html
//
import csap.BuildUtils
import csap.CsapMessagePluginExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.File
import java.io.FileInputStream
import java.util.*


plugins {
    `java`
    `jvm-test-suite`
    `maven-publish`
    id ("csap-message")
    id( "global-conventions" )
}

val conventionName = javaClass.simpleName
//val currentName = project.name
val description = BuildUtils.getPathLevels( project.buildFile.parentFile, 1 )
configure<CsapMessagePluginExtension> {
    message.set( "${ description } (${ conventionName }) " )
}

repositories {
    mavenCentral()
}

tasks.test {

    var threads = providers.gradleProperty("threads").orNull ?: "1";
    maxParallelForks = threads.toInt()
    doFirst {
        BuildUtils.print_header_always("Test - maxParallelForks: ${maxParallelForks}")
    }
}

//java {
//    sourceCompatibility = JavaVersion.VERSION_17
//    targetCompatibility = JavaVersion.VERSION_17
//}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.compileJava {
//    options.compilerArgs.add("-Xlint:-deprecation")
    options.isDeprecation = false
}



tasks.withType(Test::class.java) {

    //
    // Show summary results
    //
    testLogging {
        showStandardStreams=false
        exceptionFormat = TestExceptionFormat.SHORT
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null) {
                    System.out.println("\n\t == Test summary =="
                            + "\n\t result:  ${result.resultType}"
                            + "\n\t tests:   ${result.testCount}"
                            + "\n\t passed:  ${result.successfulTestCount}"
                            + "\n\t failed:  ${result.failedTestCount}"
                            + "\n\t skipped: ${result.skippedTestCount}")
                }
            }
        })

    }

    //
    // Force tests to run every time
    //
    outputs.upToDateWhen {
        false
    }
    //
    //   junit test tags
    //
    var excludeTags = System.getProperty( "excludeTags" ) ;
    excludeTags = excludeTags ?: "containers" // assign if not set

    if ( file("/Users/peter.nightingale/IdeaProjects").exists() ) {
        excludeTags = "none"
    }

    csap.BuildUtils.print_two_columns("excluded tags", "'$excludeTags'")

    useJUnitPlatform {

        excludeTags( excludeTags )

    }

    maxHeapSize = "1G"

    // used for desktop socks proxy tests -DsocksProxyHost=localhost -DsocksProxyPort=7199
    if ( excludeTags != "containers" ) {
        csap.BuildUtils.print_header("Adding to unit tests: -DsocksProxyHost=localhost -DsocksProxyPort=7197")
        csap.BuildUtils.print_two_columns ( "note", "ensure ssh tunnel is created on port 7197") ;

        jvmArgs( listOf( "-DsocksProxyHost=localhost", "-DsocksProxyPort=7197") )
//        jvmArgs '-DsocksProxyHost=localhost'
//        jvmArgs '-DsocksProxyPort=7197'
    }

}

