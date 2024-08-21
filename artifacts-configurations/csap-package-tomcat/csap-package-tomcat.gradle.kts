plugins {
    id( "package-conventions" )
}

distributions {

    main {

        contents {

            into("/custom") {
                from ("custom")
                dirMode = "755".toInt( 8 )
                fileMode = "755".toInt( 8 )
            }

        }
    }
}