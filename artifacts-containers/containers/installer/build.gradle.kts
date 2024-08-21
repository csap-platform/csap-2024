plugins {
    id( "csap-container-conventions" )
}

//createBuildFolder {
//
//        from "$projectDir/docker", "$projectDir/../../../csap-packages/csap-host-install/build/distributions/csap-host-install-${imageVersion}.zip"
//        into "$buildDir/${dockerBuildContext}"
//
//        filesMatching('**/Dockerfile') {
//            filter {
//                // project.property('myhost')
//                it.replace('IMAGE_VERSION', imageVersion)
//            }
//        }
//
//}

