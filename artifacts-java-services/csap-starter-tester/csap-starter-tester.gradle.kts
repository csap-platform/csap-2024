
csap.BuildUtils.print_location( "loading ${ javaClass.simpleName }", project.buildFile)

plugins {
    id( "spring-conventions" )
}


dependencies {

    implementation( project(":artifacts-java-libs:csap-starter") )

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")


    implementation("org.postgresql:postgresql")
    implementation("org.apache.commons:commons-dbcp2")
    implementation("org.apache.httpcomponents:httpclient")
    implementation("org.springframework.boot:spring-boot-starter-jersey")
    implementation("org.springframework:spring-jms")
    implementation("org.apache.activemq:artemis-jakarta-server")

//    implementation("org.apache.activemq:activemq-client")
    implementation("org.hsqldb:hsqldb")



    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
tasks.bootRun {
    systemProperty ("csapTestInitAllocGb", "1" )
    systemProperty ("csapTestAllocMetaspaceCount", "2" )
    systemProperty ("csapTestAllocKb", "3" )
    systemProperty ("csapTestAllocCountK", "5" )
    systemProperty ("csapTestAllocJsonObjects", "true" )
}
