import csap.BuildUtils


plugins {
    id( "spring-conventions" )
}

csap.BuildUtils.print_location( "loading ${ javaClass.simpleName }", project.buildFile)

description = "-->  ui and api to linux, docker, kubernetes apis"

tasks.processResources {
    from("src/test/java") {
        include(
            "**/*.json",
            "**/*.yaml",
            "**/*.yml"
        )
    }
    filesMatching("**/default-service-definitions.yaml" ) {
        filter{
            it.replace("22.09", version.toString())
        }
    }
}
tasks.bootRun {
    systemProperty ("csapProcessId", "csap-agent" )
}

tasks.register<Test>("quick") {

//    maxParallelForks = 6 ;
    var threads = providers.gradleProperty("threads").orNull ?: "6" ;
    maxParallelForks = threads.toInt()
    doFirst{
        BuildUtils.print_header_always( "Test - maxParallelForks: ${ maxParallelForks }")
    }
    //
    // Force tests to run every time
    //
    outputs.upToDateWhen{
        false
    }
    useJUnitPlatform{
        excludeTags( "containers")
    }
    filter {
        includeTestsMatching( "org.csap.agent.project.*")
        includeTestsMatching( "org.csap.agent.begin.*")
        includeTestsMatching( "org.csap.agent.integration.linux.*")
        includeTestsMatching( "org.csap.agent.integration.misc.*")
        // Adds 10s   includeTestsMatching( "org.csap.agent.integration.services.*")
        includeTestsMatching( "org.csap.agent.ui.*")
    }

}

dependencies {

    implementation( project(":artifacts-java-libs:csap-starter") )

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.commonmark:commonmark:0.18.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.18.1")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.8.1.202007141445-r")

    //
    // Kubernetes
    //
    implementation("io.kubernetes:client-java:14.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.jayway.jsonpath:json-path")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.0.4")


    //
    // Docker 3.2.12
    //
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.0")
    implementation("com.github.docker-java:docker-java:3.3.0") {
        exclude ( "",  "docker-java-transport-jersey" )
        exclude ( "",  "docker-java-transport-netty" )
    }
    implementation("org.springdoc:springdoc-openapi-ui:1.6.4")

    testImplementation("net.sourceforge.htmlunit:htmlunit")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
