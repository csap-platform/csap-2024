package org.csap.agent.ui.rest.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.agent.api.AgentApi;
import org.csap.agent.container.C7;
import org.csap.agent.container.ContainerIntegration;
import org.csap.agent.integrations.CsapEvents;
import org.csap.agent.model.Application;
import org.csap.agent.model.ProcessRuntime;
import org.csap.agent.model.ServiceInstance;
import org.csap.docs.CsapDoc;
import org.csap.helpers.CSAP;
import org.csap.security.CsapUser;
import org.csap.security.config.CsapSecurityRoles;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping ( CsapConstants.FILE_URL )
@CsapDoc ( title = "File Monitoring", notes = {
        "File monitoring, and associated rest operations. Includes viewing, saving, editing files",
        "<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
        "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class CsapFileMonitor {
    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    public static final String FILE_CHANGES_URL = "/getFileChanges";

    @Inject
    public CsapFileMonitor(
            CsapApis csapApis,
            ObjectMapper jacksonMapper,
            FileApiUtils fileApiUtils
    ) {

        this.csapApis = csapApis;
        this.jacksonMapper = jacksonMapper;
        this.fileApiUtils = fileApiUtils;

    }

    CsapApis csapApis;
    ObjectMapper jacksonMapper;
    FileApiUtils fileApiUtils;

    @Autowired ( required = false )
    private CsapOauth2SecurityConfiguration csapOauthConfig;

    final public static String FILE_MONITOR = "/FileMonitor";
    final public static String FILE_REMOTE_MONITOR = "/FileRemoteMonitor";

    @GetMapping ( "/remote/listing" )
    @ResponseBody
    public String remoteFileMonitorListing(
            @RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM, required = false ) String serviceName,
            String hostName,
            String containerName,
            String fileName,
            String podName,
            ModelMap modelMap,
            HttpServletRequest request,
            HttpSession session
    ) {

        if ( csapApis.application( ).isAdminProfile( ) ) {

            var requestParameters = new LinkedMultiValueMap<String, String>( );

            if ( serviceName != null ) {

                requestParameters.set( CsapConstants.SERVICE_PORT_PARAM, serviceName );

            } else {

                requestParameters.set( "fileName", fileName );
                requestParameters.set( "podName", podName );

            }

            requestParameters.set( "containerName", containerName );
            requestParameters.set( "apiUser", CsapUser.currentUsersID( ) );

//			if ( ( serviceName != null )
//					&& serviceName.equals( "unregistered" ) ) {
//				requestParameters = new LinkedMultiValueMap<String, String>( ) ;
//				requestParameters.set( "fileName", FileToken.DOCKER.value + "" + containerName ) ;
//			}

            String url = CsapConstants.FILE_URL + FILE_REMOTE_MONITOR;
            List<String> hosts = new ArrayList<>( );
            hosts.add( hostName );

            logger.debug( " hitting: {} with {}", hostName, requestParameters );

            JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                    hosts,
                    url,
                    requestParameters );

            // logger.info( "remoteCall: {}", CSAP.jsonPrint( remoteCall ) );

            var remoteListing = remoteCall.path( hostName ).asText( );

            if ( remoteListing.startsWith( CsapConstants.CONFIG_PARSE_ERROR ) ) {

                remoteListing = "error: failed to get remote listing " + url;
                remoteListing += "\n\n" + CSAP.jsonPrint( remoteCall );

            }

            return remoteListing;

        }

        return " Admin call only";

    }

    @GetMapping ( value = {
            FILE_MONITOR, FILE_REMOTE_MONITOR
    } )
    public String fileMonitor(
            @RequestParam ( value = "fromFolder", required = false ) String fromFolder,
            @RequestParam ( value = "fileName", required = false ) String fileName,
            @RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM, required = false ) String serviceName,
            String podName,
            boolean agentUi,
            String apiUser,
            @RequestParam ( value = "containerName", required = false ) String containerIdOrPodLabel,
            String corsHost,
            ModelMap modelMap,
            HttpServletRequest request
    ) {

        fileApiUtils.setCommonAttributes( modelMap, request, "FileMonitor", apiUser );

        logger.info( CSAP.buildDescription(
                "File Monitor launched",
                "serviceName", serviceName,
                "fileName", fileName,
                "podName", podName,
                "containerIdOrPodLabel", containerIdOrPodLabel ) );

        modelMap.addAttribute( "serviceName", serviceName );
        modelMap.addAttribute( "fromFolder", fromFolder );

        var fileChangeUrl = FILE_CHANGES_URL.substring( 1 );

        if ( csapOauthConfig != null && StringUtils.isNotEmpty( corsHost ) ) {

            fileChangeUrl = "http://" + corsHost + ":" + request.getServerPort( ) + request.getContextPath( )
                    + CsapConstants.FILE_URL
                    + FILE_CHANGES_URL;
            modelMap.addAttribute( "bearerAuth", csapOauthConfig.getAuthorizationHeader( ) );
            logger.info( "bearer: '{}'", csapOauthConfig.getAuthorizationHeader( ) );

        }

        modelMap.addAttribute( "fileChangeUrl", fileChangeUrl );

        String shortName = "tail";

        if ( fileName != null ) {

            shortName = ( new File( fileName ) ).getName( );

        } else if ( serviceName != null ) {

            shortName = "logs " + serviceName;

        }

        modelMap.addAttribute( "shortName", shortName );
        String initialLogFileToShow = "";

        List<String> logFileNames = null;

        ServiceInstance serviceInstance = null;

        if ( StringUtils.isNotEmpty( serviceName ) ) {

            if ( !serviceName.equals( ProcessRuntime.unregistered.getId( ) ) ) {

                serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceName );

                if ( serviceInstance == null ) {

                    logger.warn( "Requested service not found using csap-agent" );
                    serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost(
                            CsapConstants.AGENT_NAME );

                }

                initialLogFileToShow = serviceInstance.getDefaultLogToShow( );

                if ( StringUtils.isNotEmpty( fileName ) ) {

                    initialLogFileToShow = getShortNameFromCsapFilePath( fileName );

                }

                var dockerContainerId = containerIdOrPodLabel;

                if ( serviceInstance.is_cluster_kubernetes( ) ) {

                    if ( StringUtils.isEmpty( containerIdOrPodLabel ) ) {

                        // handle agent dashboard - look up container name
                        dockerContainerId = serviceInstance.findContainerName( serviceName );
                        logger.warn( " podid: {} , container name located using: {}",
                                serviceName,
                                containerIdOrPodLabel );

                    }

                } else if ( StringUtils.isNotEmpty( containerIdOrPodLabel )
                        && containerIdOrPodLabel.equals( "default" ) ) {

                    // occures when docker service is stopped; but it might be there - so use the
                    // default name
                    dockerContainerId = serviceInstance.getDockerContainerPath( );

                }

                logFileNames = build_log_list_for_service( serviceInstance, dockerContainerId );

            } else {

                //
                // Unregistered containers: docker or kubernetes
                //

                fileName = "unregistered-detected";

                logFileNames = new ArrayList<String>( );
                logFileNames.add( fileName );

                initialLogFileToShow = getShortNameFromCsapFilePath( fileName );

            }

        } else if ( StringUtils.isNotEmpty( fileName ) ) {

            // k8 podName means we need to lookup the container name
            if ( StringUtils.isNotEmpty( podName ) ) {

                File containerNamePath = new File( fileName );
                File podNamePath = new File( podName );
                fileName = Application.FileToken.DOCKER.value
                        + csapApis.containerIntegration( ).findDockerContainerId(
                        podNamePath.getName( ),
                        containerNamePath.getName( ) );

            }
            // Use case: Show files in folder selected From file Browser
            // file requested will be inserted at top of list

            initialLogFileToShow = getShortNameFromCsapFilePath( fileName );

            if ( fileName.startsWith( Application.FileToken.DOCKER.value ) ) {
                logFileNames = new ArrayList<String>( );
                logFileNames.add( fileName );

            } else if ( fileName.startsWith( Application.FileToken.JOURNAL.value ) ) {
                // strip off leading / as names shown do not include it
                if ( initialLogFileToShow.length( ) > 2 ) {
                    initialLogFileToShow = initialLogFileToShow.substring( 1 );
                }
                logFileNames = new ArrayList<String>( );

                var systemServices = csapApis.osManager( ).getLinuxServices( );
                for ( var svc : systemServices ) {
                    logFileNames.add( Application.FileToken.JOURNAL.value + "/" + svc.trim( ) );
                }


            } else {

                var metaTimer = csapApis.metrics( ).startTimer( );
                File targetFile = csapApis.application( ).getRequestedFile( fileName, serviceName, false );

                if ( targetFile.getParentFile( ).exists( ) ) {

                    // populate drop down with files in same folder; convenience
                    // for
                    // browsing.

                    logFileNames = new ArrayList<String>( );
                    logFileNames.add( fileName );

                    try ( Stream<Path> pathStream = Files.list( targetFile.getParentFile( ).toPath( ) ) ) {

                        var pathsFromFs = pathStream
                                .filter( Files::isRegularFile )
                                .filter( path -> !path.equals( targetFile.toPath( ) ) )
                                .map( path -> {

                                    return Application.FileToken.ROOT.value + path.toAbsolutePath( ).toString( );

                                } )
                                .collect( Collectors.toList( ) );

                        logFileNames.addAll( pathsFromFs );

                    } catch ( Exception e ) {

                        logger.warn( "Failed to get file listing: ", CSAP.buildCsapStack( e ) );

                    }

                }

                var metaNanos = csapApis.metrics( ).stopTimer( metaTimer, "os.monitor-file-listing" );
                var metaMs = TimeUnit.NANOSECONDS.toMillis( metaNanos );

                if ( metaMs > 500 ) {

                    logger.warn( "Slow Reads detected while listing {} metaRead: {}ms ",
                            targetFile, metaMs );

                }

            }

        }

        String firstNonZipFile = null;
        boolean foundInitialDisplay = false;

        if ( logFileNames == null || logFileNames.size( ) == 0 ) {

            logger.error( "Failed to find any matching log files: '{}'", fileName );

        } else {

            Map<String, String> logFileMap = new TreeMap<>( );
            Map<String, String> serviceJobMap = new TreeMap<>( );
            Map<String, String> journalMap = new TreeMap<>( );
            Map<String, String> csapDeployMap = new TreeMap<>( );
            Map<String, String> configMap = new TreeMap<>( );

            for ( var fullFilePath : logFileNames ) {

                var label = getShortNameFromCsapFilePath( fullFilePath );

                try {

                    if ( firstNonZipFile == null
                            && !label.endsWith( ".gz" )
                            && !label.startsWith( "logRotate" )
                            && !label.endsWith( ".pid" ) ) {

                        firstNonZipFile = label;

                    }

                    if ( label.endsWith( initialLogFileToShow ) ) {

                        foundInitialDisplay = true;

                    }

                    if ( fullFilePath.startsWith( Application.FileToken.JOURNAL.value ) ) {

                        journalMap.put( label.substring( 1 ), fullFilePath );

                    } else if ( fullFilePath.startsWith( Application.FileToken.WORKING.value ) ) {

                        csapDeployMap.put( label.substring( 1 ), fullFilePath );

                    } else if ( label.startsWith( "serviceJobs" )
                            && label.length( ) > ( "serviceJobs".length( ) + 1 ) ) {

                        logger.info( "label: {} size: {}", label, label.length( ) );

                        serviceJobMap.put( label.substring( "serviceJobs".length( ) + 1 ), fullFilePath );

                    } else if ( label.startsWith( "logRotate" ) || label.endsWith( ".pid" ) ) {

                        configMap.put( label, fullFilePath );

                    } else if ( agentUi
                            && fullFilePath.equals( "kubernetes-pods-detected" ) ) {

                        var podContainer = serviceInstance.findPodContainer( serviceName );
                        logger.info( "Locating container for {} : {}", serviceName, podContainer );

                        if ( podContainer.isPresent( ) ) {

                            // tight coupling to agent-logs.js
                            var pod = podContainer.get( );

                            if ( StringUtils.isNotEmpty( pod.getPodName( ) ) ) {

                                modelMap.addAttribute( "podContainer", pod );
                                var podNameFields = pod.getPodName( ).split( "-" );
                                var csapContainerLabel = pod.getContainerLabel( ) + " ("
                                        + podNameFields[ podNameFields.length - 1 ] + ")";
                                modelMap.addAttribute( "csapContainerLabel", csapContainerLabel );
                                modelMap.addAttribute( "container", pod.getContainerLabel( ) );
                                modelMap.addAttribute( "pod", pod.getPodName( ) );
                                modelMap.addAttribute( "namespace", pod.getPodNamespace( ) );

                            } else {

                                logger.info( "podName not set - master?" );

                            }

                        }

                        logFileMap.put( label, fullFilePath );

                    } else {

                        logFileMap.put( label, fullFilePath );

                    }

                } catch ( Exception e ) {

                    logger.warn( "Failed getting listing fullFilePath: {} label: {} {}",
                            fullFilePath,
                            label,
                            CSAP.buildCsapStack( e ) );

                }

            }

            ;

            if ( !logFileMap.isEmpty( ) )
                modelMap.addAttribute( "logFileMap", logFileMap );

            if ( !serviceJobMap.isEmpty( ) ) {

                modelMap.addAttribute( "serviceJobMap", serviceJobMap );

            } else if ( serviceInstance != null ) {

                // support for working dir for NON relative paths
                File jobLogDir = new File( csapApis.application( ).getWorkingLogDir( serviceInstance
                        .getServiceName_Port( ) ),
                        "serviceJobs" );

                if ( jobLogDir.exists( ) ) {

                    File[] jobItems = jobLogDir.listFiles( );

                    if ( jobItems != null ) {

                        for ( File jobItem : jobItems ) {

                            if ( jobItem.isFile( ) ) {

//								var fullFilePath = Application.FileToken.ROOT.value + "/" + jobItem.getPath( ) ;
                                var fullFilePath = Application.FileToken.ROOT.value + jobItem.getPath( );
                                var label = getShortNameFromCsapFilePath( fullFilePath );
                                serviceJobMap.put( label.substring( "serviceJobs".length( ) + 1 ), fullFilePath );

                            }

                        }

                    }

                }

                if ( !serviceJobMap.isEmpty( ) ) {

                    modelMap.addAttribute( "serviceJobMap", serviceJobMap );

                }

            }

            if ( !journalMap.isEmpty( ) )
                modelMap.addAttribute( "journalMap", journalMap );

            if ( !csapDeployMap.isEmpty( ) )
                modelMap.addAttribute( "csapDeployMap", csapDeployMap );

            if ( !configMap.isEmpty( ) )
                modelMap.addAttribute( "configMap", configMap );

            // This is used to select file in UI
        }

        if ( !foundInitialDisplay && firstNonZipFile != null ) {

            initialLogFileToShow = firstNonZipFile;

        }

        modelMap.addAttribute( "initialLogFileToShow", initialLogFileToShow );

        logger.info( "initialLogFileToShow: '{}', firstNonZipFile: {}", initialLogFileToShow, firstNonZipFile );

        return CsapConstants.FILE_URL + "/file-monitor";

    }

    private List<String> build_log_list_for_service(
            ServiceInstance serviceInstance,
            String containerName
    ) {

        var logFileNames = new ArrayList<String>( );

        var serviceWorkingFolder = csapApis.application( ).getCsapWorkingFolder( );

        if ( serviceWorkingFolder.exists( ) ) {

            var serviceWorkingFiles = serviceWorkingFolder.listFiles( );

            if ( serviceWorkingFiles != null ) {

                for ( var serviceWorkingFile : serviceWorkingFiles ) {

                    if ( serviceWorkingFile.isFile( )
                            && serviceWorkingFile.getName( ).startsWith( serviceInstance.getName( ) )
                            && serviceWorkingFile.getName( ).endsWith( ".log" ) ) {

                        logFileNames.add( Application.FileToken.WORKING.value + "/" + serviceWorkingFile.getName( ) );

                    }

                }

            }

        } else {

            logger.warn( "{} working folder does not exist: {}", serviceInstance.getName( ), serviceWorkingFolder );

        }

//		File serviceFolder =  csapApis.application().getLogDir( serviceInstance.getServiceName_Port( ) ) ;
        var serviceLogFolder = serviceInstance.getLogWorkingDirectory( );
        var csapWorkingLogFolderForContainers = new File( serviceInstance.getWorkingDirectory( ), serviceInstance.getLogDirectory( ) );

        if ( serviceInstance.isKubernetesMaster( ) ) {

            serviceLogFolder = serviceInstance.getWorkingDirectory( );

        }

        var useContainerForLogs = ( csapApis.containerIntegration( ) != null )
                && csapApis.containerIntegration( ).is_docker_logging( serviceInstance );

        var logPath = serviceInstance.getLogDirectory( );

        if ( logPath.startsWith( FileApiUtils.NAMESPACE_PVC_TOKEN ) ) {

            useContainerForLogs = false;
            var logListingPath = fileApiUtils.buildNamespaceListingPath( serviceInstance, logPath );
            logger.info( "logListingPath: {}", logListingPath );
            serviceLogFolder = new File( logListingPath );

        }

        logger.info( CSAP.buildDescription(
                "File Listing",
                "service", serviceInstance,
                "serviceLogFolder", serviceLogFolder,
                "serviceWorkingLogFolder", csapWorkingLogFolderForContainers,
                "logDirectory", serviceInstance.getLogDirectory( ),
                "logPath", logPath,
                "getLogRegEx", serviceInstance.getLogRegEx( ),
                "useContainerForLogs", useContainerForLogs,
                "defaultLog", csapApis.application( ).getDefaultLogFileName( serviceInstance
                        .getServiceName_Port( ) ) ) );

        if ( useContainerForLogs ) {

            String baseDocker = Application.FileToken.DOCKER.value + containerName;

            if ( StringUtils.isEmpty( containerName ) ) {

                // legacy
                baseDocker = Application.FileToken.DOCKER.value
                        + csapApis.containerIntegration( ).determineDockerContainerName( serviceInstance );

            }

            if ( serviceInstance.is_cluster_kubernetes( ) ) {

                logFileNames.add( "kubernetes-pods-detected" );

            } else {

                logFileNames.add( baseDocker );

            }

            String dockerLogDir = "/var/log";

            if ( !serviceInstance.getLogDirectory( ).equals( "logs" ) ) {

                dockerLogDir = serviceInstance.getLogDirectory( );

            }

            String baseDockerLogs = baseDocker + dockerLogDir + "/";

            var fileListing = fileApiUtils.buildListingUsingDocker(
                    baseDockerLogs.substring( Application.FileToken.DOCKER.value.length( ) ),
                    new HashMap<String, String>( ),
                    baseDockerLogs );

            List<String> subFileNames = new ArrayList<String>( );
            CsapConstants.jsonStream( fileListing ).forEach( logListing -> {

                logger.debug( "logListing: {}", logListing );

                if ( ( !logListing.has( "folder" ) ) && logListing.has( "location" ) ) {

                    String location = logListing.get( "location" ).asText( );

                    if ( !location.contains( ContainerIntegration.MISSING_FILE_NAME ) ) {

                        subFileNames.add( location );

                    }

                }

            } );
            logFileNames.addAll( subFileNames );

        }

        //
        // Docker and kubernetes: check for reports or other csap created files
        //
        if ( csapWorkingLogFolderForContainers.exists( )
                && useContainerForLogs ) {

            logger.info( "scanning {}", csapWorkingLogFolderForContainers );
            if ( csapWorkingLogFolderForContainers.canRead( ) ) {

                addFiles( serviceInstance, logFileNames, csapWorkingLogFolderForContainers );

                addNestedFilesFromSubFolders(
                        serviceInstance,
                        logFileNames,
                        csapWorkingLogFolderForContainers );

                logger.info( "supplemental container scan for deployment files: {}, logFileNames: {}",
                        csapWorkingLogFolderForContainers,
                        logFileNames );
            }

        }

        var logFilesWithRootSupport = new ArrayList<String>( );

        fileApiUtils.buildLogFiles(
                serviceLogFolder,
                serviceInstance,
                logFilesWithRootSupport,
                null );

        logger.debug( "logFilesWithRootSupport: {}", logFilesWithRootSupport );

        if ( serviceLogFolder.exists( )
                || logFilesWithRootSupport.size( ) > 0 ) {

//			var serviceFolderLogListings = serviceLogFolder.listFiles( ) ;
            var logFileNamesInSubDir = new ArrayList<String>( );

            if ( !serviceLogFolder.canRead( ) ) {

                // might be a root listing
                logFileNamesInSubDir.addAll( logFilesWithRootSupport );

            } else {

                addFiles( serviceInstance, logFileNames, serviceLogFolder );

                //
                // Leave subfolder search disabled?
                //

                logger.info( CSAP.buildDescription( "checking for ",
                        "serviceLogFolder", serviceLogFolder.getAbsolutePath( ),
                        "csapInstallFolder", csapApis.application( ).getCsapInstallFolder( ).getAbsolutePath( ) ) );

                if ( serviceLogFolder.getAbsolutePath( ).contains(
                        csapApis.application( ).getCsapInstallFolder( ).getAbsolutePath( ) )
                        || csapApis.application( ).isDesktopHost( ) ) {

                    addNestedFilesFromSubFolders( serviceInstance, logFileNames, serviceLogFolder );

                }


            }

            if ( logFileNamesInSubDir.size( ) != 0 ) {

                logFileNames.addAll( logFileNamesInSubDir );

            }

        } else {

            logger.warn( "{} working folder does not exist: {}", serviceInstance.getName( ), serviceWorkingFolder );

        }

        if ( StringUtils.isNotEmpty( serviceInstance.getLogJournalServices( ) ) ) {

            String[] systemServices = serviceInstance.getLogJournalServices( ).split( "," );

            for ( String svc : systemServices ) {

                logFileNames.add( Application.FileToken.JOURNAL.value + "/" + svc.trim( ) );

            }


            logger.info("Adding all services for linux package: {}", serviceInstance.getName() ) ;
            if ( serviceInstance.getName().equals( "csap-package-linux" )) {
                var allSystemServices = csapApis.osManager( ).getLinuxServices( );
                for ( var svc : allSystemServices ) {
                    logFileNames.add( Application.FileToken.JOURNAL.value + "/" + svc.trim( ) );
                }
            }

        }

        if ( logFileNames.size( ) == 0 ) {

            logger.error( "Failed to find any matching log files: " + serviceLogFolder.getAbsolutePath( )
                    + " \n Processing: " + csapApis.application( ).getCsapWorkingFolder( ).getAbsolutePath( ) );

        }

        logger.debug( "logFileNames: {}", logFileNames );

        return logFileNames;

    }

    private void addFiles(
            ServiceInstance serviceInstance,
            ArrayList<String> logFileNames,
            File folderToScan
    ) {
        try ( Stream<Path> pathStream = Files.list( folderToScan.toPath( ) ) ) {

            var pathsFromFs = pathStream
//						.filter( Files::isRegularFile )
                    .filter( path -> {
                        return !Files.isDirectory( path );
                    } )
                    .map( path -> path.toAbsolutePath( ).toString( ) )
                    .filter( pathString -> pathString.matches( serviceInstance.getLogRegEx( ) ) )
                    .map( pathString -> {

                        return Application.FileToken.ROOT.value + pathString;

                    } )
                    .collect( Collectors.toList( ) );

            logger.info( "folderToScan {}, found: {}", folderToScan, pathsFromFs );
            logFileNames.addAll( pathsFromFs );
        } catch ( Exception e ) {

            logger.warn( "Failed to get file listing: ", CSAP.buildCsapStack( e ) );

        }
    }

    private void addNestedFilesFromSubFolders(
            ServiceInstance serviceInstance,
            ArrayList<String> logFileNames,
            File folderToScan
    ) {

        if ( folderToScan.exists( ) ) {

            logger.info( "Scanning subfolders for files: {}", folderToScan );
            try ( Stream<Path> subPathStream = Files.list( folderToScan.toPath( ) ) ) {

                subPathStream
                        .filter( Files::isDirectory )
                        .forEach( path -> {


                            fileApiUtils.buildLogFiles(
                                    path.toFile( ),
                                    serviceInstance,
                                    logFileNames,
                                    null );

                        } );


            } catch ( Exception e ) {

                logger.warn( "Failed to get file listing: ", CSAP.buildCsapStack( e ) );

            }
        }
    }

    private String getShortNameFromCsapFilePath( String logFileName ) {

        String shortName = logFileName;

        for ( Application.FileToken token : Application.FileToken.values( ) ) {

            if ( logFileName.startsWith( token.value ) ) {

                shortName = logFileName.substring( token.value.length( ) );
                break;

            }

        }

        String endName = shortName;

        // hook to shorten name
        if ( StringUtils.countMatches( shortName, "/" ) > 2 ) {

            endName = shortName.substring( 0, shortName.lastIndexOf( "/" ) );
            endName = shortName.substring( endName.lastIndexOf( "/" ) + 1 );

        }

        // windows
        if ( StringUtils.countMatches( shortName, "\\" ) > 2 ) {

            endName = shortName.substring( 0, shortName.lastIndexOf( "\\" ) );
            endName = shortName.substring( endName.lastIndexOf( "\\" ) + 1 );

        }

        if ( endName.startsWith( "logs" ) ) {

            endName = endName.substring( 5 );

        }

        return endName;

    }

    public final static String LAST_LINE_SESSION = "lastLineInSession";

    public final static String LOG_FILE_OFFSET_PARAM = "logFileOffset";
    public final static String LOG_SELECT_PARAM = "logSelect";
    public final static String PROP_SELECT_PARAM = "propSelect";

    final static String EOL = System.getProperty( "line.separator" );

    final static int EOL_SIZE_BYTES = EOL.length( );

    // final static int DEFAULT_TAIL = 1024 * 50;

    public final static int NUM_BYTES_TO_READ = 1024; // 5k/time
    public final static String PROGRESS_TOKEN = "*Progress:";
    public final static String OFFSET_TOKEN = "*Offset:";

    /**
     * Key Use Cases: 1) Used to tail log files on FileMonitor UI 2) Used on MANY ui
     * s to tail results of commands while they are running. This provides feedback
     * to users
     *
     * @param fromFolder
     * @param bufferSize
     * @param hostNameArray
     * @param serviceName_port
     * @param isLogFile
     * @param offsetLong
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping ( value = {
            FILE_CHANGES_URL
    }, produces = MediaType.APPLICATION_JSON_VALUE )
    @ResponseBody
    public JsonNode getFileChanges(
            @RequestParam ( value = "fromFolder", required = false ) String fromFolder,
            @RequestParam ( defaultValue = "0" ) int dockerLineCount,
            @RequestParam ( defaultValue = "0" ) String dockerSince,
            @RequestParam ( value = "bufferSize", required = true ) long bufferSize,
            @RequestParam ( value = CsapConstants.HOST_PARAM, required = false ) String hostName,
            @RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM, required = false ) String serviceName_port,
            @RequestParam ( value = "isLogFile", required = false, defaultValue = "false" ) boolean isLogFile,
            @RequestParam ( value = LOG_FILE_OFFSET_PARAM, required = false, defaultValue = "-1" ) long offsetLong,
            boolean useLocal,
            String apiUser,
            HttpServletRequest request,
            HttpSession session
    )
            throws IOException {

        if ( hostName == null ) {

            hostName = csapApis.application( ).getCsapHostName( );

        }

        var file_being_tailed = csapApis.application( ).getRequestedFile( fromFolder, serviceName_port, isLogFile );
        var userName = CsapUser.currentUsersID( );

        // debug only && ! csapApis.application().isDesktopHost()
        if ( csapApis.application( ).isAdminProfile( ) && !useLocal ) {

            MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

            urlVariables.set( "apiUser", userName );
            urlVariables.set( "fromFolder", fromFolder );
            urlVariables.set( "dockerLineCount", Integer.toString( dockerLineCount ) );
            urlVariables.set( "dockerSince", dockerSince );
            urlVariables.set( "bufferSize", Long.toString( bufferSize ) );
            urlVariables.set( CsapConstants.HOST_PARAM, hostName );
            urlVariables.set( "isLogFile", Boolean.toString( isLogFile ) );
            urlVariables.set( "fromFolder", fromFolder );
            urlVariables.set( CsapConstants.SERVICE_NOPORT_PARAM, serviceName_port );
            urlVariables.set( CsapFileMonitor.LOG_FILE_OFFSET_PARAM, Long.toString( offsetLong ) );

            String url = CsapConstants.API_AGENT_URL + AgentApi.LOG_CHANGES;
            var hosts = List.of( hostName );

            JsonNode changesFromRemoteAgentApi = csapApis.osManager( ).getServiceManager( )

                    .remoteAgentsApi(
                            csapApis.application( ).rootProjectEnvSettings( ).getAgentUser( ),
                            csapApis.application( ).rootProjectEnvSettings( ).getAgentPass( ),
                            hosts,
                            url,
                            urlVariables )

                    .get( hostName );

            if ( changesFromRemoteAgentApi.isTextual( )
                    && ( changesFromRemoteAgentApi.asText( ).startsWith( CsapConstants.CONFIG_PARSE_ERROR )
                    || changesFromRemoteAgentApi.asText( ).startsWith( "Skipping host" ) ) ) {

                var errorReport = jacksonMapper.createObjectNode( );

                errorReport.put( "error", "Failed to collect changes from remote agent" );
                errorReport.put( "reason", changesFromRemoteAgentApi.asText( ) );

                changesFromRemoteAgentApi = errorReport;

            }

            return changesFromRemoteAgentApi;

        }

        if ( StringUtils.isNotEmpty( apiUser ) ) {

            userName = apiUser;

        }

        fileApiUtils.auditTrail( userName, file_being_tailed, "tail" );

        if ( session == null ) {

            // access logged via client api

        } else {

            if ( !csapApis.security( )
                    .getRoles( )
                    .getAndStoreUserRoles( session, null )
                    .contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

                if ( !file_being_tailed
                        .getCanonicalPath( )
                        .startsWith(
                                csapApis.application( ).getCsapWorkingFolder( ).getCanonicalPath( ) ) ) {

                    logger.warn(
                            "Attempt to access file system: {}. Only {} is permitted. Check if {}  is bypassing security: ",
                            file_being_tailed.getCanonicalPath( ),
                            csapApis.application( ).getCsapWorkingFolder( ).getCanonicalPath( ),
                            CsapUser.currentUsersID( ) );

                    ObjectNode errorResponse = jacksonMapper.createObjectNode( );
                    errorResponse
                            .put( "error", "*** Content protected: can be accessed by admins " + fromFolder );
                    return errorResponse;

                }

            }

            // Only allow infra admin to view security files.
            if ( !csapApis.security( )
                    .getRoles( )
                    .getAndStoreUserRoles( session, null )
                    .contains( CsapSecurityRoles.INFRA_ROLE ) ) {

                // @formatter:on
                // run secondary check
                if ( fileApiUtils.isInfraOnlyFile( file_being_tailed ) ) {

                    logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security.", fromFolder,
                            CsapUser.currentUsersID( ) );
                    ObjectNode errorResponse = jacksonMapper.createObjectNode( );
                    errorResponse
                            .put( "error", "*** Content masked: can be accessed by infra admins " + fromFolder );
                    return errorResponse;

                }

            }

            // generate audit records as needed
            @SuppressWarnings ( "unchecked" )
            ArrayList<String> fileList = ( ArrayList<String> ) request.getSession( )
                    .getAttribute( "FileAcess" );

            if ( CsapConstants.HOST_PARAM == null
                    && ( fileList == null || !fileList.contains( file_being_tailed.getAbsolutePath( ) ) ) ) {

                if ( fileList == null ) {

                    fileList = new ArrayList<String>( );
                    request.getSession( ).setAttribute( "FileAcess", fileList );

                }

                fileList.add( file_being_tailed.getAbsolutePath( ) );

                csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/tail",
                        CsapUser.currentUsersID( ),
                        csapApis.events( ).fileName( file_being_tailed, 100 ),
                        file_being_tailed.getAbsolutePath( ) );

            }

        }

        if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

            return tailUsingDocker( fromFolder, dockerLineCount, dockerSince );

        } else if ( fromFolder.startsWith( Application.FileToken.JOURNAL.value ) ) {

            return tailUsingJournal( fromFolder, dockerLineCount, dockerSince );

        } else {

            if ( file_being_tailed == null || !file_being_tailed.isFile( ) || !file_being_tailed.canRead( ) ) {

                fileApiUtils.addUserReadPermissions( file_being_tailed );

            }

            return readFileChanges( bufferSize, offsetLong, file_being_tailed );

        }

    }

    private ObjectNode tailUsingJournal(
            String fromFolder,
            int numberOfLines,
            String dockerSince
    ) {

        ObjectNode fileChangesJson = jacksonMapper.createObjectNode( );

        if ( numberOfLines == 0 ) {

            numberOfLines = 50;

        }

        fileChangesJson.put( "source", "journalctl" );
        var contentsJson = fileChangesJson.putArray( "contents" );

        var journalService = fromFolder.substring( Application.FileToken.JOURNAL.value.length( ) + 1 );

        try {

            String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) );
            fileChangesJson.put( "since", now );

            String since = "";

            if ( !dockerSince.equals( "0" ) && !dockerSince.equals( "-1" ) ) {

                since = dockerSince;

            }

            String journalResults = csapApis.osManager( ).getJournal(
                    journalService, since,
                    Integer.toString( numberOfLines ),
                    false, false );

            if ( !journalResults.contains( "-- No entries --" ) ) {

                contentsJson.add( journalResults );

                // String[] tailLines = journalResults.split( "\n" );
                // for ( String line : tailLines ) {
                // contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
                // }
            }

        } catch ( Exception e ) {

            logger.error( "Failed tailing file: {}", CSAP.buildCsapStack( e ) );

        }

        return fileChangesJson;

    }

    private ObjectNode tailUsingDocker(
            String fromFolder,
            int numberOfLines,
            String dockerSince
    ) {

        var fileChangeReport = jacksonMapper.createObjectNode( );

        if ( numberOfLines == 0 ) {

            numberOfLines = 50;

        }

        fileChangeReport.put( "source", C7.definitionSettings.val( ) );

        var latestSummary = csapApis.containerIntegration( ).getCachedSummaryReport( );
        if ( latestSummary != null ) {
            fileChangeReport.put( "containerVersion", latestSummary.path( "version" ) );
        } else {
            fileChangeReport.put( "containerVersion", "not-found" );
        }


        ArrayNode contentsJson = fileChangeReport.putArray( "contents" );

        String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) );
        String[] dockerContainerAndPath = fileApiUtils.splitDockerTarget( dockerTarget );

        try {

            if ( dockerContainerAndPath[ 1 ] == null || dockerContainerAndPath[ 1 ].trim( ).length( ) == 0 ) {

                // show container logs
                ObjectNode tailResult = csapApis.containerIntegration( ).containerTail(
                        null,
                        dockerContainerAndPath[ 0 ],
                        numberOfLines,
                        Integer.parseInt( dockerSince ) );

                fileChangeReport.put( "since", tailResult.get( "since" ).asInt( ) );

                String logsAsText = tailResult.get( "plainText" ).asText( );

                if ( logsAsText.length( ) > 0 ) {

                    contentsJson.add( logsAsText );

                    // for ( String line : logsAsText.split( "\n" ) ) {
                    // contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
                    // }
                }

            } else {

                // tail on docker file
                String logsAsText = csapApis.containerIntegration( )
                        .tailFile(
                                dockerContainerAndPath[ 0 ],
                                dockerContainerAndPath[ 1 ],
                                numberOfLines );

                fileChangeReport.put( "since", -1 );

                if ( logsAsText.length( ) > 0 ) {

                    contentsJson.add( logsAsText );

                    // for ( String line : logsAsText.split( "\n" ) ) {
                    // contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
                    // }
                }

            }

        } catch ( Exception e ) {

            logger.error( "Failed tailing file: {}", CSAP.buildCsapStack( e ) );

        }

        return fileChangeReport;

    }

    public ObjectNode readFileChanges(
            long bufferSize,
            long offsetLong,
            File targetFile
    )
            throws IOException,
            FileNotFoundException {

        ObjectNode fileChangesJson = jacksonMapper.createObjectNode( );

        fileChangesJson.put( "source", "java" );
        // getTail(targetFile, offsetLong, dirBuf, response);
        ArrayNode contentsJson = fileChangesJson.putArray( "contents" );

        // || targetFile.getAbsolutePath().contains( "banner" )
        if ( targetFile == null || !targetFile.isFile( ) || !targetFile.canRead( ) ) {

            // UI is handling...
            logger.debug( "File not accessible: " + targetFile.getAbsolutePath( ) );
            fileChangesJson
                    .put( "error",
                            "Warning: File does not exist or permission to read is denied. Try the root tail option or select another file\n" );

            return fileChangesJson;

        }

        // try with resource
        try ( RandomAccessFile randomAccessFile = new RandomAccessFile(
                targetFile, "r" ) ; ) {

            long fileLengthInBytes = randomAccessFile.length( );

            Long lastPosition = offsetLong;

            if ( lastPosition.longValue( ) == -1 ) { // -1 means default tail

                // size,
                // -2
                // is show whole file

                if ( fileLengthInBytes < bufferSize ) {

                    lastPosition = Long.valueOf( 0 ); // start from the start of the
                    // file

                } else {

                    lastPosition = Long.valueOf( fileLengthInBytes - bufferSize ); // new Long( fileLengthInBytes - bufferSize ) ;

                }

            } else if ( lastPosition.longValue( ) == -2 ) { // show whole file

                // without
                // chunking
                lastPosition = Long.valueOf( 0 );

            }

            // file may have been rolled by agent - emptying it , compressing
            // contents.
            if ( lastPosition.longValue( ) > fileLengthInBytes ) {

                contentsJson.add( "FILE ROLLED\n" );

                if ( fileLengthInBytes < bufferSize ) {

                    lastPosition = Long.valueOf( 0 ); // start from the start of the
                    // file

                } else {

                    lastPosition = Long.valueOf( fileLengthInBytes - bufferSize );

                }

            }

            // Progress info is displayed on 1st line
            if ( offsetLong != -2 ) {

                fileChangesJson.put( "lastPosition", lastPosition.longValue( ) );

            }

            if ( offsetLong != -2 ) {

                fileChangesJson.put( "fileLength", randomAccessFile.length( ) );

            }

            // printWriter.print(PROGRESS_TOKEN + " " + lastPosition.longValue()
            // / 1024 + " of " + randomAccessFile.length() / 1024 + " Kb\n");
            // log.info("fileName: " + fileName + " Raf length of file:" +
            // currLength + " Offset:" + lastPosition) ;
            long currPosition = lastPosition.longValue( );
            byte[] bufferAsByteArray = new byte[ NUM_BYTES_TO_READ ];
            int numBytes = NUM_BYTES_TO_READ;

            // as the files roll
            String stringReadIn = null;
            randomAccessFile.seek( currPosition ); // this goes to the byte
            // before
            int numBytesSent = 0;

            while ( currPosition < fileLengthInBytes ) {

                if ( ( fileLengthInBytes - currPosition ) < NUM_BYTES_TO_READ ) {

                    long numBytesLong = fileLengthInBytes - currPosition;
                    numBytes = ( Long.valueOf( numBytesLong ) ).intValue( );

                }

                randomAccessFile.read( bufferAsByteArray, 0, numBytes );

                stringReadIn = new String( bufferAsByteArray, 0, numBytes );
                // System.out.print(" ---- read in" + stringReadIn +
                // " at offset: "
                // + currPosition) ;

                currPosition += numBytes;
                // we stream in the data, to keep server side as lean as
                // possible
                // StringEscapeUtils.escapeHtml4(
                contentsJson.add( stringReadIn );
                // sbuf.append(stringReadIn);

                numBytesSent += numBytes; // send on

                if ( numBytesSent > fileApiUtils.CHUNK_SIZE_PER_REQUEST - 1 ) {

                    break; // only send back limited at a time for

                } // responsiveness

            }

            long newOffset = lastPosition.longValue( ) + numBytesSent;
            // printWriter.print("\n" + OFFSET_TOKEN + " " + newOffset +
            // " Total: "
            // + currLength);

            long numChunks = ( fileLengthInBytes / fileApiUtils.CHUNK_SIZE_PER_REQUEST ) + 1;

            fileChangesJson.put( "numChunks", numChunks );
            fileChangesJson.put( "newOffset", newOffset );
            fileChangesJson.put( "currLength", fileLengthInBytes );

        }

        return fileChangesJson;

    }

}
