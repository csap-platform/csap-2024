import org.gradle.api.tasks.testing.logging.TestExceptionFormat

//
// copy to $HOME/.gradle/init.gradle.kts
//


// initscript {
//     repositories {
//         maven { url "https://plugins.gradle.org/m2" }
//     }
//     dependencies {
// 	    classpath "com.dorongold.plugins:task-tree:3.0.0"
//     }
// }
// rootProject {
//     apply plugin: com.dorongold.gradle.tasktree.TaskTreePlugin
// }

//
// Simple Logging
//

 // allprojects {
 //     plugins.withType<JavaPlugin> {
 //         tasks.withType(Test::class.java) {

 //             //
 //             // Show summary results
 //             //
 //             testLogging {
 //                 showStandardStreams = false
 //                 exceptionFormat = TestExceptionFormat.SHORT
 //                 addTestListener(object : TestListener {
 //                     override fun beforeSuite(suite: TestDescriptor) {}
 //                     override fun beforeTest(testDescriptor: TestDescriptor) {}
 //                     override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
 //                     override fun afterSuite(suite: TestDescriptor, result: TestResult) {
 //                         if (suite.parent == null) {
 //                             System.out.println(
 //                                 "\n${ suite.name }"
 //                                         + "\n\t result:  ${result.resultType}"
 //                                         + "\n\t tests:   ${result.testCount}"
 //                                         + "\n\t passed:  ${result.successfulTestCount}"
 //                                         + "\n\t failed:  ${result.failedTestCount}"
 //                                         + "\n\t skipped: ${result.skippedTestCount}"
 //                             )
 //                         }
 //                     }
 //                 })

 //             }

 //             //
 //             // Force tests to run every time
 //             //
 //             outputs.upToDateWhen {
 //                 false
 //             }

 //         }
 //     }
 // }




//
// NICE Logging
//

initscript {
    repositories {
        //mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.adarshr:gradle-test-logger-plugin:4.0.0")
    }
}

allprojects {
    plugins.withType<JavaPlugin> {
        repositories {

            maven {
                url = uri("https://plugins.gradle.org/m2/")
            }
        }
        // ids NOT supported in init scripts
        //project.plugins.apply ("com.adarshr.test-logger")
        project.plugins.apply (com.adarshr.gradle.testlogger.TestLoggerPlugin::class )


        configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
            setTheme( "plain")
            setLogLevel( "quiet" )
            slowThreshold = 500
            showSummary = true
            showPassed = true
            showSkipped = true
            showFailed = true
            showOnlySlow = false
            showSimpleNames = false

            showStackTraces=true
            showExceptions=true
            showCauses=true

            if (System.getenv("TERM")=="xterm-256color") setTheme("mocha")
        }
        // remove name and apply to both "test" and "integrationTest"
//        project.tasks.named<Test>("test") {
        project.tasks.withType<Test> {
            outputs.upToDateWhen {
                false
            }
        }
    }
}
