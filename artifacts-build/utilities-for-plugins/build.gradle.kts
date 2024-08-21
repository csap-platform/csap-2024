plugins {
    `kotlin-dsl`
//    `groovy-gradle-plugin`
}

dependencies {
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.14.2")
}

if ( project.hasProperty("blog") )
    println(  "--- loading ${ project.buildFile.parentFile.name } from: \n\t${ project.buildFile.absolutePath }\n"  )



gradlePlugin {
    plugins {
        create("petersPlugin") {
            id = "csap-message"
            implementationClass = "csap.ProgressMessagesPlugin"
        }
    }
}