//
//BuildUtils.print_section(  "" + settings.buildscript.sourceFile  )
//
//
//
//val projectFolder = file( rootDir )
//BuildUtils.print_two_columns ("Project Folder", "${ projectFolder.name }")
//
//var buildFolders=ArrayList<String>()
//
//projectFolder.listFiles()?.forEach { possibleGradleFolder ->
//
//    var folderStatus = "  running "
//
//    if ( possibleGradleFolder.isDirectory
//        && possibleGradleFolder.name != "buildSrc"
//        && File( possibleGradleFolder, "build.gradle.kts").exists())  {
//
//        buildFolders.add( possibleGradleFolder.name )
//
//    } else {
//        folderStatus = "* skipping"
//    }
//
//    if ( folderStatus.contains("loading:") )
//        BuildUtils.print_indent ( "$folderStatus      $possibleGradleFolder.name" )
//}
//
//gradle.extra.set("buildFoldersChild", buildFolders)
//
//
//BuildUtils.print_two_columns ("buildFolders", "$buildFolders")
//
//include(
//    buildFolders
//)
