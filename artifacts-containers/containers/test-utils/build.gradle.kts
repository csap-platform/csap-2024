plugins {
    id( "csap-container-conventions" )
}



//task testArtifacts(type: Copy) {
//
//    into "$buildDir/${dockerBuildContext}/test-utils"
//
//    from( "/Users/peter.nightingale/peters-google-drive/csap/thirdPartyDist/test-utils" ) {
//        include '*.*'
//    }
//
//
//    from( "/Users/peter.nightingale/IdeaProjects/performance-projects/perf-reference-app/build/libs" ) {
//        exclude '*-plain.jar'
//    }
//}
//
//createBuildFolder {
//    dependsOn testArtifacts
//
//    from "$projectDir/docker"
//    into "$buildDir/${dockerBuildContext}"
//
//    filesMatching('**/Dockerfile') {
//        filter {
//            // project.property('myhost')
//            it.replace('IMAGE_VERSION', imageVersion)
//        }
//    }
//
//}

