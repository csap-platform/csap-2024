
plugins {
    `kotlin-dsl`
    `groovy-gradle-plugin`
}

if ( project.hasProperty("blog") )
    println(  "--- loading ${ project.buildFile.parentFile.name } from: \n\t${ project.buildFile.absolutePath }\n"  )


dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.0.4")
    implementation("com.adarshr:gradle-test-logger-plugin:3.2.0")
    implementation("com.dorongold.plugins:task-tree:2.1.1")
    implementation( project(":utilities-for-plugins") )

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
    implementation("ru.vyarus:gradle-use-python-plugin:3.0.0")
}
// println("got here")

//gradlePlugin {
//    plugins {
//        create("petersPlugin") {
//            id = "csap-message"
//            implementationClass = "csap.CsapMessagePlugin"
//        }
//    }
//}
//BuildUtils.print_indent("hi") ;
