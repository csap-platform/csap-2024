package org.csap.agent.ui.rest.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.agent.integrations.CsapEvents;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.csap.agent.ui.windows.CorePortals;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.integations.CsapInformation;
import org.csap.security.CsapUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

@Component
public class FileApiUtils {
    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    @Inject
    public FileApiUtils(
            CsapApis csapApis,
            ObjectMapper jacksonMapper
    ) {

        this.csapApis = csapApis;
        this.jacksonMapper = jacksonMapper;

    }

    CsapApis csapApis;
    ObjectMapper jacksonMapper;

    private OsCommandRunner osCommandRunner = new OsCommandRunner( 90, 3, "FileRequests" );

    private DateTimeFormatter fileDateFormatter = DateTimeFormatter.ofPattern( "HH:mm:ss, MMM dd yyyy" );

    public final static int CHUNK_SIZE_PER_REQUEST = 1024 * 100; // 500k/time:
    // Some browsers will choke on large chunks.

    static final String NAMESPACE_PVC_TOKEN = "namespacePvc:";

    void auditTrail(
            File targetFile,
            String operation
    ) {

        var userName = CsapUser.currentUsersID( );
        auditTrail( userName, targetFile, operation );

    }

    void auditTrail(
            String userName,
            File targetFile,
            String operation
    ) {

        var auditName = CsapConstants.FILE_URL + "/" + operation + "/" + targetFile.getAbsolutePath( );

        if ( csapApis.application( ).getActiveUsers( ).addTrail( userName, auditName ) ) {

            csapApis.events( ).publishUserEvent(
                    CsapEvents.CSAP_OS_CATEGORY + "/file/" + operation,
                    userName,
                    csapApis.events( ).fileName( targetFile, 60 ), targetFile.getAbsolutePath( ) );

        }

    }

    @Autowired
    CsapInformation csapInformation;

    @Autowired
    CorePortals corePortals;

    void setCommonAttributes(
            ModelMap modelMap,
            HttpServletRequest session,
            String windowName
    ) {

        setCommonAttributes( modelMap, session, windowName, null );

    }

    void setCommonAttributes(
            ModelMap modelMap,
            HttpServletRequest request,
            String windowName,
            String apiUser
    ) {

        if ( StringUtils.isNotEmpty( windowName ) ) {

            var auditName = CsapConstants.FILE_URL + "/" + windowName;
            var userName = CsapUser.currentUsersID( );

            if ( StringUtils.isNotEmpty( apiUser ) ) {

                userName = apiUser;
                corePortals.setCommonAttributes( modelMap, null );

            } else {

                corePortals.setCommonAttributes( modelMap, request.getSession( ) );

            }

            if ( csapApis.application( ).getActiveUsers( ).addTrail( userName, auditName ) ) {

                csapApis.events( ).publishUserEvent(
                        CsapEvents.CSAP_OS_CATEGORY + "/accessed",
                        userName,
                        "User interface: " + windowName, "" );

            }

        } else {

            corePortals.setCommonAttributes( modelMap, request.getSession( ) );

        }

        modelMap.addAttribute( "host", csapApis.application( ).getCsapHostName( ) );

        modelMap.addAttribute( "osUsers", csapApis.application( ).buildOsUsersList( ) );

        modelMap.addAttribute( "dateTime",
                LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) );

        modelMap.addAttribute( "userid", CsapUser.currentUsersID( ) );

        modelMap.addAttribute( "deskTop", csapApis.application( ).isDesktopHost( ) );

        modelMap.addAttribute( "user", csapApis.application( ).getAgentRunUser( ) );
        modelMap.addAttribute( "csapApp", csapApis.application( ) );

        modelMap.addAttribute( csapApis.application( ).rootProjectEnvSettings( ) );
        modelMap.addAttribute( "analyticsUrl", csapApis.application( ).rootProjectEnvSettings( )
                .getAnalyticsUiUrl( ) );
        modelMap.addAttribute( "eventApiUrl", csapApis.application( ).rootProjectEnvSettings( ).getEventApiUrl( ) );

        modelMap.addAttribute( "eventApiUrl", csapApis.application( ).rootProjectEnvSettings( ).getEventApiUrl( ) );

