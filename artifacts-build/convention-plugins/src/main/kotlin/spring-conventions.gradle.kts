plugins {
    id( "java-conventions" )
    id ("io.spring.dependency-management")
    id ("org.springframework.boot")
}


configurations.implementation {
    exclude("", "spring-boot-starter-logging")
    exclude("ch.qos.logback", "")

}

// Skip building the plain jar
tasks.named("jar") {
    enabled = false
}

sourceSets.create("csapMulti") {
    resources.srcDirs("src/main/resources", "../../artifacts-java-libs/csap-starter/src/main/resources" )
}


tasks.bootRun {
//    sourceResources sourceSets.main
    sourceResources( sourceSets.getByName("csapMulti"))
    csap.BuildUtils.print_two_columns( "deleting", file("logs2").absolutePath )
    project.delete( file("logs") )
}

publishing {
    publications {

        create<MavenPublication>("maven") {
            artifact ( tasks.bootJar )
            pom {
                name.set( "${project.name}")
                description.set("csap component using gradle kotlin")
                url.set("https://github.com/csap-platform/csap-core/wiki")

            }
        }

    }
}

//ext['spring-security.version'] = '5.8.1'
//ext['activemq.version'] = '5.16.3'

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation ("org.springframework.boot:spring-boot-starter-web")
    implementation ("org.springframework.boot:spring-boot-starter-log4j2")
    implementation ("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation ("org.springframework.boot:spring-boot-starter-aop")

    implementation ("org.springframework.boot:spring-boot-starter-security")
    implementation ("org.springframework.security:spring-security-ldap")
    implementation ("org.springframework.boot:spring-boot-starter-cache")


    implementation ("org.springframework.boot:spring-boot-starter-webflux") {
        exclude ( "org.springframework.boot",  "spring-boot-starter" )
    }

    implementation ( "org.springframework.security:spring-security-oauth2-client" )
    implementation ( "org.springframework.security:spring-security-oauth2-jose" )
    implementation ( "org.springframework.security:spring-security-oauth2-resource-server" )

    implementation ( "org.springframework.boot:spring-boot-starter-thymeleaf" )
    implementation ( "org.thymeleaf.extras:thymeleaf-extras-springsecurity6" )

//    implementation ( "org.apache.httpcomponents:httpclient"
    implementation ( "org.apache.httpcomponents.client5:httpclient5" )
    implementation ( "org.springframework.boot:spring-boot-starter-actuator" )

    //
    // General use
    //

    implementation ( "org.springframework.boot:spring-boot-starter-mail" ) // 2.7.5
    implementation ( "org.springframework.retry:spring-retry" ) //:1.3.2

    implementation ( "org.apache.commons:commons-dbcp2"  )// :2.9.0
    implementation ( "io.micrometer:micrometer-core" )
    implementation ( "io.micrometer:micrometer-registry-prometheus"  )// 1.8.4
    implementation ( "org.ehcache:ehcache::jakarta"  )// :3.9.9
//    implementation ( "org.ehcache:ehcache:3.10.8:jakarta"
    implementation ( "org.apache.commons:commons-lang3"  )// 3.4

    // Standards
    implementation ( "javax.cache:cache-api"  )// 1.1.1
    implementation ( "jakarta.inject:jakarta.inject-api:2.0.1" )


    // Version Managed
    implementation ( "mysql:mysql-connector-java:8.0.28" )
//    implementation ( "org.glassfish.jaxb:jaxb-runtime:2.3.6" )
    implementation ( "org.jasypt:jasypt:1.9.2" )
    implementation ( "org.jasypt:jasypt-spring31:1.9.2" )
    implementation ( "commons-io:commons-io:2.11.0" )
    implementation ( "net.sf.jopt-simple:jopt-simple:5.0.3" )

    //
    //  UI webjar versions
    //
    implementation ( "org.webjars:requirejs:2.1.20" )
    implementation ( "org.webjars.npm:plotly.js-dist-min:2.9.0" )
    implementation ( "org.webjars:jquery:3.6.4" )
    implementation ( "org.webjars:jquery-ui:1.13.2" )
    implementation ( "org.webjars:alertifyjs:1.13.1" )
    implementation ( "org.webjars.npm:papaparse:5.3.2" )
    implementation ( "org.webjars.bower:jQuery-contextMenu:2.8.0" )
    implementation ( "org.webjars:jquery-form:4.2.2" )
    implementation ( "org.webjars:tablesorter:2.31.3" )
    implementation ( "org.webjars.npm:flot:4.2.2" )
    implementation ( "org.webjars.npm:mathjs:9.4.4" )
    implementation ( "org.webjars:jqplot:1.0.8r1250" )
//    implementation ( "org.webjars.bower:fancytree:2.30.0"
//    https://github.com/mar10/fancytree/blob/master/CHANGELOG.md
    implementation ( "org.webjars.npm:jquery.fancytree:2.38.2" )
//    implementation ( "org.webjars:datatables:1.10.21"
    implementation ( "org.webjars:datatables:1.13.2" )
    implementation ( "org.webjars.npm:ace-builds:1.4.11" )
    implementation ( "org.webjars.npm:js-yaml:4.1.0" )

    annotationProcessor ( "org.springframework.boot:spring-boot-configuration-processor" )

    // resolve gradle spring plugin warnings
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation ( "org.springframework.boot:spring-boot-starter-test" )
    testImplementation ( "org.springframework.security:spring-security-test" )

    implementation ( "org.junit.platform:junit-platform-suite" )

}



tasks.register("spring-boot-properties") {
//    group = 'Introspection'
//    description = 'Print properties from all BOMs'
    doLast {

        csap.BuildUtils.print_section( "Getting Spring Properties" )


        csap.BuildUtils.print("\n\n bootProperties: \n\n no available in kotlin")


    }
}