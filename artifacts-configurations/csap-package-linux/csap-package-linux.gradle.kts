plugins {
    id( "package-conventions" )
}


description = "-->  provides os scripts"

//configure<CsapMessagePluginExtension> {
//    message.set( "\n\n inside ${ project.buildFile.absolutePath } \n\n" )
//}


distributions {

    main {

        contents {

            into("/auto-plays").from( "auto-plays")


            into("/installer") {
                from ("installer")
                from ("environment")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

            into("/platform-bin") {
                from ("platform-bin")
                from ("environment")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

        }
    }
}

