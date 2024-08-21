import csap.BuildUtils


pluginManagement {
    includeBuild("artifacts-build")
}
//

plugins {
    //
    // gradle.properties: csap.finder.artifactPrefix, csap.finder.projectPrefix
    //
    id( "csap.plugins.scan-and-include-builds" )
}