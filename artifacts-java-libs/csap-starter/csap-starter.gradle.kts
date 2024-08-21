plugins {
    id( "spring-conventions" )
    id( "java-library-conventions" )
//    `java`
//    `maven-publish`
}

description = "-->  spring boot bom and common lib"
dependencies {
    implementation("net.sf.jopt-simple:jopt-simple:5.0.3")
    implementation("org.apache.commons:commons-csv:1.10.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

//
// default in csap-spring is disabled, renable
//
tasks.named("jar") {
    enabled = true
}

tasks.named("bootJar") {
    enabled = true
}


publishing {
    publications {

        create<MavenPublication>("mavenBootPlainJar") {
            artifact ( tasks.jar )  // bootJar will generate an executable jar
            pom {
                name.set( "${project.name}")
                description.set("csap component using gradle kotlin")
                url.set("https://github.com/csap-platform/csap-core/wiki")

            }
        }

    }
}
