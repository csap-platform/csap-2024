package csap

import java.io.File
import java.util.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper

import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport

object BuildUtils {

//    object Versions {
//        const val buildToolsVersion = "1.0.0"
//    }

//    const val myBuildVersion = "org.sample:simple:${Versions.buildToolsVersion}"

    const val SHOW_BUILD = "blog"

    var myLevel="off"

    fun  isInfo():Boolean {
//        println("\t\t myLevel: ${myLevel}") ;
//        if ( System.getProperty(SHOW_BUILD) != null ) {
        if ( myLevel != null
            &&  !myLevel.lowercase().equals( "off")  ) {
            return true ;
        }
        return false;
    }
    fun  isDebug():Boolean {
        if ( isInfo()
            && myLevel.lowercase().equals("debug")) {
            return true ;
        }
        return false;
    }

    fun sourceName( level : Int): String {
        val frames = Thread.currentThread().stackTrace ;
        if (frames.size >= level ) {
            var layer = Thread.currentThread().stackTrace[level] ;
            // + layer.methodName
            return String.format("%-50s", "<${layer.fileName}>" )
        }
        return "" ;
    }

    fun print_section(message: String) {
        if ( isInfo() ) {
            println("\n \n* \n** \n*** ${message} \n** \n*")
        }
    }

    fun print_header(message: String) {
        if ( isInfo() ) {
            println("\n\n--- ${message} ${ sourceName(2) } \n")
        }
    }

    fun print_header_debug(message: String) {
        if ( isDebug() ) {
            println("\n\n--- ${message} ${ sourceName(2) } \n")
        }
    }
    fun print_header_always(message: String) {
        println("\n\n--- ${message} \n")
    }

    fun print_location(message: String, theFile:File) {
        if ( isInfo() ) {
            var location= getPathLevels( theFile, 1)
            println("--- ${String.format("%-30s", message) }   location: ${ location }")
        }
    }

    fun getPathLevels(theFile:File, levels:Int ):String {

        var filePath = theFile.parentFile
        var result= theFile.name ;
        var currentLevel=0 ;
        while ( filePath != null && currentLevel++ < levels ) {
            result= filePath.name + "/" + result ;
            filePath=filePath.parentFile
        }
        return result ;
    }


    fun print_list(theList: List<String>, message: String = "Items") {

        if ( isInfo() ) {
            println("\n")
            print("$message:")

            theList.forEach {
                print_indent(it)
            }
            println("\n\n")
        }
    }

    fun print_indent(message: String) {
        if ( isInfo() ) {
            println("${ String.format("%5s", "--" )} ${message}")
        }
    }


    fun print_indent2(message: String) {
        if ( isInfo() ) {
            println("${ String.format("%15s", "--" )} ${message}")
        }
    }

    fun print_indent_debug(message: String) {
        if ( isDebug() ) {
            println("${ String.format("%5s", "--" )} ${message}")
        }
    }

    fun print(message: String) {
        if ( isInfo() ) {
            println("--- ${message}")
        }
    }

    fun print_two_columns(col1: String, col2: Any) {
        if ( isInfo() ) {
            println("${col1.padStart(30)}:   ${col2.toString()}")
        }
    }
    fun debug_print_two_columns(col1: String, col2: Any) {
        if ( isDebug() ) {
            println("${col1.padStart(30)}:   ${col2.toString()}")
        }
    }

    var jsonMapper = ObjectMapper();
    @Suppress("UNCHECKED_CAST")
    fun prettyPrint(theItem: Any?): String {
        var jsonDetails="not possible"

        jsonMapper
            .configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL, true);
        try {
            jsonDetails = jsonMapper.convertValue( theItem, JsonNode::class.java).toPrettyString()
        } catch (e: java.lang.Exception) {
//            print_indent("Failed to process object ${ e.message }")
            jsonDetails = "Failed to process object ${ e.message }"
        }

        return jsonDetails
    }

    /**
     * Scan the specified folder for items starting with artifactPrefix,
     * then scan that for folders starting with projectPrefix
     * @param artifactPrefix defaults to "artifact"
     * @param projectPrefix defaults to "csap"
     */
    fun findProjectFoldersUsingPrefix(
        rootBuildFolder: File,
        artifactPrefix: String = "artifact",
        projectPrefix: String = "csap"
    ): MutableList<String> {

        val theFolders = mutableListOf<String>()
        //theFolders.add( "peter" )

        rootBuildFolder.listFiles().forEach { artifactFolder: File ->

            if (!artifactFolder.name.startsWith(artifactPrefix))
                return@forEach;

            artifactFolder.listFiles().forEach artifact@{ projectFolder: File ->


                if (!projectFolder.name.startsWith(projectPrefix))
                    return@artifact;

                theFolders.add(":${artifactFolder.name}:${projectFolder.name}")

            }
        }

        return theFolders
    }

    fun findProjectsUsingExtension(
        rootBuildFolder: File,
        fileEnding: String ,
    ): List<String> {

        var theFolders = rootBuildFolder
            .walk(FileWalkDirection.TOP_DOWN).maxDepth(5)
//            .walkTopDown()
//            .filter{ item -> item.name.endsWith( fileEnding ) }
            .filter{ item -> item.name.equals( item.parentFile.name + fileEnding ) }
            .map{ item -> item.parentFile.absolutePath }
            .map{ path -> path.substring( rootBuildFolder.absolutePath.length ) }
            .map{ path -> path.replace("/", ":")}
            .toList()


        return theFolders
    }


    fun generatePlatformUsingToml( tomlSourceFile: File): MutableList<String> {

        print_header("loading: ${tomlSourceFile.absolutePath}")

        var theDependencies = mutableListOf<String>()
        if ( ! tomlSourceFile.exists() ) {
            print_section( "Failed to locate toml file")
            throw Exception( "Failed to locate toml file" )
        }

        val tomlMapper = TomlMapper()
//
        val versionCatalog: JsonNode = tomlMapper.readTree(tomlSourceFile)

        print_indent_debug( "loaded ${ versionCatalog.toPrettyString() }" )

        val fieldNames = ArrayList<String>()
        versionCatalog.fieldNames().forEachRemaining { fieldName: String ->
            fieldNames.add(
                fieldName
            )
        }
        print_indent("fieldNames:  $fieldNames")


        val librariesNode = versionCatalog.path("libraries")
        val versionNode = versionCatalog.path("versions")

        if (!librariesNode.isObject()
            || ! versionNode.isObject()
        ) {

            print_section( "Invalid toml file: did not find libraries or versions")
            throw Exception( "Invalid toml file: did not find libraries or versions" )
        }

        val libraries = librariesNode as ObjectNode
        val versions = versionNode as ObjectNode
        print_indent("libraries: ${libraries.size()}, versions: ${versions.size()} ")

        theDependencies =  streamIt( libraries.fieldNames() )

            .filter { libraryName -> libraryName.startsWith("managed_") }

            .filter { libraryName -> libraries.path(libraryName).isObject() }

            .map { libraryName -> libraries.path(libraryName) }

            .map { library ->

                val versionVariable =  library.path("version").path("ref").asText()
                val version = versions.path(versionVariable).asText()

                library.path("module").asText() + ":" + version
            }

            .collect(Collectors.toList()) ;

        return theDependencies ;
    }
    fun streamIt( theIterator :Iterator<String> ): Stream<String> {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize<Any>(
                theIterator,
                Spliterator.ORDERED
            ), false
        ) as Stream<String>;
    }
}