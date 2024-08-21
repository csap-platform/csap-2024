


plugins {
    id( "spring-conventions" )
}


csap.BuildUtils.print_location( "loading ${ javaClass.simpleName }", project.buildFile)

description = "-->  ui and api for storing user and host data into mongo"

dependencies {

    implementation( project(":artifacts-java-libs:csap-starter") )

    implementation("org.mongodb:mongo-java-driver:3.6.1")

    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:3.0.0")
    testImplementation("net.sourceforge.htmlunit:htmlunit")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}








//
//plugins {
//    id 'csap-java-conventions'
//    id 'csap-spring-conventions'
//}
//
//dependencies {
//    implementation project(':artifacts-java-libs:csap-starter')
//    implementation 'org.mongodb:mongo-java-driver:3.6.1'
//
//
//    developmentOnly 'org.springframework.boot:spring-boot-devtools'
//
////    implementation 'org.apache.httpcomponents:httpclient'
//    testImplementation 'de.flapdoodle.embed:de.flapdoodle.embed.mongo:3.0.0'
//    testImplementation 'net.sourceforge.htmlunit:htmlunit'
//}
//
//description = 'csap-events'
