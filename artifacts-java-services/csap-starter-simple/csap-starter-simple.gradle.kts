
plugins {
    id( "spring-conventions" )
}

csap.BuildUtils.print_location( "loading ${ javaClass.simpleName }", project.buildFile)

dependencies {

    implementation( project(":artifacts-java-libs:csap-starter") )

    implementation("org.springdoc:springdoc-openapi-ui:1.6.4")

    testImplementation("net.sourceforge.htmlunit:htmlunit")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
}



