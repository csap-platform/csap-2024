

var theProjectFolder = file( rootDir ) ;
var taskPrefix=""
//print_section()

csap.BuildUtils.print_section( project.buildFile.absolutePath  )

var buildFolders = mutableListOf<String>()

if ( gradle.extra.has( "buildFoldersChild") ) {

    //
    //  composite build
    //
    theProjectFolder = file( rootDir ) ;
    @Suppress("UNCHECKED_CAST")
    buildFolders = gradle.extra.get("buildFoldersChild") as MutableList<String>

} else {

    //
    //  multiproject build
    //
    theProjectFolder = file( project.projectDir ) ;
    taskPrefix = ":" + theProjectFolder.name
    theProjectFolder.listFiles()?.forEach { possibleGradleFolder ->

        var folderStatus = "  running "

        if ( possibleGradleFolder.isDirectory
            && possibleGradleFolder.name != "buildSrc"
            && File( possibleGradleFolder, "build.gradle.kts").exists())  {

            buildFolders.add( ":${ theProjectFolder.name }:${ possibleGradleFolder.name }" )

        } else {
            folderStatus = "* skipping"
        }

        if ( folderStatus.contains("loading:") )
            csap.BuildUtils.print_indent ( "$folderStatus      $possibleGradleFolder.name" )
    }

}

csap.BuildUtils.print_two_columns( "theProjectFolder", "$theProjectFolder") ;
csap.BuildUtils.print_indent ("binding child modules to clean, build, and publish cd \n\t\t $buildFolders \n\n")

tasks.register(taskPrefix + "clean") {
    dependsOn(
        buildFolders.map { ":${ it }:clean" }
    )
}

tasks.register("build") {
    dependsOn(
        buildFolders.map { ":${ it }:build" }
    )
    mustRunAfter("clean")
}


tasks.register("assemble") {
    dependsOn(
        buildFolders.map { ":${ it }:assemble" }
    )
    mustRunAfter("clean")
}