        modelMap.addAttribute( "eventMetricsUrl",
                csapApis.application( ).rootProjectEnvSettings( ).getEventMetricsUrl( ) );
        modelMap.addAttribute( "eventUser", csapApis.application( ).rootProjectEnvSettings( ).getEventDataUser( ) );
        modelMap.addAttribute( "life", csapApis.application( ).getCsapHostEnvironmentName( ) );

    }

    public static final String CSAP_SECURITYPROPERTIES = "csapSecurity.properties";
    public static final String CSAPTOKEN = "csap.token";

    boolean isInfraOnlyFile( File targetFile ) {

        String filePath = Application.filePathAllOs( targetFile );

        if ( filePath.endsWith( CSAPTOKEN ) ) {

            return true;

        }

        if ( filePath.endsWith( CSAP_SECURITYPROPERTIES ) ) {

            return true;

        }

        if ( filePath.endsWith( ".yml" ) ) {

            String processing = Application.filePathAllOs( csapApis.application( ).getCsapWorkingFolder( ) );

            if ( filePath.matches( processing + ".*admin.*application.*yml" ) ) {

                return true;

            }

            if ( filePath.matches( processing + ".*CsAgent.*application.*yml" ) ) {

                return true;

            }

        }

        if ( csapApis.application( ).isRunningOnDesktop( ) && filePath.endsWith( ".sh" ) ) {

            String processing = Application.filePathAllOs( csapApis.application( ).getCsapWorkingFolder( ) );
            Pattern p = Pattern.compile( processing + ".*pTemp.*open.*sh" );
            Matcher m = p.matcher( filePath );
            logger.info( " Checking pattern {} for file in {}", p.toString( ), filePath );

            if ( m.matches( ) ) {

                return true;

            }

        }

        return false;

    }

    String buildNamespaceListingPath(
            ServiceInstance service,
            String serviceProperyFolder
    ) {

        var volumeReports = csapApis.kubernetes( ).reportsBuilder( ).volumeReport( );
        var podNamespace = service.getDefaultContainer( ).getPodNamespace( );

        if ( StringUtils.isNotEmpty( podNamespace ) ) {

            if ( csapApis.application( ).isDesktopHost( ) ) {

                logger.warn( CsapApplication.testHeader( "setting test namespace for {}" ),
                        serviceProperyFolder );

            }

            var matchedVolumes = CSAP.jsonStream( volumeReports )
                    .filter( volumeReport -> volumeReport.path( "ref-namespace" ).asText( ).startsWith(
                            podNamespace ) )
                    .collect( Collectors.toList( ) );

            if ( matchedVolumes.size( ) != 1 ) {

                logger.warn( "Unexpected match count: {} \n {}",
                        matchedVolumes.size( ),
                        matchedVolumes );

            }

            var nfsSubPath = serviceProperyFolder.substring( NAMESPACE_PVC_TOKEN.length( ) );

            for ( var volumeReport : matchedVolumes ) {

                // var volumeReport = matchedVolumes.get( 0 ) ;

                var volumeNfsServer = volumeReport.path( "nfs-server" ).asText( "nfs-missing" );

                var volumePath = volumeReport.path( "path" ).asText( "path-missing" );

                var nfsPath = findNfsPath( volumePath, volumeNfsServer );

                logger.info( CSAP.buildDescription(
                        "NFS property",
                        "volumeNfsServer", volumeNfsServer,
                        "volumePath", volumePath,
                        "nfsPath", nfsPath ) );

                serviceProperyFolder = nfsPath + nfsSubPath;

                var testFolder = new File( serviceProperyFolder );

                if ( testFolder.exists( ) ) {

                    var testFiles = new ArrayList<String>( );

                    buildLogFiles(
                            testFolder,
                            service,
                            testFiles,
                            null );

                    if ( testFiles.size( ) > 0 ) {

                        logger.info( "serviceProperyFolder: {} \n {}", serviceProperyFolder, Arrays.asList(
                                testFiles ) );

                        // use the first PVC that contains files
                        break;

                    }

                }

            }

        }

        return serviceProperyFolder;

    }

    String buildLogFiles(
            File folderToSearch,
            ServiceInstance instance,
            List<String> logFileNamesInSubDir,
            String firstFileNameInFirstDir
    ) {
        // Some services have multiple subfolders in log
        // directory.
        // One directory down will also be scanned for files.

        // var subFiles = folderToSearch.listFiles( ) ;
        var firstFileAtomic = new AtomicReference<String>( );
        firstFileAtomic.getAndSet( firstFileNameInFirstDir );

        if ( !folderToSearch.canRead( ) ) {

            try {

                var fileListings = buildListingUsingRoot( folderToSearch, new HashMap<String, String>( ), "notUsed" );

                CSAP.jsonStream( fileListings )

                        .filter( fileListing -> !fileListing.has( "folder" ) )

                        .filter( fileListing -> fileListing.path( "name" ).asText( )
                                .matches( instance.getLogRegEx( ) ) )

                        .forEach( fileListing -> {

                            var fileName = fileListing.path( "name" ).asText( );

                            var filePath = folderToSearch.getName( ) + "/" + fileName;

                            if ( StringUtils.isEmpty( firstFileAtomic.get( ) ) ) {

                                firstFileAtomic.getAndSet( filePath );

                            }

                            // logFileNamesInSubDir.add(path);
                            // use full path for passing to other UIs
                            try {

                                logFileNamesInSubDir.add( Application.FileToken.ROOT.value
                                        + folderToSearch.getCanonicalPath( ) + "/" + fileName );

                            } catch ( Exception e ) {

                                logger.error( "Reverting to absolute path {}", CSAP.buildCsapStack( e ) );
                                logFileNamesInSubDir.add( Application.FileToken.ROOT.value
                                        + folderToSearch.getAbsolutePath( ) + "/" + fileName );

                            }

                        } );

            } catch ( Exception e1 ) {

                logger.warn( "Failed getting log files: {}", CSAP.buildCsapStack( e1 ) );

            }

        } else {

            try ( Stream<Path> pathStream = Files.list( folderToSearch.toPath( ) ) ) {

                var pathsFromFs = pathStream
                        .filter( Files::isRegularFile )
                        .map( path -> path.toAbsolutePath( ).toString( ) )
                        .filter( pathString -> pathString.matches( instance.getLogRegEx( ) ) )
                        .map( pathString -> {

                            return Application.FileToken.ROOT.value + pathString;

                        } )
                        .collect( Collectors.toList( ) );

                logFileNamesInSubDir.addAll( pathsFromFs );

            } catch ( Exception e ) {

                logger.warn( "Failed to get file listing: ", CSAP.buildCsapStack( e ) );

            }

//			for ( var subFile : subFiles ) {
//
//				if ( subFile.isFile( ) ) {
//
//					if ( ! subFile.getName( ).matches(
//							instance.getLogRegEx( ) ) ) {
//
//						continue ;
//
//					}
//
//					var filePath = folderToSearch.getName( ) + "/" + subFile.getName( ) ;
//
//					if ( StringUtils.isEmpty( firstFileAtomic.get( ) ) ) {
//
//						firstFileAtomic.getAndSet( filePath ) ;
//
//					}
//
//					try {
//;
//
//						logFileNamesInSubDir.add( Application.FileToken.ROOT.value + subFile.getAbsolutePath( ) ) ;
//
//					} catch ( Exception e ) {
//
//						logger.error( "Reverting to absolute path", e ) ;
//						logFileNamesInSubDir.add( Application.FileToken.ROOT.value + subFile.getAbsolutePath( ) ) ;
//
//					}
//
//				}
//
//			}

        }

        return firstFileAtomic.get( );

    }

    String findNfsPath(
            String volumePath,
            String volumeNfsServer
    ) {

        var folders = volumePath.split( "/", 3 );
        var folders2 = volumePath.split( "/", 4 );

        // logger.info( "fromFolder: {}, folders2: {}", fromFolder, Arrays.asList(
        // folders2 ) );
        var firstFolder = folders[ 1 ];
        var mountSource = volumeNfsServer + ":/" + firstFolder;

        var mountLocation = csapApis.osManager( ).getMountPath( mountSource );

        volumePath = mountLocation + "/" + folders[ 2 ];
        File testNfsFolder = new File( volumePath );

        if ( !testNfsFolder.exists( ) ) {

            // some time nfs is subfoldered, strip off another level\
            volumePath = mountLocation + "/" + folders2[ 3 ];
            testNfsFolder = new File( volumePath );

            if ( !testNfsFolder.exists( ) ) {

                logger.warn( "Unable to locate nfs folder: {}, \n\tfromFolder: {}, folders2: {}", testNfsFolder,
                        volumePath, Arrays.asList( folders2 ) );

            }

        }

        // fromFolder = FileToken.ROOT.value + fromFolder ;
        logger.info( "nfs detected - mountSource: {}, fromFolder: {}", mountSource, volumePath );
        return volumePath;

    }

    boolean isCompressedFile( String filePath ) {
        if ( filePath.endsWith( "/" ) ) {
            filePath = filePath.substring( 0, filePath.length( ) - 1 );
        }
        return filePath.endsWith( ".war" )
                || filePath.endsWith( ".jar" )
                || filePath.endsWith( ".zip" );
    }

    boolean isCompressedFileInPath( String filePath ) {
        return filePath.contains( ".war/" )
                || filePath.contains( ".jar/" )
                || filePath.contains( ".zip/" );
    }

    File extractFileFromZip(
            File targetFile
    ) throws IOException {

        var fromFolder = targetFile.getAbsolutePath( );

        logger.info( "Attempting to load: {}", fromFolder );

//		/Users/peter.nightingale/csap/csap-host-install-22.10.zip/csap-platform/bin/admin-clean-deploy.sh

        var zipViewerFolder = new File(
                csapApis.application( ).getCsapSavedFolder( ),
                "zip-viewer"
        );

        zipViewerFolder.mkdirs( );

        var extension = ".war";
        var sourceZipPieces = fromFolder.split( ".war/", 2 );
        if ( sourceZipPieces.length != 2 ) {
            extension = ".zip";
            sourceZipPieces = fromFolder.split( ".zip/", 2 );
        }
        if ( sourceZipPieces.length != 2 ) {
            extension = ".jar";
            sourceZipPieces = fromFolder.split( ".jar/", 2 );
        }
        if ( sourceZipPieces.length == 2 ) {
            var sourceZipPath = sourceZipPieces[ 0 ] + extension;
            var filePathToExtractFromZip = sourceZipPieces[ 1 ];


            logger.info( CSAP.buildDescription( "extracting",
                    "fromFolder", fromFolder,
                    "sourceZipPath", sourceZipPath,
                    "filePathToExtractFromZip", filePathToExtractFromZip,
                    "zipViewerFolder", zipViewerFolder
            ) );

            var sourceZipFile = new File( fromFolder );
            var fromFolderFile = new File( fromFolder );

            var unzipCommand = List.of(
                    "#!/bin/bash",
                    getOsCommandRunner( ).sourceCommonFunctions( ),
                    "",
                    "cd " + zipViewerFolder.getAbsolutePath( ),
                    "",
                    "backup_file '" + fromFolderFile.getName( ) + "'",
                    "",
                    "unzip -qq -j " + sourceZipPath + " '" + filePathToExtractFromZip + "'",
                    "" );

            logger.info( CSAP.buildDescription( "unzipCommand", unzipCommand ) );

            var unzipOutput = getOsCommandRunner( ).runUsingDefaultUser( "file-unzip", unzipCommand );
            logger.info( CSAP.buildDescription( "unzipOutput",
                    Arrays.asList( unzipOutput.split( System.lineSeparator( ) ) ) ) );

            return new File( zipViewerFolder, fromFolderFile.getName( ) );
        }

//		"unzip -qq csap-package-linux.zip installer/*"
        return null;
    }

    ArrayNode buildListingUsingZip(
            File sourceZipFile,
            Map<String, String> duLines,
            String fromFolder
    )
            throws IOException {

        var fileListing = jacksonMapper.createArrayNode( );

        if ( isCompressedFileInPath( sourceZipFile.getAbsolutePath( ) ) ) {
            logger.info( "Extracting {}", sourceZipFile );
            sourceZipFile = extractFileFromZip( sourceZipFile );
            fromFolder = Application.FileToken.ROOT.value + sourceZipFile.getAbsolutePath( ) + "/";
        }

        if ( !sourceZipFile.exists( ) ) {

            var fileReport = fileListing.addObject( );
            var name = "not-able-to-open";

            fileReport.put( "name", name );
            fileReport.put( "location",
                    fromFolder + name );
            fileReport.put( "title", name );

            return fileListing;
        }

        final String sourceFromFolder = fromFolder;

        //
        // Switch to https://commons.apache.org/proper/commons-compress/examples.html
        //

        try ( ZipFile zipFile = new ZipFile( sourceZipFile ) ) {
//			zipFile.stream()
//					.map( ZipEntry::getName)
//					.forEach(System.out::println);

            zipFile.stream( )
                    .filter( zipEntry -> !zipEntry.isDirectory( ) )
                    .forEach( zipEntry -> {
                        var fileReport = fileListing.addObject( );


                        var name = zipEntry.getName( );
                        var location = sourceFromFolder;
                        location += name;

                        fileReport.put( "name", name );
                        fileReport.put( "filter", name );
                        fileReport.put( "location", location );
                        fileReport.put( "title", name );

                        LocalDateTime localDateTime = zipEntry.getLastModifiedTime( )
                                .toInstant( )
                                .atZone( ZoneId.systemDefault( ) )
                                .toLocalDateTime( );
                        fileReport.put(
                                "meta", zipEntry.getSize( ) + ","
                                        + localDateTime.format( fileDateFormatter ) );
                        fileReport.put( "size", zipEntry.getSize( ) );
                    } );

        }


        return fileListing;

    }


    public String convertMarkdownToHtml(
            String cliResults,
            String readMeSource
    ) {

        var extensions = Arrays.asList( TablesExtension.create( ) );
        var parser = Parser.builder( ).extensions( extensions ).build( );
        var document = parser.parse( cliResults );
        var renderer = HtmlRenderer.builder( ).extensions( extensions ).build( );

        cliResults = renderer.render( document );

        cliResults = cliResults.replaceAll( "a href", "a target=_blank href" );
        cliResults = cliResults.replaceAll( "<table>", "<table class=csap>" );

        if ( StringUtils.isNotEmpty( readMeSource )
                && readMeSource.startsWith( "http" )
                && readMeSource.endsWith( ".md" ) ) {

            var urlPrefix = readMeSource.substring( 0, readMeSource.lastIndexOf( "/" ) );
            cliResults = cliResults.replaceAll( "\"ghubdocs/", "\"" + urlPrefix + "/ghubdocs/" );

        }

        return cliResults;

    }

    public ArrayNode
    buildListingUsingRoot(
            File targetFolder,
            Map<String, String> duLines,
            String fromFolder
    )
            throws IOException {

        var path = pathWithSpacesEscaped( targetFolder );
        var lsCommand = List.of(
                "#!/bin/bash",
                "ls -al " + path,
                "" );

        logger.debug( "lsCommand: {}", lsCommand );

        var lsOutput = getOsCommandRunner( ).runUsingRootUser( "file-ls", lsCommand );
        lsOutput = csapApis.application( ).check_for_stub( lsOutput, "linux/ls-using-root.txt" );

        var fileListing = buildListingUsingOs( fromFolder, lsOutput, duLines );

        return fileListing;

    }


    final static String REPLACE_DEBIAN = Matcher.quoteReplacement( "1," );

    ArrayNode buildListingUsingOs(
            String fromFolder,
            String lsOutput,
            Map<String, String> duLines
    ) {

        logger.debug( CsapApplication.header( "ls: {} " ) + CsapApplication.header( "duLines: {} " ), lsOutput,
                duLines );

        var lsOutputLines = lsOutput.split( "\n" );
        var fileListing = jacksonMapper.createArrayNode( );

        for ( String line : lsOutputLines ) {

            String[] lsOutputWords = CsapConstants.singleSpace( line
                            .replaceAll( REPLACE_DEBIAN, "" ) )
                    .split( " ", 9 );

            logger.debug( "line: {} words: {} ", line, lsOutputWords.length );

            if ( lsOutputWords.length == 9 && lsOutputWords[ 0 ].length( ) >= 10 ) {

                ObjectNode itemJson = fileListing.addObject( );
                String currentItemName = lsOutputWords[ 8 ];
                itemJson.put( "name", currentItemName );

                String fsize = lsOutputWords[ 4 ] + " b, ";
                long fsizeNumeric = 0;

                try {

                    Long fileSize = Long.parseLong( lsOutputWords[ 4 ] );
                    fsizeNumeric = fileSize.longValue( );
                    if ( fileSize > 1000 )
                        fsize = fsizeNumeric / 1000 + "kb, ";

                } catch ( NumberFormatException e ) {

                    logger.info( "Unable to parse date from: '{}', reason: '{}' ",
                            Arrays.asList( lsOutputWords ),
                            CSAP.buildCsapStack( e ) );

                }

                if ( lsOutputWords[ 0 ].contains( "d" ) ) {

                    itemJson.put( "folder", true );
                    itemJson.put( "lazy", true );

                    if ( duLines != null ) {
                        var matchedDu = duLines.entrySet( ).stream( )
                                .filter( entry -> entry.getKey( ).endsWith( "/" + currentItemName ) )
                                .findFirst( );

                        if ( matchedDu.isPresent( ) ) {

                            var reportSize = matchedDu.get( ).getValue( );
                            fsize = reportSize + "MB, ";

                            try {

                                fsizeNumeric = Long.parseLong( reportSize );
                                fsizeNumeric = fsizeNumeric * 1000 * 1000;

                                // gets to bytes
                            } catch ( Exception e ) {

                                logger.error( "Failed to parse to long" + fsize );

                            }

                        }
                    }

                }

                itemJson.put( "restricted", true );
                itemJson.put( "filter", false );
                itemJson.put( "location",
                        fromFolder + lsOutputWords[ 8 ] );
                // itemJson.put("data", dataNode) ;
                itemJson.put( "title", lsOutputWords[ 8 ] );
                itemJson.put(
                        "meta",
                        "~"
                                + fsize
                                + lsOutputWords[ 5 ] + " "
                                + lsOutputWords[ 6 ] + " "
                                + lsOutputWords[ 7 ] + ","
                                + lsOutputWords[ 0 ] + ","
                                + lsOutputWords[ 1 ] + ","
                                + lsOutputWords[ 2 ] + ","
                                + lsOutputWords[ 3 ] );

                itemJson.put( "size", fsizeNumeric );
                itemJson.put( "target", fromFolder + lsOutputWords[ 8 ]
                        + "/" );

            }

        }

        if ( fileListing.size( ) == 0 ) {

            fileListing = jacksonMapper.createArrayNode( );
            ObjectNode itemJson = fileListing.addObject( );
            itemJson.put( "title", "Unable to get Listing" );

        }

        return fileListing;

    }

    public ArrayNode buildListingUsingJava(
            File targetFile,
            String fromFolder,
            Map<String, String> duLines
    ) {

        var fileListing = jacksonMapper.createArrayNode( );

        var readMeta = new AtomicBoolean( true );
        var cumlativeReadsMs = new AtomicLong( );

        try ( Stream<Path> pathStream = Files.list( targetFile.toPath( ) ) ) {

//			files = pathStream
//					.map( Path::toFile )
//					.collect( Collectors.toList( ) ) ;

            pathStream.forEach( path -> {

                var simpleName = path.getName( path.getNameCount( ) - 1 ).toString( );
                long fsizeNumeric = -1;
                FileTime lastModified = FileTime.fromMillis( 1 );

                // path.is

                if ( readMeta.get( ) ) {
                    // super slow filesystem - we abort early

                    var metaTimer = csapApis.metrics( ).startTimer( );

                    try {

                        fsizeNumeric = Files.size( path );
                        lastModified = Files.getLastModifiedTime( path );

                    } catch ( Exception e1 ) {

                        logger.info( "Failed to get info for file {}: {}", simpleName, e1 );

                    }

                    var metaNanos = csapApis.metrics( ).stopTimer( metaTimer, "os.read-file-meta" );
                    var metaMs = TimeUnit.NANOSECONDS.toMillis( metaNanos );

                    var currentTotal = cumlativeReadsMs.addAndGet( metaMs );

                    if ( currentTotal > csapApis.application().configuration().getMaxMsToListFiles() ) {

                        logger.warn(
                                "Slow Reads detected, aborting size and date reads: {}, while listing {} metaRead: {}ms file: {}",
                                currentTotal, targetFile, metaMs, simpleName );
                        readMeta.set( false );

                    }

                    logger.debug( "{} metaRead: {}ms file: {}", targetFile, metaMs, simpleName );

                }

                var fsize = CSAP.printBytesWithUnits( fsizeNumeric );

                var fileReport = fileListing.addObject( );
                // ObjectNode dataNode =jacksonMapper.createObjectNode() ;

                if ( Files.isDirectory( path ) ) {

                    fileReport.put( "folder", true );
                    fileReport.put( "lazy", true );

                    if ( duLines != null ) {
                        var matchedDu = duLines.entrySet( ).stream( )
                                .filter( entry -> entry.getKey( ).endsWith( "/" + simpleName ) )
                                .findFirst( );

                        if ( matchedDu.isPresent( ) ) {

                            var reportSize = matchedDu.get( ).getValue( );
                            fsize = reportSize + "MB, ";

                            try {

                                fsizeNumeric = Long.parseLong( reportSize );
                                fsizeNumeric = fsizeNumeric * 1000 * 1000;

                                // gets to bytes
                            } catch ( Exception e ) {

                                logger.error( "Failed to parse to long" + fsize );

                            }

                        }
                    }

                }

                boolean filtered = false;

                // logger.info( "simpleName: {}", simpleName ) ;

                if ( simpleName.equals( ".ssh" ) ) {

                    filtered = true;

                }

                fileReport.put( "name", simpleName );
                fileReport.put( "filter", filtered );
                fileReport.put( "location",
                        fromFolder + simpleName );
                fileReport.put( "title", simpleName );

                if ( fsizeNumeric < 0 ) {

                    fileReport.put(
                            "meta", fsize + ","
                                    + "slow" );

                    fileReport.put( "size", "-" );

                } else {

//				fileReport.put(
//						"meta", "~"
//								+ fsize
//								+ fileDateOutput.format( new Date( lastModified ) ) ) ;
                    LocalDateTime localDateTime = lastModified
                            .toInstant( )
                            .atZone( ZoneId.systemDefault( ) )
                            .toLocalDateTime( );

                    fileReport.put(
                            "meta", fsize + ","
                                    + localDateTime.format( fileDateFormatter ) );

                    fileReport.put( "size", fsizeNumeric );

                }

                fileReport.put( "target", fromFolder + simpleName + "/" );

            } );

        } catch ( Exception e ) {

            if ( targetFile.getAbsolutePath().contains( "chef" ) ) {
                logger.debug( "Failed to get listing for {} reason\n {} ", targetFile, CSAP.buildCsapStack( e ) );
            } else {
                logger.info( "Failed to get listing for {} reason\n {} ", targetFile, CSAP.buildCsapStack( e ) );
            }

        }

//		for ( var itemInFolder : filesInFolder ) {
//
//
//		}

        return fileListing;

    }

    ArrayNode buildListingUsingDocker(
            String targetFolder,
            Map<String, String> duLines,
            String fromFolder
    ) {

        logger.info( "targetFolder: {} ", targetFolder );

        ArrayNode fileListing;

        if ( targetFolder.equals( "/" ) ) {

            var containerListing = jacksonMapper.createArrayNode( );

            var containerNames = csapApis.containerIntegration( ).containerNames( true );

            if ( csapApis.isCrioInstalledAndActive( ) ) {

                containerNames.addAll( csapApis.crio( ).containerNames( ) );

            }

            containerNames

                    .forEach( fullName -> {

                        ObjectNode itemJson = containerListing.addObject( );
                        var name = fullName;

                        if ( fullName.startsWith( "/" ) ) { // docker listings

                            name = fullName.substring( 1 );

                        }

                        itemJson.put( "folder", true );
                        itemJson.put( "lazy", true );

                        itemJson.put( "name", name );
                        itemJson.put( "location", fromFolder + name );
                        // itemJson.put("data", dataNode) ;
                        itemJson.put( "title", name );

                    } );

            fileListing = containerListing;

        } else {
            // do container ls & feed to OS listing

            String[] dockerContainerAndPath = splitDockerTarget( targetFolder );
            String lsOutput;

            if ( csapApis.isCrioInstalledAndActive( )
                    && targetFolder.contains( OsManager.CRIO_DELIMETER ) ) {

                var containerName = dockerContainerAndPath[ 0 ];

                if ( containerName.startsWith( "/" ) ) { // docker listings

                    containerName = containerName.substring( 1 );

                }

                lsOutput = csapApis.crio( ).listFiles(
                        containerName,
                        dockerContainerAndPath[ 1 ] );

            } else {

                lsOutput = csapApis.containerIntegration( ).listFiles(
                        dockerContainerAndPath[ 0 ],
                        dockerContainerAndPath[ 1 ] );

            }

            fileListing = buildListingUsingOs( fromFolder, lsOutput, duLines );

        }

        return fileListing;

    }

    String[] splitDockerTarget( String targetFolder ) {

        int secondSlashIndex = targetFolder.substring( 1 ).indexOf( "/" );

        if ( secondSlashIndex == -1 ) {

            return new String[]{
                    targetFolder, ""
            };

        }

        String containerName = targetFolder.substring( 0, secondSlashIndex + 1 );
        String path = targetFolder.substring( containerName.length( ) );

        logger.debug( "containerName: {} , path: {} ", containerName, path );
        return new String[]{
                containerName, path
        };

    }

    public static String pathWithSpacesEscaped( File filePath ) {

        // return filePath.getAbsolutePath().replaceAll( REPLACE_SPACES, "\\\\ " ) ;
        return "'" + filePath.getAbsolutePath( ) + "'";

    }

    public void addUserReadPermissions( File targetFile ) {

        var readPermissionScriptResult = "";
        var setReadPermissionsScript = csapApis.osManager( ).getOsCommands( ).getFileReadPermissions(
                csapApis.application( ).getAgentRunUser( ),
                targetFile.getAbsolutePath( ) );

        try {

            readPermissionScriptResult = osCommandRunner.runUsingRootUser(
                    "permissions",
                    setReadPermissionsScript );

        } catch ( IOException e ) {

            logger.warn( "Failed running: {}", CSAP.buildCsapStack( e ) );

        }

        readPermissionScriptResult = csapApis.application( ).check_for_stub( readPermissionScriptResult,
                "linux/du-using-root.txt" );

        StringBuilder results = new StringBuilder( "Updating read access using setfacl: " + targetFile
                .getAbsolutePath( ) );

        results.append( "\nCommand: " + setReadPermissionsScript + "\n Result:" + readPermissionScriptResult );

        //
        // After setting read permissions on the file, update parents until read access
        // is available
        //

        var parentFolder = targetFile.getParentFile( );

        logger.debug( "parentFolderL {}, exists: {}, canExecute: {}, read: {}",
                parentFolder.getAbsolutePath( ), parentFolder.exists( ), parentFolder.canExecute( ), parentFolder
                        .canRead( ) );

        // this can be a very large number; limit to avoid recursive locks
        int maxDepth = 10;

        var parentDepth = parentFolder.getPath( ).split( "/" ).length;

        if ( maxDepth < parentDepth ) {

            var message = "Bypassing facls as there are too many folders: " + parentDepth + " max allowed: "
                    + maxDepth;
            results.append( message );
            logger.warn( message );

        } else {

            while ( ( maxDepth-- > 0 )
                    && ( parentFolder != null )
                    && ( !parentFolder.canExecute( ) || !parentFolder.canRead( ) ) ) {

                var parentLines = List.of(
                        "#!/bin/bash",
                        "setfacl -m u:" + csapApis.application( ).getAgentRunUser( ) + ":rx '" + parentFolder
                                .getAbsolutePath( )
                                + "' 2>&1",
                        "" );

                String parentResult = "";

                try {

                    parentResult = osCommandRunner.runUsingRootUser( "permissions", parentLines );

                } catch ( Exception e ) {

                    parentResult = CsapConstants.CONFIG_PARSE_ERROR + "Failed running";

                    logger.warn( "Failed running: {}", CSAP.buildCsapStack( e ) );
                    break;

                }

                parentResult = csapApis.application( ).check_for_stub( parentResult, "linux/du-using-root.txt" );

//					if ( StringUtils.isNotEmpty( parentResult ) ) {
                //
//						logger.warn( "Non empty result when running: {}. Output: \n {}", parentLines, parentResult ) ;
                //
//					}

                results.append( CSAP.buildDescription(
                        "Adding read/execute permissions",
                        "Command", parentLines,
                        "Result", parentResult ) );

                if ( parentResult.contains( "Operation not supported" )
                        || parentResult.contains( CsapConstants.CONFIG_PARSE_ERROR ) ) {

                    logger.warn( "Aborting facl request (suspected nfs folder)" );
                    break;

                }

                parentFolder = parentFolder.getParentFile( );

            }

        }

        csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/permissions",
                csapApis.security( ).getRoles( ).getUserIdFromContext( ),
                "Adding read permissions: " + csapApis.events( ).fileName( targetFile, 50 ),
                results.toString( ) );

        logger.debug( "Result: {}", results );

        return;

    }

    public OsCommandRunner getOsCommandRunner( ) {

        return osCommandRunner;

    }

    public void setOsCommandRunner( OsCommandRunner osCommandRunner ) {

        this.osCommandRunner = osCommandRunner;

    }

    public DateTimeFormatter getFileDateFormatter( ) {

        return fileDateFormatter;

    }

    public void setFileDateFormatter( DateTimeFormatter fileDateFormatter ) {

        this.fileDateFormatter = fileDateFormatter;

    }

    public static List<String> words( String theText ) {
        return List.of( theText.trim( ).split( "\\s+" ) );
    }

    public ObjectNode fileReverseRead(
            File theFile,
            int lineLimit,
            int kbLimit,
            String lineStartFilter,
            int lineWordCountFilter
    ) {

        ObjectNode fileContentReport = jacksonMapper.createObjectNode( );

        try ( var reverseReader = new ReversedLinesFileReader( theFile ) ) {

            var lines = new LinkedList<String>( );

            var bytesRead = 0;
            var bytesLimit = kbLimit * 1024;

            for ( var i = 0 ; i < lineLimit ; i++ ) {

                var line = reverseReader.readLine( );
                if ( line == null ) break;

//				if ( csapApis.application().isJunit() || logger.isDebugEnabled() ) {
//					logger.info( CSAP.buildDescription( "fileReverseRead",
//							"theFile", theFile ,
//							"lineStartFilter", lineStartFilter,
//							"lineLimit", lineLimit,
//							"line words",  words( line ).size(),
//							"lineWordCountFilter", lineWordCountFilter,
//							"line", line) );
//				}

                //
                //  word count matcher
                //
                if ( lineWordCountFilter > 0 ) {
                    if ( StringUtils.isEmpty( line ) || line.startsWith( "#" ) ) continue;
                    var wordsInCurrentLine = words( line ).size( );
                    if ( wordsInCurrentLine != lineWordCountFilter ) {
                        lineLimit++; // increase lines to read as current line is not being added to list.
                        continue;
                    }

                }
                if ( StringUtils.isNotEmpty( lineStartFilter )
                        && !line.startsWith( lineStartFilter ) ) {
                    lineLimit++; // increase lines to read as current line is not being added to list.
                    continue;
                }

                lines.add( line );
                bytesRead += line.length( );

                if ( bytesRead > bytesLimit ) break;

            }

            var lineIterator = lines.descendingIterator( );
            var fileContent = new StringBuilder( bytesRead );

            while ( lineIterator.hasNext( ) ) {

                fileContent.append( lineIterator.next( ) );
                fileContent.append( System.lineSeparator( ) );

            }

            fileContentReport.put( "content", fileContent.toString( ) );

        } catch ( Exception e ) {

            var stack = CSAP.buildCsapStack( e );

            fileContentReport.put( "error", theFile + "\n\n" + stack );

            logger.info( "failed loading {} {}", theFile, stack );

        }

        return fileContentReport;

    }


}
