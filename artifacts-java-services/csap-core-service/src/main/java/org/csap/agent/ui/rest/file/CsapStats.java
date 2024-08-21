package org.csap.agent.ui.rest.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.docs.CsapDoc;
import org.csap.helpers.CSAP;
import org.csap.security.CsapUser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.ModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping( CsapConstants.FILE_URL )
@CsapDoc( title = "Statistics Collector", notes = {
        "Used for browsing, collecting csv and associated schema files",
        "<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
        "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class CsapStats {

    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    @Inject
    public CsapStats(
            CsapApis csapApis,
            ObjectMapper jacksonMapper,
            FileApiUtils fileApiUtils ) {

        this.csapApis = csapApis;
        this.jacksonMapper = jacksonMapper;
        this.fileApiUtils = fileApiUtils;

    }

    CsapApis csapApis;
    ObjectMapper jacksonMapper;
    FileApiUtils fileApiUtils;

    long maxSizeBytes = 10 * 1024 * 1024;

    public final static String STATS_URL = "/service/stats";

    @GetMapping( STATS_URL )
    public ObjectNode getStatistics(
            @RequestParam( value = CsapConstants.SERVICE_PORT_PARAM, required = true ) String serviceName,
            String hostName,
            @RequestParam( defaultValue = "-1" ) Integer kbLimit,
            @RequestParam( defaultValue = "-1" ) Integer lineCount,
            String statsPath,
            @RequestParam( defaultValue = "" ) String specPath,
            @RequestParam( defaultValue = "default" ) String alternateBasePath
    ) {

        var statsReport = jacksonMapper.createObjectNode( );

        logger.info( CSAP.buildDescription( "Collecting stats",
                "serviceName", serviceName,
                "hostName", hostName,
                "alternateBasePath", alternateBasePath,
                "statsPath", statsPath,
                "specFile", specPath,
                "kbLimit", kbLimit,
                "lineCount", lineCount ) );

        if ( csapApis.application( ).isAdminProfile( ) ) {

            MultiValueMap< String, String > urlVariables = new LinkedMultiValueMap< String, String >( );

            urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );
            urlVariables.set( "serviceName", serviceName );
            urlVariables.set( "alternateBasePath", alternateBasePath );
            urlVariables.set( "statsPath", statsPath );
            urlVariables.set( "specPath", specPath );
            urlVariables.set( "kbLimit", kbLimit.toString( ) );
            urlVariables.set( "lineCount", lineCount.toString( ) );

            String url = CsapConstants.FILE_URL + STATS_URL;
            List< String > hosts = new ArrayList<>( );
            hosts.add( hostName );

            logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

            JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                    hosts,
                    url,
                    urlVariables );

            return ( ObjectNode ) remoteCall.path( hostName );

        }

        var serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceName );

