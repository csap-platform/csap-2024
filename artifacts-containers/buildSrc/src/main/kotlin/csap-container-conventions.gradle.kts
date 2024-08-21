import com.bmuschko.gradle.docker.tasks.image.*

plugins {
    id("com.bmuschko.docker-remote-api")
    id ("base")
}

// https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/


docker {
    url.set("unix:///var/run/docker.sock")

    registryCredentials {
        url.set("https://docker-dev-artifactory.yourcompany.com")
        username.set( providers.gradleProperty("artifactory_user").orNull ?: "missing-user-in-properties" )
        password.set(providers.gradleProperty("artifactory_password").orNull ?: "missing-user-in-properties")
    }

}


val versionImageName = "${ project.property("imageGroup") }/${ project.name }:${ project.property("imageVersion") }"
val latestImageName = "${ project.property("imageGroup") }/${ project.name }:latest"
val dockerBuildContext = project.property("dockerBuildContext")
val buildxRepository =  project.property("buildxRepository")


tasks.create("buildImage", DockerBuildImage::class) {
    dependsOn("createBuildFolder")

    inputDir.set(file("$buildDir/${ project.property("dockerBuildContext")  }"))
    images.add(versionImageName)
    images.add(latestImageName)
}

tasks.register<Copy>("createBuildFolder")  {
    from ("$projectDir/docker")
    into ("$buildDir/${ project.property("dockerBuildContext")  }")
}

tasks.register<Exec>("buildx") {
    dependsOn("createBuildFolder")

    group = "docker"
    workingDir = file("$buildDir/${ dockerBuildContext }" )
    executable = "/usr/local/bin/docker"

    setArgs( listOf( "buildx", "build",
        "--platform", "linux/amd64,linux/arm64",
        "-t", "$buildxRepository/$versionImageName",
        "-t", "$buildxRepository/$latestImageName",
        "--push", ".") )

}




//plugins {
//    id 'com.bmuschko.docker-remote-api'
//    id 'base'
//}
//
////defaultTasks 'buildImage'
//defaultTasks 'buildx'
//
//docker {
//    url = 'unix:///var/run/docker.sock'
//
//    registryCredentials {
////        url = 'https://index.docker.io/v1/'
//
//        //
//        url = 'https://docker-dev-artifactory.yourcompany.com'
//        username = "${ artifactory_user }"
//        password = "${ artifactory_password }"
//
//    }
//
//}
//
//ext {
//    versionImageName = "${ imageGroup }/${ project.name }:${ imageVersion }"
//    latestImageName = "${ imageGroup }/${ project.name }:latest"
//}
//
//
//import com.bmuschko.gradle.docker.tasks.image.*
//
//task createBuildFolder(type: Copy) {
//    from "$projectDir/docker"
//    into "$buildDir/${ dockerBuildContext }"
//}
//
//
//task buildImage(type: DockerBuildImage ) {
//    dependsOn createBuildFolder
////    dependsOn tasks.withType(Copy)
//    inputDir = file("$buildDir/dockerBuildContext")
//    images.add( versionImageName )
//    images.add( latestImageName )
//}
//
////task buildImage(type: DockerBuildImage) {
////    inputDir = file('docker')
////    images.add( versionImageName )
////    images.add( latestImageName )
////}
//
//task removeVersionImage(type: DockerRemoveImage) {
////    dependsOn buildMyAppImage
//    force = true
//    targetImageId versionImageName
//}
//task removeLatestImage(type: DockerRemoveImage) {
////    dependsOn buildMyAppImage
//    force = true
//    targetImageId latestImageName
//}
//
////tasks.register('clean') {
////    dependsOn removeVersionImage
////    dependsOn  removeLatestImage
////    doLast {
////        println 'Default Cleaning!'
////    }
////}
//
//task pushImage(type: DockerPushImage) {
//    dependsOn buildImage
//    images = buildImage.images
//
//}
//
//task buildx(type:Exec) {
//    dependsOn createBuildFolder
//    group 'docker'
//    workingDir "$buildDir/${ dockerBuildContext }"
//    executable '/usr/local/bin/docker'
//    args = ['buildx', 'build', '--platform', 'linux/amd64,linux/arm64', '-t', "$versionImageName", '-t', "$latestImageName", '--push', '.']
//}
