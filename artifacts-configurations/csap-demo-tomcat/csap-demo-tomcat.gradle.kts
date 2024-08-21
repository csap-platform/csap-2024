

plugins {
    `war`
    `maven-publish`
    id("java-conventions")
}

description = "--> tomcat verifaction service"
dependencies {
    providedCompile("javax.servlet:javax.servlet-api:3.1.0")
}

val war by tasks

publishing {
    publications {

        create<MavenPublication>("maven") {
            artifact ( war )
        }

    }
}