//		logger.info("kbLimit: {}", kbLimit) ;
        if ( kbLimit < 0 ) {

            kbLimit = Long.valueOf( maxSizeBytes / 1024 ).intValue( );
//			logger.info("kbLimit: {}", kbLimit) ;

        }
        logger.info( "kbLimit: {}", kbLimit );

        if ( serviceInstance != null ) {

            // var statsFolder = csapApis.application( ).getRequestedFile( folderName,
            // serviceName, false ) ;

            var statsFolderString = serviceInstance.resolveRuntimeVariables( serviceInstance.getStatsDirectory( ) );
            var checkFolder = new File( statsFolderString );


            // build sources lists
            if ( checkFolder.getParentFile( ).getParentFile( ).exists( ) ) {
                var logPrevious = new File( checkFolder.getParentFile( ).getParentFile( ), "logs-previous" );
                if ( logPrevious.exists( ) && logPrevious.isDirectory( ) ) {
                    try {
                        var lastFilePaths = Files.list( logPrevious.toPath( ) )    // here we get the stream with full directory listing
                                .filter( f -> Files.isDirectory( f ) )
                                .sorted( ( p1, p2 ) -> Long.valueOf( p2.toFile( ).lastModified( ) )
                                        .compareTo( p1.toFile( ).lastModified( ) ) )
                                .map( Path::toFile )
                                .map( f -> new File( f, "stats" ) )
                                .filter( File::exists )
                                .filter( File::isDirectory )
                                .map( File::getAbsolutePath )
                                .collect( Collectors.toList( ) );

                        var allPaths = new ArrayList< String >( );
                        if ( checkFolder.exists( ) ) {
                            allPaths.add( statsFolderString );
                        }
                        var origFolderString = statsFolderString.replaceAll( "logs", "logs.orig" );
                        var origFolder = new File( origFolderString );
                        if ( origFolder.exists( ) ) {
                            allPaths.add( origFolderString );
                        }
                        allPaths.addAll( lastFilePaths );
                        statsReport.set( "discoveredFolders", jacksonMapper.convertValue( allPaths, ArrayNode.class ) );
                    } catch ( Exception e ) {
                        logger.warn( "Failed searching for previous folders", CSAP.buildCsapStack( e ) );
                    }
                }
            }


            if ( StringUtils.isNotEmpty( alternateBasePath ) &&
                    !alternateBasePath.equalsIgnoreCase( "default" ) ) {
                statsFolderString = alternateBasePath;
                checkFolder = new File( statsFolderString );
            }
            if ( !checkFolder.exists( ) ) {
                statsFolderString = searchForAlternateStatsPath( statsReport, statsFolderString, checkFolder );
            }


            if ( serviceInstance.is_docker_server( ) && false ) {

                statsReport.put( "docker", true );

                if ( !statsPath.isEmpty( ) ) {
                    logger.info( "Getting contents using docker" );

                    ModelMap modelMap = new ModelMap( );
                    var statsContent = csapApis.containerIntegration( ).writeContainerFileToString(
                            modelMap,
                            serviceInstance.getDockerContainerPath( ),
                            statsPath,
                            kbLimit * 1024,
                            lineCount,
                            1024 ).toString( );

                    statsReport.put( "content", statsContent );
                    var resultReport = modelMap.getAttribute( "result" );
                    if ( resultReport instanceof ObjectNode ) {
                        statsReport.set( "result", ( ObjectNode ) resultReport );
                    }


                } else {
                    // build listing using docker
                    logger.info( "Building listing using docker" );
                    var fileListingReport = fileApiUtils.buildListingUsingDocker(
                            serviceInstance.getDockerContainerPath( )
                                    + serviceInstance.getStatsDirectory( ),
                            new HashMap< String, String >( ), "/" );
                    logger.info( "docker listing: {}", CSAP.jsonPrint( fileListingReport ) );

                    var folderItems = statsReport.putArray( "listing" );

                    CSAP.jsonStream( fileListingReport )
                            .filter( fileItem -> fileItem.path( "name" ).asText( ).contains( "stats" ) )
                            .filter( fileItem -> fileItem.path( "size" ).asLong( ) > 0 )
                            .forEach( fileItem -> {
                                var statsItem = folderItems.addObject( );
                                statsItem.put( "name", fileItem.path( "name" ).asText( ) );
                                statsItem.put( "path",
                                        serviceInstance.getStatsDirectory( )
                                                + "/" + fileItem.path( "name" ).asText( ) );
                                statsItem.put( "bytes", fileItem.path( "size" ).asLong( ) );
                            } );
                }

            } else {

                var statsItem = new File( statsFolderString, statsPath );

                if ( statsItem.isFile( ) ) {

                    File specFile = null;

                    if ( StringUtils.isNotEmpty( specPath ) ) {

                        specFile = new File( statsFolderString, specPath );

                    }

                    loadStatsFiles( statsItem, specFile, kbLimit, lineCount, statsReport );

                } else if ( StringUtils.isNotEmpty( specPath ) ) {
                    statsReport.put( "error", "spec file not found: " + specPath );
                } else {

                    buildStatsListing( statsReport, statsItem, statsPath, statsFolderString );

                }
            }

        }

        return statsReport;

    }

    @NotNull
    private String searchForAlternateStatsPath( ObjectNode statsReport, String statsFolderString, File checkFolder ) {
        logger.info( "Did not locate {}, trying for .orig ", checkFolder );
        var statsOrig = statsFolderString.replaceAll( "logs", "logs.orig" );
        checkFolder = new File( statsOrig );
        if ( checkFolder.exists( ) ) {
            statsReport.put( "note", "requested folder: " + statsFolderString
                    + " not found.\n\n Detected: " + statsOrig );
            statsFolderString = statsOrig;
        }
        try {

            var logPrevious = new File( checkFolder.getParentFile( ).getParentFile( ), "logs-previous" );

            if ( logPrevious.exists( ) && logPrevious.isDirectory( ) ) {

                var logPreviousPath = logPrevious.toPath( );

                var lastFilePath = Files.list( logPreviousPath )    // here we get the stream with full directory listing
                        .filter( f -> Files.isDirectory( f ) )  // only use dirs
                        .max( Comparator.comparingLong( f -> f.toFile( ).lastModified( ) ) );

                if ( lastFilePath.isPresent( ) ) {
                    statsReport.put( "note", "requested folder: " + statsFolderString
                            + " not found.\n\n Detected previous: " + lastFilePath.get( ).toString( ) );
                    statsFolderString = lastFilePath.get( ).toString( ) + "/stats";
                }
            }
        } catch ( Exception e ) {
            logger.warn( "Failed searching for previous folders", CSAP.buildCsapStack( e ) );
        }
        return statsFolderString;
    }

    private void loadStatsFiles(
            File statsItem,
            File specFile,
            Integer kbLimit,
            Integer lineCount,
            ObjectNode statsReport ) {


        logger.info( CSAP.buildDescription( "Collecting stats",
                "statsItem3", statsItem,
                "specFile", specFile,
                "kbLimit", kbLimit,
                "lineCount", lineCount ) );

        ObjectNode fileLoadReport;

        if ( lineCount > 0 ) {

            fileLoadReport = fileApiUtils.fileReverseRead( statsItem, lineCount, kbLimit, null, -1 );

        } else {

            fileLoadReport = loadFileWithSizeLimit( kbLimit, statsItem );

        }

        statsReport.setAll( fileLoadReport );

        if ( specFile != null ) {

            var maxSizeForSpecFile = Long.valueOf( maxSizeBytes / 1024 ).intValue( );
            var specReport = loadFileWithSizeLimit( maxSizeForSpecFile, specFile );
            var specSection = statsReport.putObject( "spec" );
            specSection.setAll( specReport );

        }

    }

    private ObjectNode loadFileWithSizeLimit( Integer kbLimit, File statsItem ) {

        ObjectNode fileContentReport = jacksonMapper.createObjectNode( );

        try ( var randomAccessFile = new RandomAccessFile( statsItem, "r" ); ) {

            var fileLengthInBytes = randomAccessFile.length( );

            var byteLimit = kbLimit * 1024l;

            if ( byteLimit > maxSizeBytes
                    || kbLimit == 0 ) {

                byteLimit = maxSizeBytes;

            }

            var readOffset = Long.valueOf( fileLengthInBytes - byteLimit ).intValue( );

            if ( readOffset < 0 ) {

                readOffset = 0;

            }

            var numBytesToRead = Long.valueOf( fileLengthInBytes - readOffset );

            var bufferAsByteArray = new byte[ numBytesToRead.intValue( ) ];

            randomAccessFile.seek( readOffset ); // this goes to the byte

            randomAccessFile.read( bufferAsByteArray, 0, numBytesToRead.intValue( ) );

            var content = new String( bufferAsByteArray );

            // Trim to newline if needed
            if ( readOffset > 0 ) {

                var newLine = content.indexOf( "\n" );

                if ( newLine >= 0 ) {

                    content = content.substring( newLine + 1 );

                }

            }

            fileContentReport.put( "content", content );

        } catch ( Exception e ) {

            var stack = CSAP.buildCsapStack( e );

            fileContentReport.put( "error", statsItem + "\n\n" + stack );

            logger.info( "failed loading {} {}", statsItem, stack );

        }

        return fileContentReport;

    }

    private void buildStatsListing(
            ObjectNode statsReport,
            File statsItem,
            String statsFolder,
            String statsFolderString ) {

        //
        // folder/file listing
        //

        var folderItems = statsReport.putArray( "listing" );

        try ( var folderStream = Files.list( statsItem.toPath( ) ) ) {

            folderStream
                    .forEach( statsFolderPath -> {

                        if ( StringUtils.isEmpty( statsFolder ) ) {

                            addFolderItem( folderItems, statsFolderPath, statsFolderString );

                        } else if ( Files.isDirectory( statsFolderPath ) ) {

                            //
                            // Check for stats file listing in subdir of number (eg 38000)
                            //

                            try ( Stream< Path > fileStream = Files.list( statsFolderPath ) ) {

                                fileStream
                                        .filter( filePath -> Files.isRegularFile( filePath ) )
                                        .filter( filePath -> {

                                            var fileName = filePath.getFileName( ).toString( );
                                            return fileName.endsWith( ".log" )
                                                    || fileName.endsWith( ".json" )
                                                    || fileName.endsWith( ".yaml" )
                                                    || fileName.endsWith( ".yml" );

                                        } )
                                        .forEach( filePath -> {

                                            addFolderItem( folderItems, filePath, statsFolderString );

                                        } );

                            } catch ( Exception e ) {

                                logger.warn( "Failed to get file listing: ", CSAP.buildCsapStack( e ) );

                            }

                        }

                    } );

            if ( StringUtils.isNotEmpty( statsFolder ) ) {

//						 CSAP.jsonStream( folder )

            }

        } catch ( Exception e ) {

            var stack = CSAP.buildCsapStack( e );
            statsReport.put( "error", "Failed trying to access: " +
                    statsItem + "\n\n" + stack );
            logger.warn( "Failed to get folder listing: {}", stack );

        }

    }

    private void addFolderItem( ArrayNode folderItems, Path path, String statsFolderString ) {

        var folderItem = folderItems.addObject( );
        folderItem.put( "name", path.getFileName( ).toString( ) );
        folderItem.put( "path", path.toAbsolutePath( ).toString( ).replaceAll( statsFolderString, "" ) );

        var fsizeNumeric = -1L;
        var lastModified = FileTime.fromMillis( 1 );

        try {

            fsizeNumeric = Files.size( path );
            lastModified = Files.getLastModifiedTime( path );

        } catch ( Exception e1 ) {

            logger.info( "Failed to get info for file {}: {}", path.toString( ), e1 );

        }

        folderItem.put( "bytes", fsizeNumeric );

        var localDateTime = lastModified
                .toInstant( )
                .atZone( ZoneId.systemDefault( ) )
                .toLocalDateTime( );

        folderItem.put( "modified", localDateTime.format( fileApiUtils.getFileDateFormatter( ) ) );

    }

}
