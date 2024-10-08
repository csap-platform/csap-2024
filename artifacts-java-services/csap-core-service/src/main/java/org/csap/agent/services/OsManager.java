package org.csap.agent.services;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.agent.container.C7;
import org.csap.agent.container.ContainerIntegration;
import org.csap.agent.container.ContainerProcess;
import org.csap.agent.container.kubernetes.KubernetesIntegration;
import org.csap.agent.integrations.CsapEvents;
import org.csap.agent.linux.*;
import org.csap.agent.model.Application;
import org.csap.agent.model.ContainerState;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.stats.*;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.helpers.CsapSimpleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@EnableConfigurationProperties( OsCommands.class )
public class OsManager {

    public static final String CRIO_DELIMETER = "crio---";

    @Lazy
    @Autowired
    ServiceOsManager serviceManager;

    private OsCommands osCommands;

    public static final String IO_UTIL_IN_PERCENT = "ioUtilInPercent";
    public static final String SWAP = "swap";
    public static final String RAM = "ram";
    public static final String BUFFER = "buffer";

    public static final String COLLECT_OS = "collect-os.";

    final Logger logger = LoggerFactory.getLogger( this.getClass( ) );

    private ObjectMapper jsonMapper = new ObjectMapper( );

    OsCommandRunner osCommandRunner = new OsCommandRunner( 120, 3, "OsMgr" );
    OsCommandRunner kuberernetesRunner = new OsCommandRunner( 60, 1, "kuberernetesRunner" );

    LogRollerRunnable logRoller = null;
    ServiceJobRunner jobRunner = null;
    InfrastructureRunner infraRunner = null;

    OsProcessMapper processMapper;

    @Autowired
    public OsManager( OsCommands osCommands ) {

        this.osCommands = osCommands;

        processMapper = new OsProcessMapper( );

    }

    public void buildAndWriteZip(
            HttpServletResponse response,
            File source
    )
            throws IOException {

        File workingFolder = CsapApis.getInstance( ).application( ).csapPlatformTemp( );

        if ( !workingFolder.exists( ) ) {

            workingFolder.mkdirs( );

        }

        File fileName = new File( workingFolder, source.getName( ) + ".zip" );
        File zipLocation = new File( workingFolder, fileName.getName( ) );

        if ( source.exists( ) && source.getName( ).endsWith( ".zip" ) ) {

            logger.info( CSAP.buildDescription( "existing zip file for download",
                    "source", source.getAbsolutePath( ) ) );
            zipLocation = source; // existing zip file passed in

        } else {

            logger.info( CSAP.buildDescription( "Building zip file for download",
                    "source", source.getAbsolutePath( ),
                    "location", zipLocation.getAbsolutePath( ) ) );

            if ( !source.exists( ) ) {

                logger.debug( "Zip does not exist" );
                response.setStatus( HttpServletResponse.SC_BAD_REQUEST );
                response.getWriter( ).println( HttpServletResponse.SC_BAD_REQUEST + ": BAD REQUEST" );
                return;

            }

            if ( source.isDirectory( ) ) {

                ZipUtility.zipDirectory( source, zipLocation );

            } else {

                ZipUtility.zipFile( source, zipLocation );

            }

        }

        // response.setContentType("application/octet-stream");
        response.setContentType( MediaType.APPLICATION_OCTET_STREAM_VALUE );
        response.setContentLength( ( int ) zipLocation.length( ) );
        response.setHeader( "Content-Disposition", "attachment; filename=\"" + zipLocation.getName( )
                + "\"" );

        try ( DataInputStream in = new DataInputStream( new FileInputStream(
                zipLocation.getAbsolutePath( ) ) );
              ServletOutputStream op = response.getOutputStream( ); ) {

            byte[] bbuf = new byte[ 3000 ];

            int numBytesRead;
            long startingMax = zipLocation.length( );
            long totalBytesRead = 0L; // hook for files that are being updated

            while ( (in != null) && ((numBytesRead = in.read( bbuf )) != -1)
                    && (startingMax > totalBytesRead) ) {

                totalBytesRead += numBytesRead;
                op.write( bbuf, 0, numBytesRead );

            }

        } catch ( FileNotFoundException e ) {

            logger.error( "File not found", e );
            response.getWriter( )
                    .println( "Did not find file: " + zipLocation );

        }

    }

    public void startAgentResourceCollectors( ) {

        StringBuilder startInfo = new StringBuilder( "Resource Collectors" );

        int topSeconds = getTopIntervalSeconds( );
        topStatsRunnable = new TopRunnable( topSeconds, osCommands.getSystemProcessMetrics( topSeconds ) );
        startInfo.append( CSAP.padLine( "linux top" ) + topSeconds + " seconds" );

        logRoller = new LogRollerRunnable( CsapApis.getInstance( ) );
        startInfo.append( CSAP.padLine( "linux logrotate" ) + CsapApis.getInstance( ).application( )
                .rootProjectEnvSettings( ).getLogRotationMinutes( )
                + " minutes" );

        if ( jobRunner == null ) {

            jobRunner = new ServiceJobRunner( CsapApis.getInstance( ) );
            startInfo.append( CSAP.padLine( "csap jobrunner" ) + "60 minutes" );

        }

        infraRunner = new InfrastructureRunner( CsapApis.getInstance( ) );

        //
        var diskCollectionMinutes = CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getDuIntervalMins( );

        if ( diskCollectionMinutes > 0 ) {

            var initialDelay = 1;
            startInfo.append( CSAP.padLine( "os: disk, network, services" ) + diskCollectionMinutes + " minutes" );
            intenseOsCommandExecutor.scheduleWithFixedDelay(
                    ( ) -> collect_disk_and_linux_package( ),
                    initialDelay, diskCollectionMinutes, TimeUnit.MINUTES ); // initial,and interval

        } else {

            startInfo.append( CSAP.padLine( "disk usage (du and df)" ) + "disabled" );

        }

        if ( CsapApis.getInstance( ).application( ).rootProjectEnvSettings( ).isLsofEnabled( ) ) {

            int lsofInterval = CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                    .getLsofIntervalMins( );

            serviceResourceRunnable = new ResourceCollector( CsapApis.getInstance( ), osCommandRunner );

            intenseOsCommandExecutor.scheduleWithFixedDelay(
                    serviceResourceRunnable,
                    1, lsofInterval, TimeUnit.MINUTES );

            startInfo.append(
                    CSAP.padLine( "service resources" ) + lsofInterval
                            + " minutes. Includes: minutes socket(ss), io(pidstat), files(/proc)" );

            intenseOsCommandExecutor.scheduleWithFixedDelay(
                    ( ) -> collectHostSocketsThreadsFiles( ),
                    1, lsofInterval, TimeUnit.MINUTES );

            startInfo.append( CSAP.padLine( "host resources" ) + lsofInterval
                    + " minutes.  Includes: ss,ps,lsof,/proc)" );

            intenseOsCommandExecutor.scheduleWithFixedDelay(
                    ( ) -> pingContainers( ),
                    1, lsofInterval, TimeUnit.MINUTES );

            startInfo.append( CSAP.padLine( "docker/kubernetes health" ) + lsofInterval
                    + " minutes. runs summary reports" );

        } else {

            startInfo.append( "\n\t -  Sockets, IO, And file capture is disabled" );

        }

        logger.info( startInfo.toString( ) );

    }

    public void pingContainers( ) {

        if ( CsapApis.getInstance( ).isKubernetesInstalledAndActive( ) ) {

            CsapApis.getInstance( ).kubernetes( ).buildKubernetesHealthReport( );

        }

        if ( CsapApis.getInstance( ).isContainerProviderInstalledAndActive( ) ) {

            CsapApis.getInstance( ).containerIntegration( ).getCachedSummaryReport( );

        }

    }

    public void shutDown( ) {

        logger.info( "shutting down osManager workers" );

        if ( topStatsRunnable != null ) {

            topStatsRunnable.shutdown( );

        }

        if ( serviceResourceRunnable != null ) {

            serviceResourceRunnable.shutDown( );

        }

        if ( jobRunner != null ) {

            jobRunner.shutdown( );

        }

        // if (hostStatusManager != null)
        // hostStatusManager.stop();
        if ( logRoller != null ) {

            logRoller.shutdown( );

        }

        intenseOsCommandExecutor.shutdownNow( );

    }

    private Integer getTopIntervalSeconds( ) {

        // Default is to poll every 1/2 of the service collection interval
        if ( CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getMetricToSecondsMap( )
                .size( ) == 0
                || !CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getMetricToSecondsMap( )
                .containsKey( "service" )
                || CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getMetricToSecondsMap( )
                .get( "service" )
                .size( ) == 0 ) {

            return 30;

        }

        return CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getMetricToSecondsMap( )
                .get( "service" )
                .get( 0 )
                / 2;

    }

    BasicThreadFactory openFilesThreadFactory = new BasicThreadFactory.Builder( )
            .namingPattern( "CsapOsManager-%d" )
            .daemon( true )
            .priority( Thread.NORM_PRIORITY )
            .build( );

    // both du and lsof get invoked here. Note we do this to avoid overwhelming
    // the OS with concurrent commands
    private ScheduledExecutorService intenseOsCommandExecutor = Executors
            .newScheduledThreadPool( 3, openFilesThreadFactory );

    public void resetAllCaches( ) {

        if ( !isProcessStatusInitialized( ) ) {

            logger.info( "== skipping cache reset - initialization pending ==" );
            return;

        }

        logger.info( "== process,disk, and memory caches reset ==" );

        if ( diskStatisticsCache != null ) {

            diskStatisticsCache.expireNow( );

        }

        if ( processStatisticsCache != null ) {

            processStatisticsCache.expireNow( );

        }

        if ( memoryStatisticsCache != null ) {

            memoryStatisticsCache.expireNow( );

        }

        // du is long running - so it is scheduled in background
        try {

            scheduleDiskUsageCollection( );

        } catch ( Exception e ) {

            logger.error( "Failed to schedule du", e );

        }

        checkForProcessStatusUpdate( );

    }

    TopRunnable topStatsRunnable = null; // use a lazy load so that thread
    // priority

    public int getHostTotalTopCpu( ) {

        Float f = Float.valueOf( -1 );

        if ( topStatsRunnable == null ) {

            return -1;

        }

        try {

            String[] pids = {
                    TopRunnable.VM_TOTAL
            };
            f = topStatsRunnable.getCpuForPid( Arrays.asList( pids ) );

        } catch ( Exception e ) {

            logger.error( "Unable to determine {}", CSAP.buildCsapStack( e ) );

        }

        return f.intValue( );

    }

    public int numberOfProcesses( ) {

        try {

            return lastProcessStatsCollected( ).split( LINE_SEPARATOR ).length;

        } catch ( Exception e ) {

        }

        return -1;

    }

    public ArrayNode processStatus( ) {

        return jsonMapper.convertValue( processMapper.getLatestDiscoveredProcesses( ), ArrayNode.class );

    }

    public boolean isProcessRunning( String namePattern ) {

        var optionalMatch = processMapper.getLatestDiscoveredProcesses( ).stream( )
                .map( OsProcess::getParameters )
                .filter( params -> params.matches( namePattern ) )
                .findFirst( );

        return optionalMatch.isPresent( );

    }

    /**
     * Synchronized as multiple metrics collections will try to hit this frequently.
     * This avoids costly ps calls
     */
    public ObjectNode buildServiceStatsReportAndUpdateTopCpu(
            boolean isCsapDefinitionProcessesOnly
    ) {

        ObjectNode servicesJson = jsonMapper.createObjectNode( );

        logger.debug( "Cache Refresh " );

        checkForProcessStatusUpdate( );

        logger.debug( "Cache Refresh: psResult: ", lastProcessStatsCollected( ) );

        var osPerformanceData = servicesJson.putObject( "ps" );

        if ( isCsapDefinitionProcessesOnly ) {

            CsapApis.getInstance( ).application( ).servicesOnHost( )
                    .map( serviceInstance -> {

                        // update top in date
                        serviceInstance.getContainerStatusList( ).stream( )
                                .forEach( container -> {

                                    var topCpu = 0.0f;

                                    if ( topStatsRunnable != null ) {

                                        topCpu = topStatsRunnable.getCpuForPid( container.getPid( ) );

                                    }

                                    container.setTopCpu( topCpu );

                                } );
                        return serviceInstance;

                    } )
                    .forEach( serviceInstance -> {

                        if ( serviceInstance.is_cluster_kubernetes( )
                                && !serviceInstance.getDefaultContainer( ).isRunning( ) ) {

                            // filter out inactive kubernetes processes
                        } else {

                            String id = serviceInstance.getPerformanceId( );
                            osPerformanceData.set( id, serviceInstance.buildRuntimeState( ) );

                        }

                    } );

        } else {

            // ObjectNode processNode = osPerformanceData.putObject( osProcess.getPid() );
            processMapper
                    .getLatestDiscoveredProcesses( )
                    .stream( )
                    .map( processMapper::mapProcessToUiJson )
                    .forEach( csapUiProcess -> {

                        osPerformanceData.set( csapUiProcess.at( ContainerState.JSON_PATH_PID ).asText( ),
                                csapUiProcess );

                    } );
            ;

        }

        servicesJson.set( "mp", getMpStateFromCache( ) );

        return servicesJson;

    }

    private CsapSimpleCache cpuStatisticsCache = null;
    private ReentrantLock mpStatusLock = new ReentrantLock( );

    private ObjectNode getMpStateFromCache( ) {

        if ( cpuStatisticsCache == null ) {

            cpuStatisticsCache = CsapSimpleCache.builder(
                    9,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "CPU Statistics" );
            cpuStatisticsCache.expireNow( );

        }

        if ( (cpuStatisticsCache.getCachedObject( ) != null)
                && !cpuStatisticsCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  cpuStatisticsCache   *******\n\n" );

        } else if ( mpStatusLock.tryLock( ) ) {

            logger.debug( "\n\n***** REFRESHING   cpuStatisticsCache   *******\n\n" );
            var timer = CsapApis.getInstance( ).metrics( ).startTimer( );

            try {

                cpuStatisticsCache.reset( updateMpCache( ) );

            } catch ( Exception e ) {

                logger.info( "Failed refreshing runtime: {}", CSAP.buildCsapStack( e ) );

            } finally {

                mpStatusLock.unlock( );

            }

            CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "cpu-metrics" );

        }

        return ( ObjectNode ) cpuStatisticsCache.getCachedObject( );

    }

    private ObjectNode updateMpCache( ) {

        var cpuReport = jsonMapper.createObjectNode( );

        if ( CsapApis.getInstance( ).application( ).isMacOsProfileActive( ) ) {

            var parmList = Arrays.asList( "bash", "-c", "system_profiler SPHardwareDataType | grep Core" );
            var macPhysReport = OsCommandRunner.trimHeader(
                    osCommandRunner.executeString( parmList, new File( "." ) )
            );
//            logger.info( "macPhysReport: {}", macPhysReport );
            // Total Number of Cores: 12 (8 performance and 4 efficiency)
            var numberOfCores = Integer.parseInt(  CsapConstants.words( macPhysReport )[ 4 ] ) ;

            for (var i =0 ; i<=numberOfCores; i++ ) {

                var name="CPU-" + i ;
                if ( i == 0 ) {
                    name = "all" ;
                }
                var coreReport = cpuReport.putObject( name );

                coreReport.put( "time", -1 );
                coreReport.put( "cpu", name );
                coreReport.put( "puser", -1 );
                coreReport.put( "pnice", -1 );
                coreReport.put( "psys", -1 );
                coreReport.put( "pio", -1 );
                coreReport.put( "pirq", -1 );
                coreReport.put( "psoft", -1 );
                coreReport.put( "psteal", -1 );
                coreReport.put( "pidle", -1 );
                coreReport.put( "intr", -1 );
            }

        } else {
            List< String > parmList = Arrays.asList( "bash", "-c",
                    "mpstat -P ALL  2 1| grep -i average | sed 's/  */ /g'" );

            var mpResult = "";
            mpResult = osCommandRunner.executeString( null, parmList );

            mpResult = CsapApis.getInstance( ).application( ).check_for_stub( mpResult, "linux/mpResults.txt" );

            logger.debug( "mpResult: {}", mpResult );

            if ( CsapApis.getInstance( ).application( ).isDesktopHost( ) ) {

                updateCachesWithTestData( );

            }

            // Skip past the header
            mpResult = mpResult.substring( mpResult.indexOf( "Average" ) );

            String[] mpLines = mpResult.split( LINE_SEPARATOR );

            for ( int i = 0; i < mpLines.length; i++ ) {

                String curline = mpLines[ i ].trim( );
                String[] cols = curline.split( " " );

                if ( cols.length < 11 || cols[ 1 ].equalsIgnoreCase( "cpu" )
                        || cols[ 0 ].startsWith( "_" ) ) {

                    logger.debug( "Skipping line: {}", curline );
                    continue;

                }

                String name = cols[ 1 ];

                ObjectNode coreReport = cpuReport.putObject( name );

                coreReport.put( "time", cols[ 0 ] + cols[ 1 ] );

                if ( !name.equals( "all" ) ) {

                    name = "CPU -" + name;

                }

                coreReport.put( "cpu", name );
                coreReport.put( "puser", cols[ 2 ] );
                coreReport.put( "pnice", cols[ 3 ] );
                coreReport.put( "psys", cols[ 4 ] );
                coreReport.put( "pio", cols[ 5 ] );
                coreReport.put( "pirq", cols[ 6 ] );
                coreReport.put( "psoft", cols[ 7 ] );
                coreReport.put( "psteal", cols[ 8 ] );
                coreReport.put( "pidle", cols[ 9 ] );
                coreReport.put( "intr", cols[ 10 ] );

            }
        }

        return cpuReport;

    }

    private ResourceCollector serviceResourceRunnable = null;

    private void updateCachesWithTestData( ) {

        try {

            setLinuxLineFormat( );

            if ( !CsapApis.getInstance( ).application( ).isMacOsProfileActive( ) ) {
                processStatisticsCache.reset( CsapApis.getInstance( ).application( ).check_for_stub( "",
                        "linux/ps-service-matching.txt" ) );
            }

            diskUsageForServicesCache = CsapApis.getInstance( ).application( ).check_for_stub( "",
                    "linux/ps-service-disk.txt" );
            // logger.debug( CsapApplication.testHeader( diskUsageForServicesCache ) ) ;

            diskUsageForServicesCache += CsapApis.getInstance( ).application( ).check_for_stub( "",
                    "linux/ps-system-disk.txt" );
            diskUsageForServicesCache += CsapApis.getInstance( ).application( ).check_for_stub( "",
                    "linux/ps-docker-volumes.txt" );
            // diskUsageForServicesCache +=
            // CsapApis.getInstance().application().check_for_stub( "",
            // "linux/dfResults.txt" ) ;

            diskUsageForServicesCache += collectDockerDiskUsage( );

        } catch ( Exception e ) {

            logger.error( "Failed to load test data: {}",
                    CSAP.buildCsapStack( e ) );

        }

    }

    public static void setLinuxLineFormat( ) {

        LINE_SEPARATOR = "\n";

    }

    // public void goActive() {
    // if (topStatsRunnable == null) {
    // topStatsRunnable = new TopRunnable();
    // // triggers the thread to go active
    // topStatsRunnable.getCpuForPid(Arrays.asList("dummy"));
    // }
    // }
    ArrayNode readOnlyFsResultsCache = null;
    long readOnlyFsTimeStamp = 0;

    public ArrayNode getReadOnlyFs( ) {

        logger.debug( "Getting getReadOnlyFs " );

        // Use cache
        if ( System.currentTimeMillis( ) - readOnlyFsTimeStamp < 1000 * 60 ) {

            logger.debug( "\n\n***** ReUsing  readOnlyFsResultsCache  *******\n\n" );

            return readOnlyFsResultsCache;

        }

        // Lets refresh cache
        logger.debug( "\n\n***** Refreshing readOnlyFsResultsCache   *******\n\n" );
        readOnlyFsTimeStamp = System.currentTimeMillis( );

        try {

            List< String > parmList = Arrays.asList( "bash", "-c",
                    "awk '$4~/(^|,)ro($|,)/' /proc/mounts | grep '^/dev/mapper' " );
            String roResult = osCommandRunner.executeString( parmList, new File(
                    "." ) );

            logger.debug( "roResult: {}", roResult );

            roResult = CsapApis.getInstance( ).application( ).check_for_stub( roResult, "linux/roResults.txt" );

            // if ( Application.isRunningOnDesktop() ) {
            // roResult = Application.loadTestData( "linux/roResults.txt" ) ;
            // }

            ArrayNode readOnlyResults = jsonMapper.createArrayNode( );

            String[] roLines = roResult.split( System
                    .getProperty( "line.separator" ) );

            for ( int i = 0; i < roLines.length; i++ ) {

                if ( roLines[ i ].trim( ).length( ) > 0
                        && !roLines[ i ].contains( OsCommandRunner.HEADER_TOKEN ) ) {

                    readOnlyResults.add( roLines[ i ].trim( ) );

                }

            }

            readOnlyFsResultsCache = readOnlyResults;

        } catch ( Exception e ) {

            logger.error( "Failed to write output", e );

        }

        return readOnlyFsResultsCache;

    }

    // @Inject
    // CsapEventClient csapEventClient;

    ArrayNode whoResultsCache = jsonMapper.createArrayNode( );
    long lastWhoTimeStamp = 0;

    public ArrayNode getVmLoggedIn( ) {

        logger.debug( "Entered " );

        // Use cache
        if ( System.currentTimeMillis( ) - lastWhoTimeStamp < 1000 * 60 ) {

            logger.debug( "\n\n***** ReUsing  who cache   *******\n\n" );

            return whoResultsCache;

        }

        logger.debug( "\n\n***** Refreshing who cache   *******\n\n" );
        lastWhoTimeStamp = System.currentTimeMillis( );

        try {

            List< String > parmList = Arrays.asList( "bash", "-c",
                    "who |sed 's/  */ /g'"
                            + "" );
            String whoResult = osCommandRunner.executeString( parmList, new File( "." ) );

            logger.debug( "whoResult: {}", whoResult );

            if ( Application.isRunningOnDesktop( ) ) {

                if ( Application.isDisplayOnDesktop( ) ) {

                    logger.warn( "Application.isRunningOnDesktop() - adding dummy login data" );

                }

                whoResult = "csapUser  pts/0        2014-04-16 07:58 (rtp-someDeveloper-8811.yourcompany.com)"
                        + System.getProperty( "line.separator" )
                        + "csapUser pts/34 2014-02-07 06:51";

            }

            ArrayNode whoResults = jsonMapper.createArrayNode( );

            String[] whoLines = whoResult.split( System
                    .getProperty( "line.separator" ) );

            for ( int i = 0; i < whoLines.length; i++ ) {

                String curline = whoLines[ i ].trim( );

                String[] cols = curline.split( " " );

                // some systems have a lot of non-external connections.
                // col 5 will contain host if it is external. So we ignore the
                // others
                // To focus on external traffic only
                if ( curline.length( ) == 0 || curline.contains( OsCommandRunner.HEADER_TOKEN )
                        || cols.length == 4 ) {

                    continue;

                }

                whoResults.add( curline );

            }

            if ( !whoResultsCache.toString( ).equals( whoResults.toString( ) ) ) {

                if ( whoResults.size( ) == 0 ) {

                    CsapApis.getInstance( ).application( ).getActiveUsers( ).logSessionEnd( Application.SYS_USER,
                            "No host sessions found, last found:\n"
                                    + CSAP.jsonPrint( whoResultsCache ) );

                    // CsapApis.getInstance().application().getEventClient().publishUserEvent(
                    // CsapEvents.CSAP_ACCESS_CATEGORY +
                    // "",
                    // Application.SYS_USER,
                    // "Host Session(s) Cleared", "Connections are no longer active:\n"
                    // + CSAP.jsonPrint( whoResultsCache ) ) ;
                } else {

                    CsapApis.getInstance( ).application( ).getActiveUsers( ).logSessionStart( Application.SYS_USER,
                            "Host Session(s) Changed:\n"
                                    + CSAP.jsonPrint( whoResults ) );

                    // CsapApis.getInstance().application().getEventClient().publishUserEvent(
                    // CsapEvents.CSAP_ACCESS_CATEGORY,
                    // Application.SYS_USER,
                    // "Host Session(s) Changed", "Updated output of linux \"who\": \n"
                    // + CSAP.jsonPrint( whoResults ) ) ;
                }

            }

            whoResultsCache = whoResults;

        } catch ( Exception e ) {

            logger.error( "Failed to build report {}", CSAP.buildCsapStack( e ) );

        }

        return whoResultsCache;

    }

    public static final String RECEIVE_MB = "receiveMb";
    public static final String TRANSMIT_MB = "transmitMb";
    NumberFormat twoDecimals = new DecimalFormat( "#0.00" );

    public ObjectNode networkReceiveAndTransmit( ) {

        ObjectNode networkIO = jsonMapper.createObjectNode( );

        // ens192
        String interfacePattern = CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getPrimaryNetwork( );

        List< String > lines = osCommands.getSystemNetworkStats( interfacePattern );

        try {

            String ioOutput = osCommandRunner.runUsingRootUser( "proc-net-dev", lines );
            ioOutput = CsapApis.getInstance( ).application( ).check_for_stub( ioOutput, "linux/proc-net-dev.txt" );

            // output' eth0: 757699260493 1264491650 0 6 0 0 0 176 756289173213
            // 982166397 0 0 0 0 0 0'
            String[] interfaces = ioOutput.split( LINE_SEPARATOR );

            for ( String interfaceLine : interfaces ) {

                String[] interfaceColumns = interfaceLine.trim( ).split( " " );
                logger.debug( "output from: {}  , columns: {} \n{}", lines, interfaceColumns.length, ioOutput );

                if ( interfaceColumns.length == 17 ) {

                    networkIO.put( RECEIVE_MB,
                            twoDecimals.format(
                                    networkIO.path( RECEIVE_MB ).asDouble( 0 )
                                            + Double.parseDouble( interfaceColumns[ 1 ] ) / CSAP.MB_FROM_BYTES ) );

                    networkIO.put( "readErrors",
                            networkIO.path( "readErrors" ).asInt( 0 )
                                    + Integer.parseInt( interfaceColumns[ 3 ] ) );

                    networkIO.put( TRANSMIT_MB,
                            twoDecimals.format(
                                    networkIO.path( TRANSMIT_MB ).asDouble( 0 )
                                            + Double.parseDouble( interfaceColumns[ 9 ] ) / CSAP.MB_FROM_BYTES ) );

                    networkIO.put( "transmitErrors",
                            networkIO.path( "transmitErrors" ).asInt( 0 )
                                    + Integer.parseInt( interfaceColumns[ 11 ] ) );

                } else {

                    networkIO.put( "error", true );

                }

            }

        } catch ( IOException e ) {

            logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
                    CSAP.buildCsapStack( e ) );

            networkIO.put( "error", true );

        }

        return networkIO;

    }

    private final static String KUBERNETES_NODE_SCRIPT = "bin/collect-kubernetes.sh";

    // private OsCommandRunner hostRootCommands = new OsCommandRunner( 30, 1,
    // "OsManager" );

    public volatile CsapSimpleCache kubernetesNodeUsageReportCache = null;

    public JsonNode getLatestKubernetesNodeReport( String hostName ) {

        buildCachedKubernetesNodeUsageReport( );
        return (( ObjectNode ) kubernetesNodeUsageReportCache.getCachedObject( )).path( hostName );

    }

    public ObjectNode buildCachedKubernetesNodeUsageReport( ) {

        if ( kubernetesNodeUsageReportCache == null ) {

            // typically - very very fast - but this will handle large concurrent requests
            // from hitting system
            kubernetesNodeUsageReportCache = CsapSimpleCache.builder(
                    5,
                    TimeUnit.SECONDS,
                    this.getClass( ),
                    "osmanager-kuberntes-node-usage" );
            kubernetesNodeUsageReportCache.expireNow( );

        }

        // Use cache
        if ( !kubernetesNodeUsageReportCache.isExpired( ) ) {

            logger.debug( "ReUsing kubernetesNodeUsageReportCache" );

            return ( ObjectNode ) kubernetesNodeUsageReportCache.getCachedObject( );

        }

        // Lets refresh cache
        logger.debug( "Refreshing kubernetesNodeUsageReportCache" );

        // logger.info( "Call path: {}",
        // Application.getCsapFilteredStackTrace( new Exception( "calltree"
        // ), "csap" ) );
        logger.debug( "refreshing host stats" );
        var nodeReports = jsonMapper.createObjectNode( );

        var commandOutput = "";

        try {

            // running as root to get access to all files on host.
            commandOutput = osCommandRunner.runUsingRootUser(
                    CsapApis.getInstance( ).application( ).csapPlatformPath( KUBERNETES_NODE_SCRIPT ),
                    null );

            logger.debug( "commandOutput: {}", commandOutput );

            commandOutput = CsapApis.getInstance( ).application( ).check_for_stub( commandOutput,
                    "linux/kubernetes-describe-nodes.txt" );
            logger.debug( "trimmed results: {} ", commandOutput );
            String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );

            String[] nodeCommandLines = commandOutput.split( LINE_SEPARATOR );

            Arrays.stream( nodeCommandLines )
                    .filter( StringUtils::isNotEmpty )
                    .map( line -> CsapConstants.singleSpace( line ).split( " " ) )
                    .filter( columns -> columns.length <= 5 )
                    .forEach( columns -> {

                        switch ( columns[ 0 ] ) {

                            case "Name:":
                                if ( columns.length == 2 ) {

                                    nodeReports.put( "nodeName", columns[ 1 ] );
                                    nodeReports.putObject( columns[ 1 ] );

                                }
                                break;

                            case "Capacity:":
                            case "Allocatable:":
                            case "Allocated": {

                                var nodeName = nodeReports.path( "nodeName" ).asText( );
                                var nodeReport = nodeReports.path( nodeName );

                                if ( nodeReport.isObject( ) ) {

                                    var sectionName = columns[ 0 ].split( ":" )[ 0 ];
                                    nodeReports.put( "sectionName", sectionName );
                                    var node = ( ObjectNode ) nodeReport;
                                    node.putObject( sectionName );

                                }

                            }
                            break;

                            case "cpu:":
                            case "memory:":
                            case "cpu":
                            case "memory": {

                                if ( columns.length == 2
                                        || columns.length == 5 ) {

                                    var nodeName = nodeReports.path( "nodeName" ).asText( );
                                    var nodeReport = nodeReports.path( nodeName );

                                    if ( nodeReport.isObject( ) ) {

                                        var sectionName = nodeReports.path( "sectionName" ).asText( );
                                        var sectionReport = nodeReport.path( sectionName );

                                        if ( sectionReport.isObject( ) ) {

                                            var section = ( ObjectNode ) sectionReport;
                                            var metricName = columns[ 0 ].split( ":" )[ 0 ];

                                            if ( columns.length == 2 ) {

                                                section.put( metricName, columns[ 1 ] );

                                            } else {

                                                var metricReport = section.putObject( metricName );
                                                metricReport.put( "request", columns[ 1 ] );
                                                metricReport.put( "requestPercent", stripParens( columns[ 2 ] ) );
                                                metricReport.put( "limit", columns[ 3 ] );
                                                metricReport.put( "limitPercent", stripParens( columns[ 4 ] ) );

                                            }

                                        }

                                    }

                                }

                                break;

                            }

                        }

                    } );

            kubernetesNodeUsageReportCache.reset( nodeReports );

            // setLatestKubernetesNodeReport( nodeReports ) ;

        } catch ( Exception e ) {

            logger.error( "Failed to process output from {}: {}\n {}",
                    SOCKETS_THREADS_FILES_SCRIPT, CSAP.buildCsapStack( e ), commandOutput );

        }

        return ( ObjectNode ) kubernetesNodeUsageReportCache.getCachedObject( );

    }

    private String stripParens( String input ) {

        return input.replaceAll( "[()%]", "" );

    }

    volatile ObjectNode hostResourceSummary = null;

    public ObjectNode getHostResourceSummary( ) {

        if ( hostResourceSummary == null ) {

            collectHostSocketsThreadsFiles( );

        }

        return hostResourceSummary;

    }

    public int getHostSummaryItem(
            String fieldName
    ) {

        if ( hostResourceSummary == null ) {

            collectHostSocketsThreadsFiles( );

        }

        if ( hostResourceSummary.has( fieldName ) ) {

            return hostResourceSummary.path( fieldName ).asInt( 0 );

        }

        return 0;

    }

    private final static String SOCKETS_THREADS_FILES_SCRIPT = "bin/collect-host-resources.sh";

    // private OsCommandRunner hostRootCommands = new OsCommandRunner( 30, 1,
    // "OsManager" );

    private void collectHostSocketsThreadsFiles( ) {

        // logger.info( "Call path: {}",
        // Application.getCsapFilteredStackTrace( new Exception( "calltree"
        // ), "csap" ) );
        logger.debug( "refreshing host stats" );
        String statsResult = null;

        try {

            // running as root to get access to all files on host.
            statsResult = osCommandRunner.runUsingRootUser( CsapApis.getInstance( ).application( ).csapPlatformPath(
                            SOCKETS_THREADS_FILES_SCRIPT ),
                    null );

            logger.debug( "statsResult: {}", statsResult );

            statsResult = CsapApis.getInstance( ).application( ).check_for_stub( statsResult,
                    "linux/vmStatsRoot.txt" );

            statsResult = statsResult.substring( statsResult.indexOf( "openFiles:" ) );
            logger.debug( "trimmed results: {} ", statsResult );
            String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );

            ObjectNode updatedHostSummary = jsonMapper.createObjectNode( );
            updatedHostSummary.put( "refreshed", now );

            String[] cols = statsResult.split( " " );

            if ( cols.length == 16 ) {

                updatedHostSummary.put( "openFiles", cols[ 1 ] );
                updatedHostSummary.put( "totalThreads", cols[ 3 ] );
                updatedHostSummary.put( "csapThreads", cols[ 5 ] );
                updatedHostSummary.put( "totalFileDescriptors", cols[ 7 ] );
                updatedHostSummary.put( "csapFileDescriptors", cols[ 9 ] );
                updatedHostSummary.put( "networkConns", cols[ 11 ] );
                updatedHostSummary.put( "networkWait", cols[ 13 ] );
                updatedHostSummary.put( "networkTimeWait", StringUtils.strip( cols[ 15 ], "\n" ) );

            } else {

                updatedHostSummary.put( "error", statsResult );

            }

            hostResourceSummary = updatedHostSummary;

        } catch ( Exception e ) {

            logger.error( "Failed to process output from {}: {}\n {}",
                    SOCKETS_THREADS_FILES_SCRIPT, CSAP.buildCsapStack( e ), statsResult );

        }

    }

    public String getJournal(
            String serviceName,
            String since,
            String numberOfLines,
            boolean reverse,
            boolean json
    ) {

        String serviceFilter = "";

        if ( !serviceName.equalsIgnoreCase( "none" ) ) {

            serviceFilter = " --unit " + serviceName + " ";

        }

        String fromFilter = "";

        if ( StringUtils.isNotEmpty( since ) ) {

            fromFilter = " --since '" + since + "' ";

        }

        String reverseParam = "";
        if ( reverse )
            reverseParam = " -r ";

        String jsonParam = "";
        if ( json )
            jsonParam = " -o json ";

        var journalCollectionScript = List.of(
                "#!/bin/bash",
                "journalctl --no-pager -n " + numberOfLines + serviceFilter + fromFilter + reverseParam + jsonParam,
                "" );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "journal", journalCollectionScript );
            logger.debug( "journalCollectionScript: {}, \n scriptOutput: {}", journalCollectionScript, scriptOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        return scriptOutput;

    }

    private int cachedNetworkInterfaceCount = -1;

    public List< String > networkInterfaces( ) {

        var timer = CsapApis.getInstance( ).metrics( ).startTimer( );

        logger.debug( "Entered " );

        List< String > networkDevices = Arrays.asList( "none" );

        List< String > collectionScripts = osCommands.getSystemNetworkDevices( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "system interface list", collectionScripts );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/network-devices.txt" );
            String[] serviceLines = scriptOutput.split( LINE_SEPARATOR );

            var mergedLines = new ArrayList< String >( );
            Arrays.stream( serviceLines )
                    .filter( StringUtils::isNotEmpty )
                    .forEach( line -> {

                        if ( line.charAt( 0 ) != ' ' ) {

                            mergedLines.add( line.replaceAll( "<", "" ).replaceAll( ">", "" ) );

                        } else if ( mergedLines.size( ) > 0 && line.contains( "inet " ) ) {

                            mergedLines.set( mergedLines.size( ) - 1, mergedLines.get( mergedLines.size( ) - 1 )
                                    + line );

                        }

                    } );
            ;

            networkDevices = mergedLines;

            setCachedNetworkInterfaceCount( networkDevices.size( ) );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "linux-devices-ip" );

        return networkDevices;

    }

    private ConcurrentHashMap< String, String > ip_to_hostname = new ConcurrentHashMap< String, String >( );

    private String[] hostSplit( String fqdn ) {

        String host = fqdn;
        if ( host == null )
            host = "";

        return host.split( Pattern.quote( "." ) );

    }

    public String ipToHostName( String hostIP ) {

        if ( hostIP == null ) {

            return "host_not_found";

        }

        if ( hostIP.equals( "127.0.0.1" ) ) {

            return "localhost";

        }

        if ( !ip_to_hostname.containsKey( hostIP ) ) {

            String name = hostIP;

            if ( CsapApis.getInstance( ).isKubernetesInstalledAndActive( )
                    && CsapApis.getInstance( ).kubernetes( ).getSettings( ).isDnsLookup( ) ) {

                try {

                    // name = InetAddress.getByName( hostIP ).getHostName() ;
                    name = InetAddress.getByName( hostIP ).getCanonicalHostName( );
                    logger.info( "Resolved name: {} - {}", hostIP, name );

                    if ( !hostIP.equals( name ) ) {

                        name = hostSplit( name )[ 0 ];

                    } else {

                        // localhost will not resolve if found in /etc/hosts
                        // check for hostname
                        Stream< NetworkInterface > niStream = StreamSupport.stream(
                                Spliterators.spliteratorUnknownSize( NetworkInterface
                                        .getNetworkInterfaces( ).asIterator( ), Spliterator.ORDERED ),
                                false );

                        Optional< String > niAddress = niStream.flatMap( ni -> ni.getInterfaceAddresses( ).stream( ) )
                                .map( address -> address.getAddress( ).getCanonicalHostName( ) )
                                .filter( niHostName -> niHostName.contains( hostIP ) )
                                .findFirst( );

                        if ( niAddress.isPresent( ) ) {

                            logger.info( "Found IP: {}, setting host to: {}", hostIP, CsapApis.getInstance( )
                                    .application( ).getCsapHostName( ) );
                            name = CsapApis.getInstance( ).application( ).getCsapHostName( );

                        }

                        // .collect( Collectors.joining( "\n\t" ) ) ;
                    }

                } catch ( Exception e ) {

                    logger.info( "Failed getting host name {}", CSAP.buildCsapStack( e ) );

                }

            } else {

                logger.debug( "DNS resolution disabled - ip addresses will be shown in ui" );

            }

            ip_to_hostname.put( hostIP, name );

        }

        return ip_to_hostname.get( hostIP );

    }

    private String resolveSocketHost( String hostPort ) {

        var resolvedHost = hostPort;

        if ( StringUtils.isNotEmpty( hostPort ) ) {

            var tokens = hostPort.split( ":" );

            if ( tokens.length == 2 ) {

                resolvedHost = ipToHostName( tokens[ 0 ] ) + ":" + tokens[ 1 ];

            }

            if ( tokens.length == 5 ) {

                resolvedHost = ipToHostName( tokens[ 3 ] ) + ":" + tokens[ 4 ];

            }

        }

        return resolvedHost;

    }

    public ArrayNode socketConnections( boolean isSummarize ) {

        ArrayNode portReport = jsonMapper.createArrayNode( );

        var timer = CsapApis.getInstance( ).metrics( ).startTimer( );
        logger.debug( "Entered " );

        Map< String, ObjectNode > summaryReportCache = new HashMap<>( );

        List< String > collectionScripts = osCommands.getSystemNetworkPorts( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "ss-ports", collectionScripts );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/network-ports-connections.txt" );
            String[] serviceLines = scriptOutput.split( LINE_SEPARATOR );

            Arrays.stream( serviceLines )
                    .filter( StringUtils::isNotEmpty )
                    .forEach( portLine -> {

                        String[] columns = CsapConstants.singleSpace( portLine ).split( " ", 6 );

                        if ( columns.length == 6 ) {

                            var portId = columns[ 3 ];

                            try {

                                var labels = portId.split( ":" );

                                if ( labels.length > 1 ) {

                                    portId = labels[ labels.length - 1 ];

                                }

                            } catch ( Exception e ) {

                                logger.info( "failed splitting '{}'", portId );

                            }

                            var peer = columns[ 4 ];

                            var processName = columns[ 5 ];
                            var users = processName.split( "\"", 3 );

                            if ( users.length == 3 ) {

                                processName = users[ 1 ];

                            }

                            var peerProcessKey = processName + peer;

                            ObjectNode portDetails;

                            if ( isSummarize ) {

                                if ( summaryReportCache.containsKey( portId ) ) {

                                    var portPrimary = summaryReportCache.get( portId );
                                    var portSecondary = portPrimary.path( "related" );

                                    if ( !portSecondary.isArray( ) ) {

                                        portSecondary = portPrimary.putArray( "related" );

                                    }

                                    portDetails = (( ArrayNode ) portSecondary).addObject( );

                                } else if ( summaryReportCache.containsKey( peerProcessKey ) ) {

                                    var portPrimary = summaryReportCache.get( peerProcessKey );
                                    var portSecondary = portPrimary.path( "related" );

                                    if ( !portSecondary.isArray( ) ) {

                                        portSecondary = portPrimary.putArray( "related" );

                                    }

                                    portDetails = (( ArrayNode ) portSecondary).addObject( );

                                } else {

                                    portDetails = portReport.addObject( );
                                    summaryReportCache.put( portId, portDetails );
                                    summaryReportCache.put( peerProcessKey, portDetails );

                                }

                            } else {

                                portDetails = portReport.addObject( );

                            }

                            var details = columns[ 5 ];
                            var pid = findFirstPidInSSDetails( details );
                            portDetails.put( "csapNoSort", true );
                            portDetails.put( "port", portId );
                            portDetails.put( "pid", pid );
                            portDetails.put( "processName", processName );
                            portDetails.put( "state", columns[ 0 ] );
                            portDetails.put( "recv-q", columns[ 1 ] );
                            portDetails.put( "send-q", columns[ 2 ] );
                            portDetails.put( "local", resolveSocketHost( columns[ 3 ] ) );
                            portDetails.put( "peer", resolveSocketHost( peer ) );
                            portDetails.put( "details", details );

                        }

                        // portDetails.put( "line", portLine ) ;
                    } );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "socket-connections" );

        return portReport;

    }

    private String findFirstPidInSSDetails( String details ) {

        var pid = "";
        var pidIndex = details.indexOf( "pid=" );

        if ( pidIndex != -1 ) {

            pid = details.substring( pidIndex + 4 );
            pid = pid.substring( 0, pid.indexOf( "," ) );

        }

        return pid;

    }

    public ArrayNode socketListeners( boolean isSummarize ) {

        ArrayNode portReport = jsonMapper.createArrayNode( );

        var timer = CsapApis.getInstance( ).metrics( ).startTimer( );
        logger.debug( "Entered " );

        List< String > collectionScripts = osCommands.getSystemNetworkListenPorts( );

        Map< String, ObjectNode > summaryReportCache = new HashMap<>( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "ss-ports", collectionScripts );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/network-ports-listen.txt" );
            String[] serviceLines = scriptOutput.split( LINE_SEPARATOR );

            Arrays.stream( serviceLines )
                    .filter( StringUtils::isNotEmpty )
                    .forEach( portLine -> {

                        String[] columns = CsapConstants.singleSpace( portLine ).split( " ", 6 );

                        if ( columns.length == 6 ) {

                            var portId = columns[ 3 ];

                            try {

                                var labels = portId.split( ":" );

                                if ( labels.length > 1 ) {

                                    portId = labels[ labels.length - 1 ];

                                }

                            } catch ( Exception e ) {

                                logger.info( "failed splitting '{}'", portId );

                            }

                            ObjectNode portDetails;

                            if ( isSummarize ) {

                                if ( summaryReportCache.containsKey( portId ) ) {

                                    var portPrimary = summaryReportCache.get( portId );
                                    var portSecondary = portPrimary.path( "related" );

                                    if ( !portSecondary.isArray( ) ) {

                                        portSecondary = portPrimary.putArray( "related" );

                                    }

                                    portDetails = (( ArrayNode ) portSecondary).addObject( );

                                } else {

                                    portDetails = portReport.addObject( );
                                    summaryReportCache.put( portId, portDetails );

                                }

                            } else {

                                portDetails = portReport.addObject( );

                            }

                            var details = columns[ 5 ];
                            var pid = findFirstPidInSSDetails( details );

                            portDetails.put( "csapNoSort", true );
                            portDetails.put( "port", portId );
                            portDetails.put( "pid", pid );
                            portDetails.put( "state", columns[ 0 ] );
                            portDetails.put( "recv-q", columns[ 1 ] );
                            portDetails.put( "send-q", columns[ 2 ] );
                            portDetails.put( "local", columns[ 3 ] );
                            portDetails.put( "peer", columns[ 4 ] );
                            portDetails.put( "details", details );

                        }

                        // portDetails.put( "line", portLine ) ;
                    } );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "socket-listeners" );

        return portReport;

    }

    private int cachedLinuxPackageCount = -1;

    public List< String > getLinuxPackages( ) {

        var timer = CsapApis.getInstance( ).metrics( ).startTimer( );

        logger.debug( "Entered " );

        List< String > linuxRpms = Arrays.asList( "none" );

        List< String > lines = osCommands.getSystemPackages( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "system rpm list", lines );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/rpmResults.txt" );
            String[] serviceLines = scriptOutput.split( LINE_SEPARATOR );

            linuxRpms = Arrays.stream( serviceLines )
                    .filter( StringUtils::isNotEmpty )
                    .collect( Collectors.toList( ) );

            setCachedLinuxPackageCount( linuxRpms.size( ) );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "linux-packages" );

        return linuxRpms;

    }

    public String runFile(
            File commandFile
    ) {

        logger.debug( "Entered " );

        String scriptOutput = "Failed to run";

        scriptOutput = osCommandRunner.runUsingRootUser( commandFile, null );
        scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                "linux/kubectl-dashboard.txt" );

        return scriptOutput;

    }

    public String getMountPath( String mountSource ) {

        logger.debug( "Entered " );

        List< String > lines = osCommands.getNfsMountLocation( mountSource );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "mount-location", lines );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/disk-nfs-mount-location.txt" );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        return scriptOutput.trim( ).replaceAll( "[\\n\\t ]", "" );

    }

    public ObjectNode run_cpu_listing( ) {

        logger.debug( "Entered " );

        var lines = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "print_command \"lscpu\" \"$(lscpu)\"",
                "print_command \"cat /proc/cpuinfo\" \"$(cat /proc/cpuinfo)\"",
                "" );

        var processReport = jsonMapper.createObjectNode( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingDefaultUser( "cpu listing", lines );

            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/mpResults.txt" );
            processReport.put( C7.response_plain_text.val( ), scriptOutput );


        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );


        }

        processReport.put( C7.response_plain_text.val( ), scriptOutput );

        return processReport;

    }

    public ObjectNode run_zing_memory_listing( ) {

        logger.debug( "Entered " );

        var processReport = jsonMapper.createObjectNode( );

        String scriptOutput = "not-run";

        try {

            var zingPsFile = CsapApis.getInstance( ).application( ).configuration( ).getZingPsFile( );
            ;
            processReport.put( "run", false );
            if ( (zingPsFile != null && zingPsFile.exists( )) || !CsapApplication.isCsapFolderSet( ) ) {

                var lines = List.of(
                        "#!/bin/bash",
                        sourceCommonFunctions( ),
                        zingPsFile.getAbsolutePath( ) + " -s -h",
                        "" );

                scriptOutput = osCommandRunner.runUsingDefaultUser( "zing memory listing", lines );

                scriptOutput = OsCommandRunner.trimHeader( scriptOutput );

                scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                        "linux/mpResults.txt" );
                processReport.put( C7.response_plain_text.val( ), scriptOutput );

                processReport.put( "run", true );
            }


        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );


        }

        processReport.put( C7.response_plain_text.val( ), scriptOutput );

        return processReport;

    }

    public ObjectNode build_memory_report( ) {

        logger.debug( "Entered " );

        var processReport = jsonMapper.createObjectNode( );

        String scriptOutput = "not-run";


        var lines = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "print_section 'ref. http://linux-kb.blogspot.com/2009/09/free-memory-in-linux-explained.html'",
                "run_and_format free -m",
                "run_and_format show_memory",
                "" );

        try {

            scriptOutput = osCommandRunner.runUsingDefaultUser( "build-memory-report", lines );

            scriptOutput = OsCommandRunner.trimHeader( scriptOutput );

            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/freeResults.txt" );
            processReport.put( C7.response_plain_text.val( ), scriptOutput );

            processReport.put( "run", true );

            processReport.put( C7.response_plain_text.val( ), scriptOutput );
        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );


        }

        return processReport;

    }

    public ObjectNode run_mpstat_listing( ) {

        logger.debug( "Entered " );

        var lines = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "print_command \"mpstat -P ALL 2 1 | grep -i average\" \"$(mpstat -P ALL 2 1 | grep -i average)\"",
                "" );

        var processReport = jsonMapper.createObjectNode( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingDefaultUser( "mpstat listing", lines );

            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/mpResults.txt" );
            processReport.put( C7.response_plain_text.val( ), scriptOutput );


        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );


        }

        processReport.put( C7.response_plain_text.val( ), scriptOutput );

        return processReport;

    }

    public ObjectNode run_cron_job_listing( ) {

        logger.debug( "Entered " );

        List< String > lines = List.of( "crontab -l" );
        var processReport = jsonMapper.createObjectNode( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "crontab listing", lines );

            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/disk-nfs-mount-location.txt" );
            processReport.put( C7.response_plain_text.val( ), scriptOutput );


        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );


        }

        processReport.put( C7.response_plain_text.val( ), scriptOutput );

        return processReport;

    }

    public String getLinuxPackageInfo(
            String packageName
    ) {

        logger.debug( "Entered " );

        List< String > lines = osCommands.getSystemPackageDetails( packageName );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "system rpm info", lines );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/disk-nfs-mount-location.txt" );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        return scriptOutput;

    }

    public void setCachedChefCookbookCount( int cachedChefCookbookCount ) {
        this.cachedChefCookbookCount = cachedChefCookbookCount;
    }

    private int cachedChefCookbookCount = -1;

    public List< String > getChefListing( ) {

        var chefCookbookPath = CsapApis.getInstance( ).application( ).configuration( ).getChefCookbookPath( );
        if ( StringUtils.isEmpty( chefCookbookPath ) ) {
            return List.of( );
        }
        var chefFolder = new File( chefCookbookPath );

//        var fileListing = CsapApis.getInstance( ).fileUtils( ).buildListingUsingJava( chefFolder, chefFolder.getAbsolutePath( ), null );
        List< String > cookbookNames = List.of( );
        try {
            var fileListing = CsapApis.getInstance( ).fileUtils( ).buildListingUsingRoot( chefFolder, null, chefFolder.getAbsolutePath( ) );

            if ( !CsapApplication.isCsapFolderSet( ) ) {
                fileListing = CsapApis.getInstance( ).fileUtils( ).buildListingUsingJava( chefFolder, chefFolder.getAbsolutePath( ), null );
            }

            cookbookNames = CSAP.jsonStream( fileListing )
                    .filter( JsonNode::isObject )
                    .map( node -> ( ObjectNode ) node )
                    .map( node -> node.path( "name" ).asText( ) )
                    .collect( Collectors.toList( ) );
        } catch ( Exception e ) {
            throw new RuntimeException( e );
        }

        setCachedChefCookbookCount( cookbookNames.size( ) );

        return cookbookNames;

    }

    private int cachedLinuxServiceCount = -1;

    public List< String > getLinuxServices( ) {

        var timer = CsapApis.getInstance( ).metrics( ).startTimer( );
        logger.debug( "Entered " );

        List< String > linuxSystemdServices = Arrays.asList( "none" );

        List< String > lines = osCommands.getSystemServices( );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "system services list", lines );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/systemctl-services.txt" );
            String[] serviceLines = scriptOutput.split( LINE_SEPARATOR );

            linuxSystemdServices = Arrays.stream( serviceLines )
                    .filter( StringUtils::isNotEmpty )
                    .collect( Collectors.toList( ) );

            setCachedLinuxServiceCount( linuxSystemdServices.size( ) );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "linux-services" );

        return linuxSystemdServices;

    }

    public String getLinuxServiceStatus(
            String serviceName
    ) {

        logger.debug( "Entered " );

        List< String > lines = osCommands.getSystemServiceDetails( serviceName );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "system services list", lines );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/systemctl-status.txt" );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        return scriptOutput;

    }

    public String removeNonGitFiles(
            File folderWithGit
    ) {

        logger.debug( "Entered " );

        File verifyGit = new File( folderWithGit, ".git" );

        if ( !verifyGit.isDirectory( ) )
            return "Skipping - did not find git files";

        List< String > lines = List.of(
                "cd " + folderWithGit.getAbsolutePath( ),
                "if test -d .git ; then " + folderWithGit.getAbsolutePath( ),
                "\\rm --recursive --verbose --force * ; ",
                "fi" );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingDefaultUser( "system services list", lines );
            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/systemctl-status.txt" );

        } catch ( IOException e ) {

            logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( );

        }

        return "command: " + lines + "\n output:" + scriptOutput;

    }

    private int diskCount = 0;

    public int getDiskCount( ) {

        return diskCount;

    }

    private volatile CsapSimpleCache kubernetesJoinCache = null;

    public synchronized ObjectNode getCachedKubernetesJoin( ) {

        logger.debug( "Entered " );

        if ( kubernetesJoinCache == null ) {

            kubernetesJoinCache = CsapSimpleCache.builder(
                    60,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "Kubernetes Join" );
            kubernetesJoinCache.expireNow( );

        }

        // Use cache
        if ( !kubernetesJoinCache.isExpired( ) ) {

            logger.debug( "ReUsing kubernetesJoinCache" );

            return ( ObjectNode ) kubernetesJoinCache.getCachedObject( );

        }

        // Lets refresh cache
        logger.debug( "Refreshing kubernetesJoinCache" );

        try {

            File joinFile = new File( CsapApis.getInstance( ).application( ).kubeletInstance( ).getWorkingDirectory( ),
                    "/scripts/cluster-join-commands.sh" );

            // String joinOutput = runFile( joinFile ) ;

            List< String > scriptLines = List.of( "desktop-only" );

            if ( joinFile.exists( ) ) {

                scriptLines = Files.readAllLines( joinFile.toPath( ) );

            }

            String joinOutput = osCommandRunner.runUsingDefaultUser( "kubernetes-join-commands", scriptLines );

            joinOutput = CsapApis.getInstance( ).application( ).check_for_stub( joinOutput, "linux/kubeadm-join.txt" );
            //
            logger.debug( "joinOutput: {}", joinOutput );

            String[] joinOutputLines = joinOutput.split( LINE_SEPARATOR );

            ObjectNode joinCommands = jsonMapper.createObjectNode( );

            for ( var line : joinOutputLines ) {

                line = CsapConstants.singleSpace( line );

                if ( line.startsWith( "joinWorkerCommand" ) ) {

                    joinCommands.put( "worker", line.substring( line.indexOf( ':' ) + 1 ).trim( ) );

                } else if ( line.startsWith( "joinControlCommand" ) ) {

                    joinCommands.put( "master", line.substring( line.indexOf( ':' ) + 1 ).trim( ) );

                }

            }

            if ( joinCommands.has( "master" ) && joinCommands.has( "worker" ) ) {

                logger.info( "primary kubernetes master resolved join; next master will join at least 60s from now" );
//				kubernetesJoinCache.reset( joinCommands ) ;
                kubernetesJoinCache.reset( jsonMapper.createObjectNode( ) );
                return joinCommands;

            } else {

                logger.warn( "did not find master and work in output: {}", joinOutput );
                return joinCommands;

            }

        } catch ( Exception e ) {

            logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) );

        }

        return ( ObjectNode ) kubernetesJoinCache.getCachedObject( );

    }

    private volatile CsapSimpleCache crioPsCache = null;

    public ObjectNode getCachedCrioPs( ) {

        logger.debug( "Entered " );

        if ( crioPsCache == null ) {

            crioPsCache = CsapSimpleCache.builder(
                    2,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "Crio Ps" );
            crioPsCache.expireNow( );

        }

        // Use cache
        if ( !crioPsCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  crioPsCache   *******\n\n" );

            return ( ObjectNode ) crioPsCache.getCachedObject( );

        }

        // Lets refresh cache
        logger.debug( "\n\n***** Refreshing crioPsCache   *******\n\n" );

        try {

            // run as root to pick up docker and kubelet filesystems
            var criPsOutput = osCommandRunner.runUsingRootUser( "crio-ps", osCommands.getCriPs( ) );

            logger.debug( "criPsOutput: {}", criPsOutput );

            criPsOutput = CsapApis.getInstance( ).application( ).check_for_stub( criPsOutput, "crio/crictl-ps.json" );

            crioPsCache.reset( jsonMapper.readTree( criPsOutput ) );

        } catch ( Exception e ) {

            logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) );

        }

        return ( ObjectNode ) crioPsCache.getCachedObject( );

    }

    private volatile CsapSimpleCache crioPidCache = null;

    public ObjectNode getCachedCrioPidReport( ) {

        logger.debug( "Entered " );

        if ( crioPidCache == null ) {

            crioPidCache = CsapSimpleCache.builder(
                    2,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "crio-pid-report" );
            crioPidCache.expireNow( );

        }

        // Use cache
        if ( !crioPidCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  crioPidCache   *******\n\n" );

            return ( ObjectNode ) crioPidCache.getCachedObject( );

        }

        // Lets refresh cache
        logger.debug( "\n\n***** Refreshing crioPidCache   *******\n\n" );

        try {

            var pidReport = jsonMapper.createObjectNode( );
            var usedNames = new ArrayList< String >( );

            // run as root to pick up docker and kubelet filesystems
            var criPidOutput = osCommandRunner.runUsingRootUser( "crio-pid-report", osCommands.getCriPidReport( ) );

            criPidOutput = CsapApis.getInstance( ).application( ).check_for_stub( criPidOutput, "crio/crio-pids.txt" );
            logger.debug( "criPidOutput: {}", criPidOutput );

            var outputLines = OsCommandRunner.trimHeader( criPidOutput ).split( LINE_SEPARATOR );

            Arrays.stream( outputLines )
                    .filter( StringUtils::isNotEmpty )
                    .filter( line -> !line.startsWith( "#" ) )
                    .map( String::trim )
                    .map( line -> line.split( "," ) )

                    .filter( csvArray -> csvArray.length == 3 )
                    .forEach( csvArray -> {

                        var labelsRaw = csvArray[ 0 ];
                        var labelsSpaceSeparated = labelsRaw.substring( 4, labelsRaw.length( ) - 1 );

                        var labelEntries = OsCommandRunner.trimHeader( labelsSpaceSeparated ).split( " " );
                        var labelReport = Arrays.stream( labelEntries )
                                .filter( StringUtils::isNotEmpty )
                                .map( line -> line.split( ":" ) )
                                .filter( labelVal -> labelVal.length == 2 )
                                .collect( Collectors.toMap(
                                        labelVal -> labelVal[ 0 ],
                                        labelVal -> labelVal[ 1 ] ) );

                        if ( labelReport.containsKey( "io.kubernetes.pod.name" ) ) {
//

//							var containerLabel = labelReport.get( "io.kubernetes.pod.name" )
//									+ ","
//									+ labelReport.get( "io.kubernetes.container.name" ) ;

                            var containerLabel = CRIO_DELIMETER + labelReport.get( "io.kubernetes.pod.namespace" )
                                    + "---" + labelReport.get( "io.kubernetes.pod.name" )
                                    + "---" + labelReport.get( "io.kubernetes.container.name" );

//							if ( usedNames.contains( containerLabel ) ) {
//
//								containerLabel += "---" + labelReport.get( "io.kubernetes.container.name" ) ;
//
//							}

                            usedNames.add( containerLabel );

                            var item = pidReport.putObject( containerLabel );
                            item.put( "name", containerLabel );
                            item.put( "pid", csvArray[ 1 ] );
                            item.put( "id", csvArray[ 2 ] );

                            labelReport.entrySet( ).stream( ).forEach( entry -> {

                                item.put( entry.getKey( ), entry.getValue( ) );

                            } );

                        }

                    } );

            logger.debug( "pidReport: {}", pidReport );
            crioPidCache.reset( pidReport );

        } catch ( Exception e ) {

            logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) );

        }

        return ( ObjectNode ) crioPidCache.getCachedObject( );

    }

    public String buildCrioFileListing(
            String containerId,
            String path
    ) {

        logger.debug( "Entered " );
        var listing = "";

        try {

            var script = List.of( "#!/bin/bash", "crictl exec " + containerId + " ls -l " + path );

            logger.debug( "script: {}", script );

            // run as root to pick up docker and kubelet filesystems
            var criInspectOutput = osCommandRunner.runUsingRootUser(
                    "crio-inspect",
                    script );

            listing = CsapApis.getInstance( ).application( ).check_for_stub( criInspectOutput, "crio/crictl-ls.txt" );

        } catch ( Exception e ) {

            logger.error( "Failed to get listing: {}", CSAP.buildCsapStack( e ) );
            ;

        }

        return listing;

    }

    public String getCrioFileContents(
            String containerId,
            String path,
            long maxEditSize
    ) {

        logger.debug( "Entered " );
        var listing = "";

        try {

            var script = List.of( "#!/bin/bash", "crictl exec " + containerId + " tail -c " + Long.toString(
                    maxEditSize ) + " " + path );

            logger.debug( "script: {}", script );

            // run as root to pick up docker and kubelet filesystems
            var criInspectOutput = osCommandRunner.runUsingRootUser(
                    "crio-inspect",
                    script );

            listing = CsapApis.getInstance( ).application( ).check_for_stub( criInspectOutput,
                    "crio/crictl-inspect-calico.json" );

        } catch ( Exception e ) {

            logger.error( "Failed to get listing: {}", CSAP.buildCsapStack( e ) );
            ;

        }

        return listing;

    }

    public ObjectNode getCrioInspect( String id ) {

        logger.debug( "Entered " );

        var inspectReport = jsonMapper.createObjectNode( );

        try {

            // run as root to pick up docker and kubelet filesystems
            var criInspectOutput = osCommandRunner.runUsingRootUser( "crio-inspect", osCommands.getCriInspect(
                    id ) );

            // logger.info( "criPsOutput: {}", criInspectOutput ) ;

            criInspectOutput = CsapApis.getInstance( ).application( ).check_for_stub( criInspectOutput,
                    "crio/crictl-inspect-calico.json" );

            inspectReport = ( ObjectNode ) jsonMapper.readTree( criInspectOutput );

        } catch ( Exception e ) {

            logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) );
            inspectReport.put( "error", e.getMessage( ) );
            inspectReport.put( "reason", CSAP.buildCsapStack( e ) );

        }

        return inspectReport;

    }

    private volatile CsapSimpleCache diskStatisticsCache = null;

    public ObjectNode getCachedFileSystemInfo( ) {

        logger.debug( "Entered " );

        if ( diskStatisticsCache == null ) {

            diskStatisticsCache = CsapSimpleCache.builder(
                    30,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "Disk Statistics" );
            diskStatisticsCache.expireNow( );

        }

        // Use cache
        if ( !diskStatisticsCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  DF cache   *******\n\n" );

            return ( ObjectNode ) diskStatisticsCache.getCachedObject( );

        }

        // Lets refresh cache
        logger.debug( "\n\n***** Refreshing df cache   *******\n\n" );

        try {

            // run as root to pick up docker and kubelet filesystems
            String dfResult = osCommandRunner.runUsingRootUser( "df-collect", osCommands.getDiskUsageSystem( ) );

            dfResult = CsapApis.getInstance( ).application( ).check_for_stub( dfResult, "linux/df-run-as-root.txt" );

            logger.debug( "dfResult: {}", dfResult );

            String[] dfLines = dfResult.split( LINE_SEPARATOR );

            ObjectNode svcToStatMap = jsonMapper.createObjectNode( );

            int lastCount = 0;

            for ( int i = 0; i < dfLines.length; i++ ) {

                String curline = dfLines[ i ].trim( );
                String[] cols = curline.split( " ", 7 );

                if ( cols.length < 7 || !cols[ 6 ].startsWith( "/" ) ) {

                    logger.debug( "Skipping line: {}", curline );
                    continue;

                }

                ObjectNode fsNode = svcToStatMap.putObject( cols[ 6 ] );

                fsNode.put( "dev", cols[ 0 ] );
                fsNode.put( "type", cols[ 1 ] );
                fsNode.put( "sized", cols[ 2 ] );
                fsNode.put( "used", cols[ 3 ] );
                fsNode.put( "avail", cols[ 4 ] );
                fsNode.put( "usedp", cols[ 5 ] );
                fsNode.put( "mount", cols[ 6 ] );
                lastCount++;

            }

            diskCount = lastCount;

            diskStatisticsCache.reset( svcToStatMap );

        } catch ( Exception e ) {

            logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) );

        }

        return ( ObjectNode ) diskStatisticsCache.getCachedObject( );

    }

    long lastSummaryTimeStamp = 0;
    ObjectNode summaryCacheNode;

    public ObjectNode getHostSummary( ) {

        // Use cache
        if ( System.currentTimeMillis( ) - lastSummaryTimeStamp < 1000 * 90 ) {

            logger.debug( "\n\n***** ReUsing  Summary cache   *******\n\n" );

            return summaryCacheNode;

        }

        var summaryNode = jsonMapper.createObjectNode( );
        //
        var parmList = Arrays.asList( "bash", "-c", "cat /etc/redhat-release" );
        String commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null,
                600, 10, null );

        logger.debug( "redhat release commandResult: {} ", commandResult );

        commandResult = OsCommandRunner.trimHeader( commandResult );
        // if (psResult.contains(LINE_SEPARATOR))
        // psResult = psResult.substring(
        // psResult.indexOf(LINE_SEPARATOR +1 ));
        summaryNode.put( "redhat", commandResult );

        // w provides uptime and logged in users
        // parmList = Arrays.asList("bash", "-c", "w");
        parmList = Arrays.asList( "bash", "-c", "uptime" );
        commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
                null );

        logger.debug( "uptime commandResult: {} ", commandResult );

        commandResult = OsCommandRunner.trimHeader( commandResult );
        summaryNode.put( "uptime", commandResult );

        //
        parmList = Arrays.asList( "bash", "-c", "uname -sr" );
        commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
                null );

        logger.debug( "uname commandResult: {} ", commandResult );

        commandResult = OsCommandRunner.trimHeader( commandResult );
        summaryNode.put( "uname", commandResult );

        //
        var dfResult = "Failed to run";
        try {
            dfResult = osCommandRunner.runUsingDefaultUser( "about-disk-usage", osCommands.getDiskUsageAbout( ) );
            dfResult = CsapApis.getInstance( ).application( ).check_for_stub( dfResult, "linux/df-about.txt" );
            logger.debug( "df commandResult: {} ", dfResult );
            dfResult = OsCommandRunner.trimHeader( dfResult );

        } catch ( IOException e ) {
            logger.warn( "Failed collecting disk: {}", CSAP.buildCsapStack( e ) );
        }
        summaryNode.put( "df", dfResult );
//        var dfResult = osCommandRunner.executeString(
//                osCommands.getDiskUsageAbout( ),
//                new File( "." ), null, null, 600, 10,
//                null );


        summaryCacheNode = summaryNode;
        lastSummaryTimeStamp = System.currentTimeMillis( );
        return summaryNode;

    }

    Format hostReportDateFormat = new SimpleDateFormat( "HH:mm:ss" );

    /**
     * AgentStatus: core method for accessing host state
     */
    @Timed( "csap.host-status" )
    public JsonNode getHostRuntime( )
            throws IOException,
            JsonParseException,
            JsonMappingException {

        // update services status if needed
        checkForProcessStatusUpdate( );

        // var agent = CsapApis.getInstance().application().flexFindFirstInstance(
        // "csap-agent" ) ;
        // logger.info( "agent rss: {}", agent.getDefaultContainer().getRssMemory() );

        // updated service artifacts if needed
        CsapApis.getInstance( ).application( ).updateServiceTimeStamps( );

        var hostReport = jsonMapper.createObjectNode( );

        // add time stamp
        hostReport.put( "timeStamp", hostReportDateFormat.format( new Date( ) ) );

        if ( serviceManager != null ) {

            hostReport.put( "serviceOpsQueue", serviceManager.getOpsQueued( ) );

        }

        var hostMetricsReport = CsapApis.getInstance( ).application( ).healthManager( )
                .build_host_status_using_cached_data( );

        try {

            var hostCollector = CsapApis.getInstance( ).application( ).metricManager( ).getOsSharedCollector( );

            if ( hostCollector != null ) {

                var ioReport = hostMetricsReport.putObject( "network" );
                ioReport.put( "network-sent-mb", hostCollector.latestNetworkTransmitted( ) );
                ioReport.put( "network-receive-mb", hostCollector.latestNetworkReceived( ) );
                ioReport.put( "sockets-active", hostCollector.latestNetworkConnections( ) );
                ioReport.put( "sockets-close-wait", hostCollector.latestNetworkWait( ) );
                ioReport.put( "sockets-time-wait", hostCollector.latestNetworkTimeWait( ) );

            }

            hostMetricsReport.put( "du", collectCsapFolderDiskAndCache( ) );

            hostMetricsReport.set( IO_UTIL_IN_PERCENT, device_utilization( ) );
            hostMetricsReport.set( "ioTotalInMb", diskReport( ) );

            hostMetricsReport.set( "vmLoggedIn", getVmLoggedIn( ) );

            ObjectNode dfNode = getCachedFileSystemInfo( );
            ObjectNode dfFilterNode = jsonMapper.createObjectNode( );

            if ( dfNode != null ) {

                for ( JsonNode node : dfNode ) {

                    dfFilterNode.put( node.path( "mount" ).asText( ), node
                            .path( "usedp" ).asText( ) );

                }

                hostMetricsReport.set( "df", dfFilterNode );

            }

            if ( getReadOnlyFs( ) != null ) {

                hostMetricsReport.set( "readOnlyFS", getReadOnlyFs( ) );

            }

            if ( getMemoryAvailbleLessCache( ) < 0 ) {

                logger.error( "Get mem is invalid: " + getCachedMemoryMetrics( ) );
                hostMetricsReport.put( "memoryAggregateFreeMb", -1 );

            } else {

                hostMetricsReport.put( "memoryAggregateFreeMb",
                        getMemoryAvailbleLessCache( ) );

            }

        } catch ( Exception e ) {

            logger.warn( "Failed to get runtime time info", e );

        }

        hostReport.set( HostKeys.hostStats.jsonId, hostMetricsReport );

        try {

            hostReport.set( HostKeys.unregisteredServices.jsonId, null );

            hostReport.set( HostKeys.services.jsonId, build_averaged_service_statistics( ) );

            hostReport.set( HostKeys.lastCollected.jsonId, null );

            if ( CsapApis.getInstance( ).application( ).rootProjectEnvSettings( ).areMetricsConfigured( ) ) {

                hostReport.set( HostKeys.lastCollected.jsonId, buildRealTimeCollectionReport( ) );

            }

            hostReport.set( HostKeys.unregisteredServices.jsonId, jsonMapper.convertValue(
                    findUnregisteredContainerNames( ), ArrayNode.class ) );

        } catch ( Exception e ) {

            logger.error( "Failed getting collection average: {}", CSAP.buildCsapStack( e ) );

        }

        return hostReport;

    }

    public List< String > findUnregisteredContainerNames( ) {

        return ContainerProcess.buildUnregisteredSummaryReport( processMapper.getLatestDiscoveredContainers( ) );

    }

    private ObjectNode build_averaged_service_statistics( ) {

        logger.debug( "got here" );

        final int serviceSampleSize = CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                .getLimitSamples( );

        ObjectNode cached_service_statistics = load_cached_service_statistics( serviceSampleSize );

        // note that admin api also uses this method - but average of data
        // cannot be used.

        ObjectNode servicesJson = jsonMapper.createObjectNode( );
        CsapApis.getInstance( ).application( ).getActiveProject( )
                .getServicesWithKubernetesFiltering( CsapApis.getInstance( ).application( ).getCsapHostName( ) )
                .forEach( serviceInstance -> {

//					var serviceId = serviceInstance.getServiceName_Port( ) ;
                    var serviceId = serviceInstance.getName( );

                    // get the latest collection - use it as the default collection value.
                    ObjectNode latestServiceStats = serviceInstance.buildRuntimeState( );

                    // handle memory

                    logger.debug( "Before: {}, stats: {}", serviceId, latestServiceStats.toString( ) );
                    // if ( serviceInstance.getName().equals( "csap-agent" ) ) {
                    // logger.info( "sample count: {} before csap-agent stats: {}",
                    // numSamplesToTake, CSAP.jsonPrint( latestServiceStats ) ) ;
                    // }

                    var needToHandleRssMbConversion = true;

                    var numSamplesToTake = serviceSampleSize;

                    if ( serviceInstance.isAggregateContainerMetrics( ) ) {

                        // os metrics are aggregated across all containers - not possible to pull and
                        // average
                        numSamplesToTake = 1;

                    }

                    if ( cached_service_statistics != null && (numSamplesToTake > 1) ) {

                        var containerStatuses = serviceInstance.getContainerStatusList( );

                        for ( int containerIndex = 0; containerIndex < containerStatuses.size( ); containerIndex++ ) {

                            var containerLabel = containerStatuses.get( containerIndex ).getContainerLabel( );

                            if ( StringUtils.isNotEmpty( containerLabel ) && containerLabel.equals(
                                    "kube-apiserver" ) ) {

                                logger.debug( "containerLabel: {}, numSamplesToTake: {}", containerLabel,
                                        numSamplesToTake );

                            }
                            // boolean isKubernetes = serviceStatus.path( ClusterType.CLUSTER_TYPE ).asText(
                            // "not" ).equals(
                            // ClusterType.KUBERNETES.getJson() );

                            // String serviceKey = serviceName;
                            // if ( isKubernetes ) {
                            // serviceKey += "-" + (i + 1);
                            // kubernetes_services.put( serviceKey, serviceName ); // stored for cleanup
                            // }

                            logger.debug( "Using rolling averages for service meterics." );

                            for ( OsProcessEnum collectedMetric : OsProcessEnum.values( ) ) {

                                if ( collectedMetric == OsProcessEnum.diskUsedInMb ) {

                                    // Disk can get large very quickly. Always report
                                    // the last collected
                                    continue;

                                }

                                String statsPath = "/" + HostCollector.DATA_JSON + "/" + collectedMetric.value + "_"
                                        + serviceInstance.getPerformanceId( );

                                if ( serviceInstance.is_cluster_kubernetes( ) ) {

                                    statsPath += "-" + (containerIndex + 1);

                                }

                                JsonNode serviceData = cached_service_statistics.at( statsPath );

                                if ( !serviceData.isArray( ) ) {

                                    logger.debug( "{} cached data not found", serviceInstance.getName( ) );

                                } else {
                                    // if ( serviceInstance.getName().equals( "csap-agent" ) && collectedMetric ==
                                    // OsProcessEnum.rssMemory ) {
                                    // logger.info( "csap-agent rss cached: {}", CSAP.jsonPrint( serviceData ) ) ;
                                    // }

                                    long total = 0;

                                    for ( int i = 0; i < serviceData.size( ); i++ ) {

                                        total += serviceData.get( i ).asLong( );

                                    }

                                    long average = (total / serviceData.size( ));
                                    logger.debug( "{} Total: {} , Average: {}", collectedMetric.value, total,
                                            average );
                                    // latestServiceStats.put( os.value, average );
                                    JsonNode container = latestServiceStats.at( ContainerState.containerPath(
                                            containerIndex ) );

                                    if ( container.isObject( ) ) {

                                        if ( collectedMetric == OsProcessEnum.rssMemory ) {

                                            needToHandleRssMbConversion = false;

                                        }

                                        (( ObjectNode ) container).put( collectedMetric.value, average );
                                        (( ObjectNode ) container).put( HostKeys.numberSamplesAveraged.jsonId,
                                                serviceData.size( ) );

                                    } else {

                                        latestServiceStats.put( "error", "Did not find containers" );

                                    }

                                }

                            }

                        }

                    }

                    if ( needToHandleRssMbConversion ) {

                        logger.debug( "Updating rss to mb: collection is stored in kb" );

                        for ( int containerIndex = 0; containerIndex < serviceInstance.getContainerStatusList( )
                                .size( ); containerIndex++ ) {

                            JsonNode container = latestServiceStats.at( ContainerState.containerPath(
                                    containerIndex ) );
                            (( ObjectNode ) container).put( OsProcessEnum.rssMemory.value,
                                    container.path( OsProcessEnum.rssMemory.value ).asLong( -1 ) / 1024 );

                        }

                    }

                    logger.debug( "After: {}, stats: {}", serviceId, latestServiceStats.toString( ) );
                    // if ( serviceInstance.getName().equals( "csap-agent" ) ) {
                    // logger.info( "after csap-agent stats: {}", CSAP.jsonPrint( latestServiceStats
                    // ) ) ;
                    // }

                    servicesJson.set( serviceId, latestServiceStats );

                } );

        return servicesJson;

    }

    private ObjectNode load_cached_service_statistics(
            final int numSamplesToTake
    ) {

        ObjectNode cached_service_statistics = null;

        if ( !CsapApis.getInstance( ).application( ).isAdminProfile( ) ) {

            OsProcessCollector osProcessCollector;

            if ( CsapApis.getInstance( ).application( ).isJunit( ) && CsapApis.getInstance( ).application( )
                    .metricManager( ).getOsProcessCollector( -1 ) == null ) {

                osProcessCollector = new OsProcessCollector( CsapApis.getInstance( ), 30, false );

                logger.warn( "\n\n\n JUNIT DETECTED - injecting stubbed OsProcessCollector" );

                // osProcessCollector.testCollection();
            } else {

                osProcessCollector = CsapApis.getInstance( ).application( ).metricManager( ).getOsProcessCollector(
                        -1 );

            }

            cached_service_statistics = osProcessCollector
                    .getCollection( numSamplesToTake, 0, OsProcessCollector.ALL_SERVICES );

        }

        logger.debug( "Adding: {} ", cached_service_statistics );
        return cached_service_statistics;

    }

    public String getLastCollected(
            ServiceInstance service,
            String searchKey
    ) {

        long result = 0;

        try {

            ServiceCollector applicationCollector = CsapApis.getInstance( ).application( ).metricManager( )
                    .getServiceCollector( CsapApis.getInstance( ).application( )
                            .metricManager( ).firstJavaCollectionInterval( ) );

            String[] serviceArray = {
                    service.getServiceName_Port( )
            };
            ObjectNode serviceData = applicationCollector.buildCollectionReport( false, serviceArray, 1, 0,
                    "custom" );
            logger.debug( "Collected: {}", serviceData );

            result = serviceData.get( "data" ).get( searchKey ).get( 0 ).asLong( );

        } catch ( Exception e ) {

            logger.warn( "{} Did not find: {}, \n {}",
                    service.getServiceName_Port( ), searchKey, CSAP.buildCsapStack( e ) );

        }

        return Long.toString( result );

    }

    // For all services.
    public ObjectNode buildLatestCollectionReport( ) {

        var collectionReport = jsonMapper.createObjectNode( );

        if ( !CsapApis.getInstance( ).application( ).isAdminProfile( ) && CsapApis.getInstance( ).application( )
                .rootProjectEnvSettings( ).areMetricsConfigured( ) ) {

            OsSharedResourcesCollector osSharedCollector = CsapApis.getInstance( ).application( ).metricManager( )
                    .getOsSharedCollector(
                            CsapApis.getInstance( ).application( ).metricManager( ).firstHostCollectionInterval( ) );

            if ( osSharedCollector != null ) {

                var osSharedReport = osSharedCollector.buildCollectionReport( false, null, 1, 0 );
                collectionReport.set( MetricCategory.osShared.json( ), osSharedReport.path( "data" ) );

            } else {

                collectionReport.put( MetricCategory.osShared.json( ), "collector-not-available" );

            }

            OsProcessCollector osProcessCollector = CsapApis.getInstance( ).application( ).metricManager( )
                    .getOsProcessCollector( CsapApis.getInstance( ).application( ).metricManager( )
                            .firstServiceCollectionInterval( ) );
            // fullCollectionJson.set( MetricCategory.osProcess.json(),
            // serviceCollector.getCSVdata(
            // false, serviceNames, 1, 0 ).get( "data" ) );
            // this will grab all entries stored

            if ( osProcessCollector != null ) {

                // get all services, with the last collected item
                collectionReport.set( MetricCategory.osProcess.json( ),
                        osProcessCollector.buildCollectionReport( false,
                                osProcessCollector.getAllCollectedServiceNames( ),
                                1, 0 ).path( "data" ) );

            } else {

                collectionReport.set( MetricCategory.osProcess.json( ), null );

            }

            ServiceCollector serviceCollector = CsapApis.getInstance( ).application( ).metricManager( )
                    .getServiceCollector( CsapApis.getInstance( ).application( ).metricManager( )
                            .firstJavaCollectionInterval( ) );

            if ( serviceCollector != null ) {

                String[] javaServices = serviceCollector.getJavaServiceNames( );

                ObjectNode latestServiceReport = serviceCollector.buildCollectionReport( false, javaServices, 1, 0 );

                if ( latestServiceReport.has( "data" ) ) {

                    collectionReport.set( MetricCategory.java.json( ), latestServiceReport.get( "data" ) );

                } else {

                    logger.warn( "java  collection does not contain data: {}. \n\t Services: {}",
                            latestServiceReport.toString( ), Arrays.asList( javaServices ) );
                    collectionReport.set( MetricCategory.java.json( ), latestServiceReport );

                }

                ObjectNode serviceReports = collectionReport.putObject( MetricCategory.application.json( ) );

                CsapApis.getInstance( ).application( ).getActiveProject( )
                        .getServicesOnHost( CsapApis.getInstance( ).application( ).getCsapHostName( ) )
                        .filter( ServiceInstance::hasServiceMeters )
                        .forEach( serviceInstance -> {

                            String[] serviceArray = {
                                    serviceInstance.getName( )
                            };
                            serviceReports.set( serviceInstance.getName( ),
                                    serviceCollector.buildCollectionReport( false, serviceArray, 1, 0, "custom" )
                                            .get( "data" ) );

                        } );

                logger.debug( "serviceReports: {}", serviceReports );

            } else {

                logger.warn( "serviceCollector is null - verify configuration in definition" );

            }

        } else {

            collectionReport.put( "warning", "VM is in manager mode" );

        }

        return collectionReport;

    }

    /**
     * Gets the values for the real time meters defined in Application.json
     *
     * @return
     */
    public ObjectNode buildRealTimeCollectionReport( ) {

        ObjectNode configCollection = jsonMapper.createObjectNode( );

        if ( !CsapApis.getInstance( ).application( ).isAdminProfile( ) ) {

            ObjectNode latestCollectionReport = buildLatestCollectionReport( );
            logger.debug( "latestCollectionReport: {}", CSAP.jsonPrint( latestCollectionReport ) );

            ObjectNode osSharedReport = configCollection.putObject( MetricCategory.osShared.json( ) );
            ObjectNode processMeterReport = configCollection.putObject( MetricCategory.osProcess.json( ) );
            ObjectNode javaMeterReport = configCollection.putObject( MetricCategory.java.json( ) );
            ObjectNode applicationReport = configCollection.putObject( MetricCategory.application.json( ) );
            ArrayNode realTimeMeterDefinitions = CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                    .getRealTimeMeters( );

            for ( JsonNode realTimeMeterDefn : realTimeMeterDefinitions ) {

                MetricCategory performanceCategory = MetricCategory.parse( realTimeMeterDefn );
                String serviceName = performanceCategory.serviceName( realTimeMeterDefn );

                try {

                    String id = realTimeMeterDefn.get( "id" ).asText( );
                    String[] idComponents = id.split( Pattern.quote( "." ) );
                    String category = idComponents[ 0 ];
                    String attribute = idComponents[ 1 ];
                    logger.debug( "collector: {}, attribute: {} ", category, attribute );
                    // vm. process. jmxCommon. jmxCustom.Service.var
                    // process.topCpu_CsAgent
                    // process.topCpu_test-k8s-csap-reference

                    switch ( performanceCategory ) {

                        case osShared:

                            var latestOsSharedReport = latestCollectionReport.path( category );

                            // logger.debug( "latestOsSharedReport: {}", CSAP.jsonPrint(
                            // latestOsSharedReport ) ) ;

                            if ( attribute.equals( "cpu" ) ) {

                                int totalCpu = CsapApis.getInstance( ).application( ).metricManager( )
                                        .getLatestCpuUsage( );
                                osSharedReport.put( "cpu", totalCpu );

                            } else if ( attribute.equals( "coresActive" ) ) {

                                int totalCpu = CsapApis.getInstance( ).application( ).metricManager( )
                                        .getLatestCpuUsage( );

                                double coresActive = totalCpu * CsapApis.getInstance( ).application( ).healthManager( )
                                        .getCpuCount( ) / 100D;
                                osSharedReport.put( "coresActive", CSAP.roundIt( coresActive, 2 ) );
//								osSharedReport.put( "cpu", totalCpu ) ;
//								osSharedReport.put( "cores", CsapApis.getInstance().application().healthManager().getCpuCount() ) ;

                            } else {

                                osSharedReport.put( attribute,
                                        latestOsSharedReport.at( "/" + attribute + "/0" ).asDouble( 0 ) );

                            }

                            break;

                        case osProcess:
                            String csapId[] = attribute.split( "_" );
                            String osStat = csapId[ 0 ];

                            addOsProcessMeterReport( serviceName, osStat,
                                    latestCollectionReport.path( category ), attribute, processMeterReport );

                            break;

                        case java:
                            // jmxCommon.sessionsActive_test-k8s-csap-reference
                            String javaId[] = attribute.split( "_" );
                            String javaStat = javaId[ 0 ];

                            buildJavaMeterReport( serviceName, javaStat, attribute,
                                    latestCollectionReport.path( category ),
                                    javaMeterReport );

                            break;

                        case application:

                            attribute = idComponents[ 2 ];
                            String qualifiedName = attribute;

                            ServiceInstance service = CsapApis.getInstance( ).application( )
                                    .flexFindFirstInstanceCurrentHost( serviceName );
                            if ( service == null ) {

                                logger.debug( "Unable to locate: {}. Assumed not deployed on host {}", serviceName,
                                        CSAP.jsonPrint( realTimeMeterDefn ) );
                                continue;

                            }

                            var podIndex = 0;
                            for ( ContainerState csapContainer : service.getContainerStatusList( ) ) {

                                if ( service.is_cluster_kubernetes( ) ) {

                                    podIndex++;
                                    qualifiedName = attribute + "_" + serviceName + "-" + podIndex;

                                }

                                logger.debug( "{} podIndex: {} qualifiedName: {}", serviceName, podIndex,
                                        qualifiedName );

                                if ( !latestCollectionReport.get( category ).has( serviceName ) ) {

                                    continue;

                                }

                                if ( !latestCollectionReport.get( category ).get( serviceName ).has(
                                        qualifiedName ) ) {

                                    continue;

                                }

                                if ( !applicationReport.has( serviceName ) ) {

                                    applicationReport.putObject( serviceName );

                                }

                                ObjectNode serviceReport = ( ObjectNode ) applicationReport.path( serviceName );

                                int hostTotal = latestCollectionReport.path( category ).path( serviceName ).path(
                                                qualifiedName )
                                        .get( 0 ).asInt( 0 );

                                hostTotal += serviceReport.path( attribute ).asInt( 0 );

                                serviceReport.put( attribute, hostTotal );

                            }
                            break;

                        default:
                            logger.warn( "Unexpected category type: {}", category );

                    }

                } catch ( Exception e ) {

                    logger.error( "Failed parsing: {}, \n {}",
                            realTimeMeterDefn,
                            CSAP.buildCsapStack( e ) );

                }

            }

        } else {

            configCollection.put( "warning", "VM is in manager mode" );

        }

        return configCollection;

    }

    private void buildJavaMeterReport(
            String javaServiceName,
            String javaStat,
            String attribute,
            JsonNode javaCollectionReport,
            ObjectNode filteredReport
    ) {

        logger.debug( "{} javaStat: {}, attribute: {}", javaServiceName, javaStat, attribute );

        if ( MetricCategory.isAllServices( javaServiceName ) ) {

            Iterable< Map.Entry< String, JsonNode > > iterable = ( ) -> javaCollectionReport.fields( );
            int allInstanceTotal = StreamSupport.stream( iterable.spliterator( ), false )
                    .filter( osEntry -> osEntry.getKey( ).startsWith( javaStat ) )
                    .mapToInt( osEntry -> osEntry.getValue( ).get( 0 ).asInt( 0 ) )
                    .sum( );
            logger.debug( "Total for {} is {}", javaServiceName, allInstanceTotal );
            filteredReport.put( attribute, allInstanceTotal );

        } else {

            ServiceInstance serviceInstance = CsapApis.getInstance( ).application( ).findServiceByNameOnCurrentHost(
                    javaServiceName );

            if ( serviceInstance != null ) {

                boolean foundData = false;
                int allInstanceTotal = 0;

                if ( serviceInstance.is_cluster_kubernetes( ) ) {

                    for ( int container = 1; container <= serviceInstance.getContainerStatusList( )
                            .size( ); container++ ) {

                        ContainerState containerState = serviceInstance.getContainerStatusList( ).get( container - 1 );
                        String stat_serviceName_id = javaStat + "_" + serviceInstance.getName( ) + "-" + container;

                        if ( containerState.isActive( ) && javaCollectionReport.has( stat_serviceName_id ) ) {

                            foundData = true;
                            allInstanceTotal += javaCollectionReport.path( stat_serviceName_id ).path( 0 ).asInt( 0 );

                        }

                    }

                } else {

                    String javaIdWithPort = javaStat + "_" + serviceInstance.getName( );

                    if ( javaCollectionReport.has( javaIdWithPort ) ) {

                        foundData = true;
                        allInstanceTotal += javaCollectionReport.path( javaIdWithPort ).path( 0 ).asInt( 0 );

                    }

                }

                if ( foundData ) {

                    filteredReport.put( attribute, allInstanceTotal );

                } else {

                    logger.debug( "Did not find a match for {} on host", attribute );

                }

            }

        }

        return;

    }

    private void addOsProcessMeterReport(
            String csapServiceName,
            String osStat,
            JsonNode processCollectionReport,
            String metric_servicename,
            ObjectNode processMeterReport
    ) {

        // process.topCpu_CsAgent
        // process.topCpu_test-k8s-csap-reference

        if ( processCollectionReport.has( metric_servicename ) ) {

            // typical
            processMeterReport.put( metric_servicename,
                    processCollectionReport.get( metric_servicename ).path( 0 ).asInt( 0 ) );

        } else {

            if ( MetricCategory.isAllServices( csapServiceName ) ) {

                Iterable< Map.Entry< String, JsonNode > > iterable = ( ) -> processCollectionReport.fields( );
                int allInstanceTotal = StreamSupport.stream( iterable.spliterator( ), false )
                        .filter( osEntry -> osEntry.getKey( ).startsWith( osStat ) )
                        .mapToInt( osEntry -> osEntry.getValue( ).path( 0 ).asInt( 0 ) )
                        .sum( );
                logger.debug( "Total for {} is {}", csapServiceName, allInstanceTotal );
                processMeterReport.put( metric_servicename, allInstanceTotal );

            } else {

                // handle kubernetes with multiple instances
                boolean isFoundOneOrMoreRunning = false;
                int allInstanceTotal = 0;

                // kubernetes
                ServiceInstance serviceInstance = CsapApis.getInstance( ).application( ).findServiceByNameOnCurrentHost(
                        csapServiceName );

                if ( serviceInstance != null && serviceInstance.is_cluster_kubernetes( ) ) {

                    for ( int container = 1; container <= serviceInstance.getContainerStatusList( )
                            .size( ); container++ ) {

                        ContainerState containerState = serviceInstance.getContainerStatusList( ).get( container - 1 );
                        String stat_serviceName_id = osStat + "_" + serviceInstance.getName( ) + "-" + container;

                        if ( containerState.isActive( ) && processCollectionReport.has( stat_serviceName_id ) ) {

                            isFoundOneOrMoreRunning = true;
                            allInstanceTotal += processCollectionReport.path( stat_serviceName_id ).path( 0 ).asInt(
                                    0 );

                        }

                    }

                }

                // @formatter:off
//				int			allInstanceTotal	= CsapApis.getInstance().application().getServicesOnHost().stream()
//					.filter( serviceinstance -> serviceinstance.getServiceName().matches( csapServiceName ) )
//					.mapToInt( serviceinstance -> {
//
//
//							String stat_serviceName_id = osStat + "_" + serviceinstance.getServiceName() + "_"
//									+ serviceinstance.getPort() ;
//
//							// logger.info("Checking for: {}", serviceAndPort);
//							if ( !processCollectionReport.has( stat_serviceName_id ) ) {
//								// logger.warn( "Did not find attribute: {}",
//								// attribute );
//								return 0 ;
//							}
//							isFoundAMatch.set( true );
//							int lastCollectedForPort = processCollectionReport.get( stat_serviceName_id )
//								.get( 0 ).asInt( 0 ) ;
//							return lastCollectedForPort ;
//
//						} )
//					.sum() ;

                if ( isFoundOneOrMoreRunning ) {

                    processMeterReport.put( metric_servicename, allInstanceTotal );

                } else {

                    logger.debug( "Did not find a match for {}", metric_servicename );

                }

                // @formatter:on
            }

        }

        return;

    }

    public ServiceOsManager getServiceManager( ) {

        return serviceManager;

    }

    public static String LINE_SEPARATOR = System.getProperty( "line.separator" );

    // triggered after deployment activities
    private void scheduleDiskUsageCollection( ) {

        // rawDuAndDfLinuxOutput = "";
        if ( !CsapApis.getInstance( ).application( ).isAdminProfile( ) ) {

            if ( !intenseOsCommandExecutor.isShutdown( ) ) {

                try {

                    if ( logger.isDebugEnabled( ) ) {

                        logger.debug( "{}", CSAP.buildCsapStack( new Exception( "scheduling cache refresh" ) ) );

                    }

                    intenseOsCommandExecutor.execute( ( ) -> collect_disk_and_linux_package( ) );

                } catch ( Exception e ) {

                    logger.warn( "Failed to scheduler os command collection {}", CSAP.buildCsapStack( e ) );

                }

            } else {

                logger.info(
                        "Skipping due to intenseOsCommandExecutor is not running, assuming shutdown in progress" );

            }

        }

    }

    private volatile String diskUsageForServicesCache = "";

    /**
     * ==== Disk is collected for both services and core file systems
     */

    private boolean isSmemCommandAvailable = (new File( "/usr/bin/smem" )).exists( );

    private volatile String cachedPssMemoryStatistics = "";

    private void collectPssMemoryStatistics( ) {

        if ( !CsapApis.getInstance( ).application( ).environmentSettings( ).isCollectPssMemory( ) ) {

            return;

        }

        logger.debug( "***X Starting" );

        if ( isSmemCommandAvailable ) {


            try {

                cachedPssMemoryStatistics = osCommandRunner
                        .runUsingRootUser(
                                "service-smem",
                                getOsCommands( ).getPssMemory( ) );

                logger.debug( "output from: {}  , \n{}",
                        getOsCommands( ).getPssMemory( ),
                        cachedPssMemoryStatistics );

            } catch ( Exception e ) {

                logger.info( "Failed to collect pidstat info: {} , \n reason: {}",
                        getOsCommands( ).getPssMemory( ),
                        CSAP.buildCsapStack( e ) );

            }

        }

        cachedPssMemoryStatistics = CsapApis.getInstance( ).application( ).check_for_stub( cachedPssMemoryStatistics, "linux/ps-smem.txt" );

        return;

    }


    boolean printDiskOnce = true;

    private void collect_disk_and_linux_package( ) {

        var diskAndPackageTimer = CsapApis.getInstance( ).metrics( ).startTimer( );

        collectPssMemoryStatistics( );

        // Updates service count
        getLinuxServices( );

        // updates chefcount

        getChefListing( );


        // Updates package count
        getLinuxPackages( );

        // Updates network cout
        networkInterfaces( );

        // Updates port count
        // socketConnections() ;

        var diskTimer = CsapApis.getInstance( ).metrics( ).startTimer( );

        logger.debug( "\n\n updating caches \n\n" );

        StringBuilder diskCollection;

        try {

            diskCollection = new StringBuilder( "\n" );

            // String[] diskUsageScript = diskUsageScriptTemplate.clone();
            var servicePaths = CsapApis.getInstance( ).application( )
                    .servicesOnHost( )
                    .map( ServiceInstance::getDiskUsagePath )
                    .distinct( )
                    .collect( Collectors.joining( " " ) );

            if ( printDiskOnce ) {

                logger.info( "Service disk locations\n{}", servicePaths );

            }

            List< String > diskUsageScript = osCommands.getServiceDiskUsage( servicePaths );

            // Step 1 - collect disk usage under csap processing. Some files may
            // be privelged - use root if available
            diskCollection.append( osCommandRunner.runUsingRootUser( "service-disk-usage", diskUsageScript ) );

            // Step 2 - collect disk usage use df output, services can specify
            // device. Use default user to avoid seeing docker mounts
            List< String > diskFileSystemScript = osCommands.getServiceDiskUsageDf( );
            diskCollection.append(
                    osCommandRunner.runUsingDefaultUser(
                            "service-disk-usage-df",
                            diskFileSystemScript ) );

            // Step 3 - disk usage from docker
            diskCollection.append( collectDockerDiskUsage( ) );

            logger.debug( "service-disk-filesystem: {} \n\n diskFileSystemScript: {} \n\n diskCollection: {}",
                    diskUsageScript,
                    diskFileSystemScript,
                    diskCollection.toString( ) );

            if ( printDiskOnce ) {

                printDiskOnce = false;

                logger.info(
                        "service-disk-filesystem: {} \n\n diskFileSystemScript: {} \n\n diskCollection: {}",
                        diskUsageScript,
                        diskFileSystemScript,
                        diskCollection.toString( ) );

            }

            if ( Application.isRunningOnDesktop( ) ) {

                diskCollection = new StringBuilder( diskUsageForServicesCache );

            }

            // Finally - update the cache
            diskUsageForServicesCache = diskCollection.toString( );

        } catch ( Exception e ) {

            logger.error( "Failed getting disk: {}", CSAP.buildCsapStack( e ) );

        }

        //
        // Collect docker container size
        //

        CsapApis.getInstance( ).metrics( ).stopTimer( diskTimer, COLLECT_OS + "service-folder-size" );

        CsapApis.getInstance( ).metrics( ).stopTimer( diskAndPackageTimer, "csap." + COLLECT_OS
                + ".disk-and-devices" );

    }

    private String collectDockerDiskUsage( ) {

        if ( !CsapApis.getInstance( ).isContainerProviderInstalledAndActive( ) ) {

            logger.debug( "Skipping docker collection because docker integration is disabled" );
            return "";

        }

        return CsapApis.getInstance( ).containerIntegration( ).collectContainersDiskUsage(
                CsapApis.getInstance( ).application( ) );

    }

    private volatile List< ContainerProcess > docker_containerProcesses = new ArrayList<>( );
    ;

    public List< ContainerProcess > getDockerContainerProcesses( ) {

        return docker_containerProcesses;

    }

    public void buildDockerPidMapping( ) {

        if ( CsapApis.getInstance( ).isContainerProviderInstalledAndActive( ) || CsapApis
                .getInstance( ).application( ).isJunit( ) ) {

            if ( CsapApis.getInstance( ).containerIntegration( ) != null ) {

                docker_containerProcesses = CsapApis.getInstance( ).containerIntegration( )
                        .build_process_info_for_containers( );

            }

            if ( Application.isRunningOnDesktop( ) ) {

                // dumped via HostDashboard, os process tab
                String stubData = CsapApis.getInstance( ).application( ).check_for_stub( "",
                        "linux/ps-docker-list.json" );

                try {

                    docker_containerProcesses = jsonMapper.readValue( stubData,
                            new TypeReference< List< ContainerProcess > >( ) {
                            } );

                    // since stubbed output is used - reset definition to false each time it is
                    // loaded
                    docker_containerProcesses.stream( ).forEach( dockerProcess -> {

                        dockerProcess.setInDefinition( false );

                    } );

                } catch ( Exception e ) {

                    logger.warn( "Failed parsing stub data: {}", CSAP.buildCsapStack( e ) );

                }

                // logger.info( "Stub Data: {}", WordUtils.wrap( stubContainers.toString(), 100)
                // );

            }

        }

    }

    private String lastProcessStatsCollected( ) {

        // pcpu,rss,vsz,nlwp,ruser,pid,nice,ppid,args
        return ( String ) processStatisticsCache.getCachedObject( );

    }

    public void expireAndUpdateProcessListing( ) {

        logger.info( CsapApplication.testHeader( ) );
        processStatisticsCache.expireNow( );
        checkForProcessStatusUpdate( );

    }

    private volatile CsapSimpleCache processStatisticsCache = null;
    AtomicInteger processScanCount = new AtomicInteger( 0 );

    public boolean isProcessStatusInitialized( ) {

        return processScanCount.get( ) >= 2;

    }

    public boolean wait_for_initial_process_status_scan(
            int maxSeconds
    ) {

        logger.debug( CSAP.buildCsapStack( new Exception( "startup-stack-display" ) ) );

        if ( isProcessStatusInitialized( ) ) {

            return true;

        }

        // possible race condition on initial lod
        int attempts = 0;

        while ( attempts < maxSeconds ) {

            attempts++;

            try {

                logger.info( "Waiting for {} of 3 process scans to complete: attempt {} of {}",
                        processScanCount.get( ),
                        attempts,
                        maxSeconds );

                TimeUnit.SECONDS.sleep( 2 );

                if ( !isProcessStatusInitialized( ) ) {

                    logger.info( "Triggering secondary ps scan to pickup docker container discovery" );
                    checkForProcessStatusUpdate( );

                }

            } catch ( Exception e ) {

                logger.info( "Wait for ps to complete", CSAP.buildCsapStack( e ) );

            }

            if ( isProcessStatusInitialized( ) )
                break;

        }

        return isProcessStatusInitialized( );

    }

    private ReentrantLock processStatusLock = new ReentrantLock( );

    public void checkForProcessStatusUpdate( ) {

        if ( processStatisticsCache == null ) {

            processStatisticsCache = CsapSimpleCache.builder(
                    9,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "Process Stats" );
            // immediate expiration to force load
            processStatisticsCache.expireNow( );

        }

        if ( !isProcessStatusInitialized( ) ) {

            logger.info( "process scanner not initialized, processScanCount: {}", processScanCount.get( ) );

        }

        logger.debug( "process cache expired: {}", processStatisticsCache.isExpired( ) );

        if ( !processStatisticsCache.isExpired( )
                && isProcessStatusInitialized( ) ) {

            logger.debug( CsapApplication.highlightHeader( "using cached  processStatisticsCache" ) );

        } else if ( processStatusLock.tryLock( ) ) {

            if ( processStatisticsCache.isExpired( )
                    || !isProcessStatusInitialized( ) ) {

                try {

                    //
                    // Note - during initial startup - services may not be loaded yet
                    //

                    logger.debug( CsapApplication.highlightHeader( "refreshing  processStatisticsCache" ) );

                    var allStepsTimer = CsapApis.getInstance( ).metrics( ).startTimer( );

                    CsapApis.getInstance( ).metrics( ).record( COLLECT_OS + "process-status", ( ) -> {

                        var ps_command_output = osCommandRunner.executeString(
                                null,
                                osCommands.getProcessStatus( ) );

//                        logger.info( "ps_command_output: {}", ps_command_output );

                        processStatisticsCache.reset( OsCommandRunner.trimHeader( ps_command_output ) );

                    } );

                    if ( Application.isRunningOnDesktop( ) ) {

                        updateCachesWithTestData( );

                    }

                    CsapApis.getInstance( ).metrics( ).record( COLLECT_OS + "process-details-docker",
                            ( ) -> {

                                buildDockerPidMapping( );

                            } );

                    if ( !CsapApis.getInstance( ).application( ).isApplicationLoaded( ) ) {

                        logger.warn( "application not loaded - deferring service mapping" );
                        processStatisticsCache.expireNow( );

                    } else {

                        CsapApis.getInstance( ).metrics( ).record( OsProcessMapper.MAPPER_TIMER, ( ) -> {

                            processMapper.process_find_all_service_matches(
                                    CsapApis.getInstance( ).application( ).getServicesOnHost( ),
                                    ( String ) processStatisticsCache.getCachedObject( ),
                                    diskUsageForServicesCache,
                                    getDockerContainerProcesses( ) );

                        } );

                        // get podIps and Update pod Ips
                        if ( CsapApis.getInstance( ).isKubernetesInstalledAndActive( ) ) {

                            CsapApis.getInstance( ).metrics( ).record( COLLECT_OS
                                    + "process-map-pod-addresses", ( ) -> {

                                CsapApis.getInstance( ).kubernetes( )
                                        .updatePodIps( CsapApis.getInstance( ).application( ) );

                            } );

                        }

                        if ( !isProcessStatusInitialized( ) ) {

                            //
                            processScanCount.getAndIncrement( );

                        }

                    }

                    CsapApis.getInstance( ).metrics( ).stopTimer( allStepsTimer, "csap." + COLLECT_OS
                            + "process-to-model" );

                } catch ( Exception e ) {

                    logger.warn( "Failed refreshing runtime {}", CSAP.buildCsapStack( e ) );

                } finally {

                    processStatusLock.unlock( );

                }

            } else {

                logger.info( "try lock not expired" );

            }

        } else {

            logger.info( "Failed to get lock" );

        }

        logger.debug( "process cache expired: {}", processStatisticsCache.isExpired( ) );

    }

    CsapSimpleCache csapFsCache;

    private int collectCsapFolderDiskAndCache( ) {

        if ( csapFsCache == null ) {

            csapFsCache = CsapSimpleCache.builder(
                    60,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "CsapFolderDf" );
            csapFsCache.expireNow( );

        }

        if ( (csapFsCache.getCachedObject( ) != null)
                && !csapFsCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  cpuStatisticsCache   *******\n\n" );

        } else {

            logger.debug( "\n\n***** REFRESHING   cpuStatisticsCache   *******\n\n" );
            var timer = CsapApis.getInstance( ).metrics( ).startTimer( );
            var diskPercent = -1;
            var dfOutput = "not-run";

            try {

                dfOutput = osCommandRunner.runUsingDefaultUser( "csap-fs-collect",
                        osCommands.getDiskUsageCsap( ) );

                logger.debug( "dfOutput: \n{}\n---", dfOutput );

                dfOutput = CsapApis.getInstance( ).application( ).check_for_stub( dfOutput, "linux/dfStaging.txt" );

                dfOutput = osCommandRunner.trimHeader( dfOutput );
                var lines = Arrays.stream( dfOutput.split( LINE_SEPARATOR ) )
                        .filter( line -> line.contains( "%" ) )
                        .map( String::trim )
                        .map( line -> line.replace( Matcher.quoteReplacement( "%" ), "" ) )
                        .findFirst( );

                diskPercent = Integer.parseInt( lines.get( ) );

            } catch ( Exception e ) {

                logger.warn( "Failed parsing df output {}", dfOutput );

            }

            CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS + "csap-fs" );
            csapFsCache.reset( diskPercent );

        }

        return ( Integer ) csapFsCache.getCachedObject( );

    }

    /**
     * Full Listing for traping memory usage
     */
    public final static List< String > PS_MEMORY_LIST = Arrays
            .asList( "bash",
                    "-c",
                    "ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed 's/  */ /g' | sed 's/,/ /g' |awk '{ for(i=1;i<=7;i++){$i=$i\",\"}; print }'" );

    public final static List< String > PS_PRIORITY_LIST = Arrays
            .asList( "bash",
                    "-c",
                    "ps -e --sort nice -o nice,pmem,rss,vsz,size,nlwp,ruser,pid,args | sed 's/  */ /g' | sed 's/,/ /g' |awk '{ for(i=1;i<=8;i++){$i=$i\",\"}; print }'" );

    public final static List< String > FREE_LIST = Arrays.asList( "bash", "-c",
            "free -g" );

    public final static List< String > FREE_BY_M_LIST = Arrays.asList( "bash",
            "-c", "free -m" );

    public String performMemoryProcessList(
            boolean sortByPriority,
            boolean isShowOnlyCsap,
            boolean isShowOnlyUser
    ) {

        List< String > psList = PS_MEMORY_LIST;

        if ( sortByPriority ) {

            psList = PS_PRIORITY_LIST;

        }
        // ps -e --sort -rss -o pmem,rss,args | awk '{
        // for(i=0;i<=NF;i++){$i=$i","}; print }'
        // ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed
        // 's/ */ /g'
        // ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed
        // 's/ */ /g' |awk '{ for(i=0;i<=7;i++){$i=$i","}; print }'
        // size or rss...switch to size as it is bigger for now

        String psResult = osCommandRunner.executeString( null, psList );
        psResult = CsapApis.getInstance( ).application( ).check_for_stub( psResult, "linux/psMemory.txt" );

        if ( sortByPriority ) {

            psResult = CsapApis.getInstance( ).application( ).check_for_stub( psResult, "linux/psNice.txt" );

        }

        String freeResult = osCommandRunner.executeString( FREE_LIST, new File( "." ) );
        freeResult += osCommandRunner.executeString( FREE_BY_M_LIST, new File( "." ) );
        freeResult = CsapApis.getInstance( ).application( ).check_for_stub( freeResult, "linux/freeResults.txt" );

        // hook to display output nicely in browser
        String[] psLines = psResult.split( LINE_SEPARATOR );
        StringBuilder psBuilder = new StringBuilder( );

        String currUser = CsapApis.getInstance( ).application( ).getAgentRunUser( );

        for ( int psIndex = 0; psIndex < psLines.length; psIndex++ ) {

            String currLine = psLines[ psIndex ].trim( );
            String nameToken = "csapProcessId=";

            int nameStart = currLine.indexOf( nameToken );
            int headerStart = currLine.indexOf( "RSS" );
            int processingStart = currLine.indexOf( CsapApis.getInstance( ).application( ).getCsapWorkingFolder( )
                    .getAbsolutePath( ) );

            if ( Application.isRunningOnDesktop( ) ) {

                processingStart = currLine.indexOf( "/home/csapUser/processing" );
                currUser = "csapUser";

            }

            // skip past any non csap processes
            if ( isShowOnlyCsap && nameStart == -1 && headerStart == -1 && processingStart == -1 ) {

                continue;

            }

            // only show csapUser processes
            if ( currUser != null && isShowOnlyUser && headerStart == -1 && !currLine.contains( currUser ) ) {

                continue;

            }

            if ( nameStart != -1 ) {

                nameStart += nameToken.length( );
                int nameEnd = currLine.substring( nameStart ).indexOf( " " )
                        + nameStart;

                if ( nameEnd == -1 ) {

                    nameEnd = nameStart + 5;

                }

                logger.debug( "currLine: {}, \n\t nameStart: {}, \t nameEnd: {}",
                        currLine, nameStart, nameEnd );

                String serviceName = currLine.substring( nameStart, nameEnd );

                int insertIndex = currLine.indexOf( "/" );

                if ( isShowOnlyCsap ) {

                    currLine = currLine.substring( 0, insertIndex ) + serviceName;

                } else {

                    currLine = currLine.substring( 0, insertIndex )
                            + serviceName
                            + " : "
                            + currLine
                            .substring( insertIndex, currLine.length( ) );

                }

            }

            psBuilder.append( currLine + LINE_SEPARATOR );

        }

        return freeResult + "\n\n" + psBuilder;

    }

    public JsonNode diskReport( ) {

        JsonNode result = null;

        try {

            result = disk_reads_and_writes( ).get( "totalInMB" );

        } catch ( Exception e ) {

            logger.warn( "Failed parsing iostat for io total reads and writes: {}", CSAP.buildCsapStack( e ) );
            ObjectNode failureNode = jsonMapper.createObjectNode( );
            failureNode.put( "reads", "-1" );
            failureNode.put( "writes", -1 );

            result = failureNode;

        }

        return result;

    }

    private CsapSimpleCache ioStatisticsCache = null;
    private ReentrantLock ioStatusLock = new ReentrantLock( );

    public ObjectNode disk_reads_and_writes( ) {

        boolean initialRun = false;

        if ( ioStatisticsCache == null ) {

            initialRun = true;
            int cacheTime = 20;
            ioStatisticsCache = CsapSimpleCache.builder(
                    cacheTime,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "IO Statistics" );
            ioStatisticsCache.expireNow( );

        }

        if ( !ioStatisticsCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  ioStatisticsCache   *******\n\n" );

        } else if ( ioStatusLock.tryLock( ) ) {

            if ( ioStatisticsCache.isExpired( ) ) {

                logger.debug( "\n\n***** REFRESHING   ioStatisticsCache   *******\n\n" );

                var timer = CsapApis.getInstance( ).metrics( ).startTimer( );

                try {

                    ioStatisticsCache.reset( updateIoCache( initialRun ) );

                } catch ( Exception e ) {

                    logger.info( "Failed refreshing ioStatisticsCache {}",
                            CSAP.buildCsapStack( e ) );

                } finally {

                    ioStatusLock.unlock( );

                }

                CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS
                        + "device-read-write-rate" );

            }

        } else {

            logger.debug( "Failed to get ioStatisticsCache lock" );

        }

        return ( ObjectNode ) ioStatisticsCache.getCachedObject( );

    }

    private ObjectNode updateIoCache(
            boolean isInitialRun
    ) {

        ObjectNode diskActivityReport = jsonMapper.createObjectNode( );

        String iostatOutput = "";

        try {

            // iostatOutput = osCommandRunner.runUsingDefaultUser( "iostat_dm",
            // diskTestScript );
            iostatOutput = osCommandRunner.executeString( null, osCommands.getSystemDiskWithRateOnly( ) );
            iostatOutput = CsapApis.getInstance( ).application( ).check_for_stub( iostatOutput,
                    "linux/ioStatResults.txt" );

            // Device: tps MB_read/s MB_wrtn/s MB_read MB_wrtn

            if ( isInitialRun ) {

                logger.info( "Results from {}, \n {}",
                        osCommands.getSystemDiskWithRateOnly( ),
                        CsapApplication.header( iostatOutput ) );

            }

            String[] iostatLines = iostatOutput.split( LINE_SEPARATOR );

            ArrayNode filteredLines = diskActivityReport.putArray( "filteredOutput" );
            int totalDiskReadMb = 0;
            int totalDiskWriteMb = 0;

            for ( int i = 0; i < iostatLines.length; i++ ) {

                String curline = CsapConstants.singleSpace( iostatLines[ i ] );

                logger.debug( "Processing line: {}", curline );

                if ( curline != null
                        && !curline.isEmpty( )
                        && curline.matches( CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                        .getIostatDeviceFilter( ) ) ) {

                    filteredLines.add( curline );
                    String[] fields = curline.split( " " );

                    if ( fields.length == 6 ) {

                        totalDiskReadMb += Integer.parseInt( fields[ 4 ] );
                        totalDiskWriteMb += Integer.parseInt( fields[ 5 ] );

                    }

                }

            }

            ObjectNode total = diskActivityReport.putObject( "totalInMB" );
            total.put( "reads", totalDiskReadMb );
            total.put( "writes", totalDiskWriteMb );

        } catch ( Exception e ) {

            logger.info( "Results from {}, \n {}, \n {}",
                    osCommands.getSystemDiskWithRateOnly( ), iostatOutput,
                    CSAP.buildCsapStack( e ) );

        }

        return diskActivityReport;

    }

    /**
     * iostat -dx : determines disk utilization
     */

    private volatile CsapSimpleCache diskUtilizationCache = null;
    private ReentrantLock diskUtilLock = new ReentrantLock( );

    public ArrayNode device_utilization( ) {

        ArrayNode result = null;

        try {

            var current = disk_io_utilization( );

            if ( current != null ) {

                result = ( ArrayNode ) current.path( "devices" );

            } else {

                result = buildDeviceError( "Pending Initialization" );

            }

        } catch ( Exception e ) {

            logger.warn( "Failed parsing iostat for io utilization: {}", CSAP.buildCsapStack( e ) );

            result = buildDeviceError( CSAP.buildCsapStack( e ) );

        }

        return result;

    }

    private ArrayNode buildDeviceError( String reason ) {

        ArrayNode result;
        result = jsonMapper.createArrayNode( );
        ObjectNode deviceData = result.addObject( );
        deviceData.put( "name", "deviceNotFound" );
        deviceData.put( "percentCapacity", -1 );
        deviceData.put( "error-reason", reason );
        return result;

    }

    public ObjectNode disk_io_utilization( ) {

        boolean initialRun = false;

        if ( diskUtilizationCache == null ) {

            initialRun = true;
            int cacheTime = 20;
            diskUtilizationCache = CsapSimpleCache.builder(
                    cacheTime,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "Disk Utilization Statistics" );
            diskUtilizationCache.expireNow( );

        }

        if ( !diskUtilizationCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  diskUtilizationCache   *******\n\n" );

        } else if ( diskUtilLock.tryLock( ) ) {

            if ( diskUtilizationCache.isExpired( ) ) {

                logger.debug( "\n\n***** REFRESHING   diskUtilizationCache   *******\n\n" );

                var timer = CsapApis.getInstance( ).metrics( ).startTimer( );

                try {

                    diskUtilizationCache.reset( updateDiskUtilCache( initialRun ) );

                } catch ( Exception e ) {

                    logger.info( "Failed refreshing diskUtilizationCache {}",
                            CSAP.buildCsapStack( e ) );

                } finally {

                    diskUtilLock.unlock( );

                }

                CsapApis.getInstance( ).metrics( ).stopTimer( timer, COLLECT_OS
                        + "device-utilization-rate" );

            }

        }

        return ( ObjectNode ) diskUtilizationCache.getCachedObject( );

    }

    private ObjectNode updateDiskUtilCache(
            boolean isInitialRun
    ) {

        ObjectNode disk_io_utilization = jsonMapper.createObjectNode( );

        List< String > diskUtilizationScript = osCommands.getSystemDiskWithUtilization( );
        String iostatDiskUtilOutput = "";

        try {

            iostatDiskUtilOutput = osCommandRunner.runUsingDefaultUser(
                    "iostat_dx",
                    diskUtilizationScript );

            iostatDiskUtilOutput = CsapApis.getInstance( ).application( ).check_for_stub( iostatDiskUtilOutput,
                    "linux/iostat-with-util.txt" );

            // Device: tps MB_read/s MB_wrtn/s MB_read MB_wrtn

            if ( isInitialRun ) {

                logger.info( "Results from {}, \n {}",
                        Arrays.asList( diskUtilizationScript ),
                        CsapApplication.header( iostatDiskUtilOutput ) );

            }

            String[] iostatLines = iostatDiskUtilOutput.split( LINE_SEPARATOR );

            ArrayNode filteredLines = disk_io_utilization.putArray( "filteredOutput" );

            Map< String, Integer > deviceMap = new TreeMap<>( );

            for ( int i = 0; i < iostatLines.length; i++ ) {

                String singleSpacedLine = CsapConstants.singleSpace( iostatLines[ i ] );

                logger.debug( "Processing line: {}", singleSpacedLine );

                if ( singleSpacedLine != null
                        && !singleSpacedLine.isEmpty( )
                        && singleSpacedLine.matches(
                        CsapApis.getInstance( ).application( ).rootProjectEnvSettings( )
                                .getIostatDeviceFilter( ) ) ) {

                    filteredLines.add( singleSpacedLine );
                    String[] fields = singleSpacedLine.split( " " );

                    if ( fields.length == 2 ) {

                        // centos 7
                        String device = fields[ 0 ];
                        int utilPercentage = -1;

                        try {

                            utilPercentage = Math.round( Float.parseFloat( fields[ 1 ] ) );

                        } catch ( Exception e ) {

                            logger.error( CSAP.buildCsapStack( e ) );

                        }

                        deviceMap.put( device, utilPercentage );

                    }

//					if ( fields.length == 14 ) {
//						// centos 7
//						String device = fields[0] ;
//						int utilPercentage = -1 ;
//						try {
//							utilPercentage = Math.round( Float.parseFloat( fields[13] ) ) ;
//						} catch ( Exception e ) {
//							logger.error( CSAP.buildCsapStack( e ) ) ;
//						}
//						deviceMap.put( device, utilPercentage ) ;
//
//					} else if ( fields.length == 16 ) {
//						String device = fields[0] ;
//						int utilPercentage = -1 ;
//						try {
//							utilPercentage = Math.round( Float.parseFloat( fields[15] ) ) ;
//						} catch ( Exception e ) {
//							logger.error( CSAP.buildCsapStack( e ) ) ;
//						}
//						deviceMap.put( device, utilPercentage ) ;
//
//					}
                }

            }

            // build sorted list
            ArrayNode devices = disk_io_utilization.putArray( "devices" );
            deviceMap.entrySet( ).stream( ).forEach( deviceEntry -> {

                ObjectNode deviceData = devices.addObject( );
                deviceData.put( "name", deviceEntry.getKey( ) );
                deviceData.put( "percentCapacity", deviceEntry.getValue( ) );

            } );

        } catch ( Exception e ) {

            logger.info( "Results from {}, \n {}, \n {}",
                    Arrays.asList( diskUtilizationScript ), iostatDiskUtilOutput,
                    CSAP.buildCsapStack( e ) );

        }

        return disk_io_utilization;

    }

    public int getMemoryAvailbleLessCache( ) {

        if ( getCachedMemoryMetrics( ) == null ) {

            return -1;

        }

        if ( isMemoryFreeAvailabe( ) ) {

            return getCachedMemoryMetrics( ).path( RAM ).path( 6 ).asInt( -1 );

        }

        return getCachedMemoryMetrics( ).path( BUFFER ).path( 3 ).asInt( -1 );

    }

    public int getMemoryCacheSize( ) {

        if ( getCachedMemoryMetrics( ) == null ) {

            return -1;

        }

        if ( isMemoryFreeAvailabe( ) ) {

            return getCachedMemoryMetrics( ).path( RAM ).path( 5 ).asInt( );

        }

        return getCachedMemoryMetrics( ).path( RAM ).path( 6 ).asInt( );

    }

    // newer RH kernels have available as last column
    private boolean isMemoryFreeAvailabe( ) {

        return getCachedMemoryMetrics( ).path( FREE_AVAILABLE ).asBoolean( );

    }

    private volatile CsapSimpleCache memoryStatisticsCache = null;

    public ObjectNode getCachedMemoryMetrics( ) {

        logger.debug( "Entered" );

        // logger.info( "{}", Application.getCsapFilteredStackTrace( new
        // Exception( "calltree" ), "csap" )) ;
        if ( memoryStatisticsCache == null ) {

            memoryStatisticsCache = CsapSimpleCache.builder(
                    4,
                    TimeUnit.SECONDS,
                    OsManager.class,
                    "Memory Stats" );
            memoryStatisticsCache.expireNow( );

        }

        // Use cache
        if ( !memoryStatisticsCache.isExpired( ) ) {

            logger.debug( "\n\n***** ReUsing  mem cache   *******\n\n" );

            return ( ObjectNode ) memoryStatisticsCache.getCachedObject( );

        }

        logger.debug( "\n\n***** reloading  mem cache   *******\n\n" );

        ObjectNode latestMemoryReport;

        TreeMap< String, String[] > memResults = new TreeMap< String, String[] >( );
        var tsFormater = new SimpleDateFormat( "HH:mm:ss" );

        if ( CsapApis.getInstance( ).application( ).isMacOsProfileActive( ) ) {

            var parmList = Arrays.asList( "bash", "-c", "top -l 1 -s 0 | grep PhysMem" );
            var macPhysReport = OsCommandRunner.trimHeader( osCommandRunner.executeString( parmList, new File( "." ) ) );
            var memUsed = 0L;
            var available = 1L;

            var nodeCommandLines = macPhysReport.split( LINE_SEPARATOR );
            var possibleMemory = Arrays.stream( nodeCommandLines )
                    .filter( StringUtils::isNotEmpty )
                    .map( line -> CsapConstants.singleSpace( line ).split( " " ) )
                    .filter( columns -> columns.length > 7 )
                    .filter( columns -> columns[ 0 ].equals( "PhysMem:" ) )
                    .map( columns -> columns[1] )
                    .findFirst( );
            logger.debug( "possibleMemory: {} freeResult: {}", possibleMemory, macPhysReport );
            if (possibleMemory.isPresent() ) {
                memUsed = DataSize.parse( possibleMemory.get() + "B" ).toMegabytes() ;
                var availText =  CsapConstants.singleSpace( macPhysReport ).split( " " )[7] ;
                available = DataSize.parse( availText + "B" ).toMegabytes() + memUsed ;
            }
            //buffer: [ "Mem:", "1547172", "1341117", "189212", "15", "26069", "206055" ]
            // ram	[ "Mem:", "1547172", "1341117", "189212", "15", "26069", "206055" ]

            latestMemoryReport = jsonMapper.createObjectNode( );
//            var buf = latestMemoryReport.putArray( "buffer" ) ;
//            buf.addAll( jsonMapper.convertValue( List.of("Mem:", "1", "1", "1", "1", "1", "1"), ArrayNode.class ) ) ;
            var ram = latestMemoryReport.putArray( "ram" );
            ram.addAll( jsonMapper.convertValue( List.of( "Mem:", available, memUsed, "1", "1", "1", "1" ), ArrayNode.class ) );

            latestMemoryReport.put( "timestamp", tsFormater.format( new Date( ) ) );
            latestMemoryReport.put( FREE_AVAILABLE, true );

            memoryStatisticsCache.reset( latestMemoryReport );
            return latestMemoryReport;

        } else {

            var parmList = Arrays.asList( "bash", "-c", "free -m" );
            var freeResult = osCommandRunner.executeString( parmList, new File( "." ) );
            freeResult = CsapApis.getInstance( ).application( ).check_for_stub( freeResult, "linux/freeResults.txt" );

            parmList = Arrays.asList( "bash", "-c", "swapon -s " );
            String swapResult = osCommandRunner.executeString( parmList, new File( "." ) );
            swapResult = CsapApis.getInstance( ).application( ).check_for_stub( swapResult, "linux/swapResults.txt" );

            try {

                // Lets refresh cache
                logger.debug( "\n\n***** Refreshing mem cache   *******\n\n" );

                logger.debug( "freeResult: {}, \n swapResult: {} ", freeResult, swapResult );

                var headers = freeResult.substring( 0, freeResult.indexOf( "Mem:" ) );
                // handle newerKernel rh7+
                boolean isFreeAvailable = false;

                if ( headers.contains( "available" ) ) {

                    isFreeAvailable = true;

                }

                // Strips off the headers
                String trimFree = freeResult.substring( freeResult.indexOf( "Mem:" ) );
                String[] memLines = trimFree
                        .split( LINE_SEPARATOR );


                for ( int i = 0; i < memLines.length; i++ ) {

                    if ( memLines[ i ].contains( "Mem:" ) ) {

                        memResults.put( RAM,
                                CsapConstants.singleSpace( memLines[ i ] ).split( " " ) );

                        // default buffer to use RAM line. centos
                        memResults.put( BUFFER, CsapConstants.singleSpace( memLines[ i ] ).split( " " ) );

                    } else if ( memLines[ i ].contains( "cache:" ) ) {

                        memResults.put( BUFFER, CsapConstants.singleSpace( memLines[ i ] )
                                .split( " " ) );

                    } else if ( memLines[ i ].contains( "Swap:" ) ) {

                        memResults.put( SWAP, CsapConstants.singleSpace( memLines[ i ] )
                                .split( " " ) );

                    }

                }

                if ( (swapResult.trim( ).length( ) == 0) || (!swapResult.contains( "Filename" )) ) {

                    String[] noResults = {
                            "no Swap Found", "", "", "", ""
                    };
                    memResults.put( "swapon1", noResults );

                } else {

                    String trimSwap = swapResult.substring( swapResult.indexOf( "Filename" ) );
                    String[] swapLines = trimSwap.split( System
                            .getProperty( "line.separator" ) );

                    // skip past the header no matter what it is
                    int i = 1;

                    for ( String line : swapLines ) {

                        ArrayList< String > swapList = new ArrayList< String >( );
                        String swapPer = "";
                        // added host below for the simple view which needs to track
                        // it
                        String[] columns = CsapConstants.singleSpace( line ).split( " " );

                        if ( columns.length == 5 && columns[ 0 ].startsWith( "/" ) ) {

                            swapPer = "";

                            try {

                                float j = Float.parseFloat( columns[ 3 ] );
                                float k = Float.parseFloat( columns[ 2 ] );
                                swapPer = Integer.valueOf( Math.round( j / k * 100 ) ).toString( );

                            } catch ( Exception e ) {

                                // ignore
                            }

                            swapList.add( columns[ 0 ] );
                            swapList.add( columns[ 1 ] );
                            swapList.add( swapPer );
                            swapList.add( columns[ 3 ] + " / " + columns[ 2 ] );
                            swapList.add( columns[ 4 ] );
                            memResults.put( "swapon" + i++,
                                    swapList.toArray( new String[ swapList.size( ) ] ) );

                        }

                    }

                }

                latestMemoryReport = jsonMapper.valueToTree( memResults );

                latestMemoryReport.put( "timestamp", tsFormater.format( new Date( ) ) );
                latestMemoryReport.put( FREE_AVAILABLE, isFreeAvailable );

                memoryStatisticsCache.reset( latestMemoryReport );
                return latestMemoryReport;

            } catch ( Exception e ) {

                logger.warn( "Failure parsing memory, free:\n {} \n swap: {} \n{}",
                        freeResult, swapResult, CSAP.buildCsapStack( e ) );

            }
        }

        return null;

    }

    public static final String FREE_AVAILABLE = "isFreeAvailable";

    public String updatePlatformCore(
            MultipartFile multiPartFile,
            String extractTargetPath,
            boolean skipExtract,
            String remoteServerName,
            String chownUserid,
            String auditUser,
            String deleteExisting,
            OutputFileMgr outputFileManager
    )
            throws IOException {

        StringBuilder results = new StringBuilder( "Host: " + remoteServerName );

        if ( multiPartFile == null ) {

            results.append( "\n========== multiPartFile is null \n\n" );
            return results.toString( );

        }

        if ( extractTargetPath.trim( )
                .length( ) == 0
                || chownUserid.trim( )
                .length( ) == 0
                || auditUser.trim( )
                .length( ) == 0 ) {

            logger.error( "extractTargetPath is empty, must be corrected" );
            results.append( "\n " + MISSING_PARAM_HACK
                    + " param was an empty string and is required. extractTargetPath: "
                    + extractTargetPath + ", chownUserid: " + chownUserid + ", auditUser:" + auditUser );
            return results.toString( );

        }
        // byte[] fileBytes = file.getBytes();

        // We temporarily extract all files to followingFolder, then copy to
        // target location
        // Note the subFolder MUST be different from original source folder, or
        // files could get overwritten
        File platformTempFolder = new File( CsapApis.getInstance( ).application( ).csapPlatformTemp( ),
                "/csap-agent-transfer-manager/" );

        if ( !platformTempFolder.exists( ) ) {

            logger.info( "Creating {}", platformTempFolder );
            platformTempFolder.mkdirs( );

        }

        File tempExtractLocation = new File( platformTempFolder, multiPartFile.getOriginalFilename( ) );

        if ( Application.isRunningOnDesktop( ) && skipExtract ) {

            logger.warn( CsapApplication.testHeader( "desktop - modifying tempExtractLocation: {}" ),
                    tempExtractLocation );
            tempExtractLocation = new File( extractTargetPath );

        }

        results.append( " uploaded file: " + multiPartFile.getOriginalFilename( ) );
        results.append( " Size: " + multiPartFile.getSize( ) );

        File extractTarget = new File( extractTargetPath );

        if ( !extractTarget.getParentFile( ).exists( ) ) {

            logger.warn( "parent folder for extraction does not exist: {} ",
                    extractTarget.getParentFile( ).getAbsolutePath( ) );

        }

        if ( extractTarget.exists( ) && extractTarget.isFile( ) ) {

            results.append( "\n ===> Destination exists and will be overwritten." );

            if ( Application.isRunningOnDesktop( ) & !skipExtract ) {

                logger.warn( CsapApplication.testHeader( "desktop - modifying tempExtractLocation: {}" ),
                        tempExtractLocation );

                tempExtractLocation = new File( extractTarget.getAbsolutePath( ) + ".windebug" );
                results.append( "\n desktop destination for testing only: "
                        + tempExtractLocation.getAbsolutePath( ) );

            }

        }

        try {

            outputFileManager.printImmediate( "\n\n *** Temporary upload location: " + tempExtractLocation
                    .getAbsolutePath( ) );
            multiPartFile.transferTo( tempExtractLocation );

        } catch ( Exception e ) {

            logger.error( "multiPartFile.transferTo : {} {}", tempExtractLocation, CSAP.buildCsapStack( e ) );

            return "\n== " + CsapConstants.CONFIG_PARSE_ERROR
                    + " on multipart file transfer on Host " + CsapApis.getInstance( ).application( ).getCsapHostName( )
                    + ":" + e.getMessage( );

        }

        if ( Application.isRunningOnDesktop( )
                && !skipExtract
                && (multiPartFile.getOriginalFilename( ).endsWith( ".zip" )) ) {

            logger.warn( CsapApplication.testHeader( "desktop - checking extractTarget: {}" ),
                    extractTarget );

            if ( extractTarget.exists( ) && extractTarget.isDirectory( ) ) {

                try {

                    File desktopTransferFolder = new File( extractTarget, "csap-desktop-transfer" );

                    if ( CsapApis.getInstance( ).application( ).isJunit( ) ) {

                        desktopTransferFolder = extractTarget;

                    }

                    ZipUtility.unzip( tempExtractLocation, desktopTransferFolder );
                    results.append( "\n Unzipped to: " + desktopTransferFolder.getAbsolutePath( ) );

                } catch ( Exception e ) {

                    results.append( "\n Failed to unzip " + tempExtractLocation.getAbsolutePath( )
                            + " due to: " + e.getMessage( ) );
                    logger.error( "\n Failed to unzip " + tempExtractLocation.getAbsolutePath( ), e );

                }

            } else {

                results.append( "\n== " + CsapConstants.CONFIG_PARSE_ERROR
                        + " Windows extract target exists and is a file: "
                        + extractTarget.getAbsolutePath( ) );

            }

        }

        if ( !tempExtractLocation.exists( ) ) {

            results.append( " Could not run as root, extract file not located in "
                    + tempExtractLocation.getAbsolutePath( ) );

        } else {

            var parmList = List.of( "echo skipping transfer" );

            if ( CsapApis.getInstance( ).application( ).isJunit( ) ) {

                logger.info( CsapApplication.testHeader( "junit: skipping FS updates" ) );

            } else {

                // backup existing
                if ( deleteExisting != null ) {

                    CsapApis.getInstance( ).application( ).move_to_csap_saved_folder( extractTarget, results );

                }

                // hook for root ownership, and script execution
                // ALWAYS use CSAP user if files are extracted to csap folder
                var userChownedInScript = chownUserid;

                if ( extractTargetPath.startsWith( CsapApis.getInstance( ).application( ).getAgentRunHome( ) ) ) {

                    userChownedInScript = CsapApis.getInstance( ).application( ).getAgentRunUser( );
                    logger.info( "Specified directory starts with: {}, userid will be set to: {}",
                            CsapApis.getInstance( ).application( ).getAgentRunHome( ),
                            userChownedInScript );

                } else if ( extractTargetPath.startsWith( "/home/" + auditUser ) ) {

                    userChownedInScript = auditUser + ":users";
                    logger.info( "Specified directory starts with: {}, userid will be set to: {}",
                            "/home/" + auditUser,
                            userChownedInScript );

                }

                File scriptPath = CsapApis.getInstance( ).application( ).csapPlatformPath(
                        "/bin/csap-unzip-as-root.sh" );
                parmList = new ArrayList< String >( );

                if ( CsapApis.getInstance( ).application( ).isRunningAsRoot( ) ) {

                    parmList.add( "/usr/bin/sudo" );
                    parmList.add( scriptPath.getAbsolutePath( ) );
                    parmList.add( tempExtractLocation.getAbsolutePath( ) );
                    parmList.add( extractTargetPath );
                    parmList.add( userChownedInScript );

                    if ( skipExtract ) {

                        parmList.add( "skipExtract" );

                    }

                } else {

                    parmList.add( "bash" );
                    parmList.add( "-c" );
                    String command = scriptPath.getAbsolutePath( )
                            + " " + tempExtractLocation.getAbsolutePath( )
                            + " " + extractTargetPath
                            + " " + chownUserid;

                    if ( skipExtract ) {

                        command += " skipExtract";

                    }

                    parmList.add( command );

                }

            }

            var extractResults = osCommandRunner.executeString( null, parmList );
            logger.debug( "script results: {}", extractResults );

            results.append( "\n" + extractResults );

        }

        CsapApis.getInstance( ).events( ).publishUserEvent( CsapEvents.CSAP_SYSTEM_CATEGORY
                        + "/fileUpload", auditUser,
                multiPartFile.getOriginalFilename( ), results.toString( ) );

        return results.toString( );

    }

    public static String MISSING_PARAM_HACK = CsapConstants.CONFIG_PARSE_ERROR + "-BlankParamFound";

    /**
     * Note that cluster can be none, in which case command is only run on current
     * VM.
     *
     * @param timeoutSeconds
     * @param contents
     * @param chownUserid
     * @param clusterName
     * @param scriptName
     * @param outputFm
     * @return
     * @throws IOException
     */
    public ObjectNode executeShellScriptClustered(
            String apiUser,
            int timeoutSeconds,
            String contents,
            String chownUserid,
            String[] hosts,
            String scriptName,
            OutputFileMgr outputFm
    )
            throws IOException {

        List< String > hostList = new ArrayList<>( Arrays.asList( hosts ) );

        ObjectNode resultsNode = jsonMapper.createObjectNode( );
        resultsNode.put( "scriptHost", CsapApis.getInstance( ).application( ).getCsapHostName( ) );
        ArrayNode hostNode = resultsNode.putArray( "scriptOutput" );

        logger.info(
                CSAP.buildDescription( "Running Script",
                        "apiUser", apiUser,
                        "chownUserid", chownUserid,
                        "timeOutSeconds", timeoutSeconds,
                        "hostList", hostList,
                        "script", scriptName ) );

        File scriptDir = CsapApis.getInstance( ).application( ).getScriptDir( );

        if ( !scriptDir.exists( ) ) {

            logger.info( "Making: " + scriptDir.getAbsolutePath( ) );
            scriptDir.mkdirs( );

        }

        File executableScript = new File( scriptDir, scriptName );

        if ( executableScript.exists( ) ) {

            logger.info( "Deleting" + executableScript.getAbsolutePath( ) );
            executableScript.delete( );
            hostNode.add( "== Deleting existing script of same name: "
                    + executableScript.getAbsolutePath( ) );

        }

        hostNode.add( "\n == Script Output: " + outputFm.getOutputFile( ).getAbsolutePath( ) + "\n\n" );

        var scriptSummary = CSAP.buildDescription(
                "Executing : script",
                "user", chownUserid,
                "hosts", hostList,
                "time out seconds", timeoutSeconds,
                "location", executableScript.getAbsolutePath( ) );

        logger.info( scriptName );

        CsapApis.getInstance( ).events( ).publishUserEvent(
                CsapEvents.CSAP_OS_CATEGORY + "/execute",
                apiUser, executableScript.getName( ),
                scriptSummary + "\n\nscript: \n" + contents );

        if ( !executableScript.exists( ) ) {

            try ( FileWriter fstream = new FileWriter( executableScript );
                  BufferedWriter out = new BufferedWriter( fstream ); ) {
                // Create file

                out.write( contents );

            } catch ( Exception e ) {

                hostNode.add( "ERROR: failed to createfile due to: " + e.getMessage( ) );
                ;

            }

            hostNode.add( CsapApis.getInstance( ).application( ).getCsapHostName( ) + ":" + " Script copied" );

        } else {

            hostNode.add( "ERROR: Script file still exists" );

        }

        resultsNode.set( "otherHosts",
                zipAndTransfer( apiUser, timeoutSeconds, hostList,
                        executableScript.getAbsolutePath( ), CsapConstants.SAME_LOCATION, chownUserid, outputFm,
                        null ) );

        return resultsNode;

    }

    /**
     * Will zip and tar
     *
     * @param timeOutSeconds
     * @param hostList
     * @param locationToZip
     * @param extractDir
     * @param chownUserid
     * @param auditUser
     * @param outputFm
     * @return
     * @throws IOException
     */
    public ArrayNode zipAndTransfer(
            String apiUser,
            int timeOutSeconds,
            List< String > hostList,
            String locationToZip,
            String extractDir,
            String chownUserid,
            OutputFileMgr outputFm,
            String deleteExisting
    )
            throws IOException {

        logger.debug( "locationToZip: {}, extractDir: {}, chownUserid: {} , hosts: {}",
                locationToZip, extractDir, chownUserid, Arrays.asList( hostList )
                        .toString( ) );

        var results = jsonMapper.createArrayNode( );

        if ( hostList == null || hostList.size( ) == 0 ) {

            return results;

        }
        // return "No Additional Synchronization required";
        // logger.info("locationToZip" + locationToZip);

        var transferManager = new TransferManager(
                CsapApis.getInstance( ),
                timeOutSeconds,
                outputFm.getBufferedWriter( ) );

        if ( deleteExisting != null ) {

            transferManager.setDeleteExisting( true );

        }

        var zipLocation = new File( locationToZip );

        var targetFolder = new File( extractDir );

        if ( extractDir.equalsIgnoreCase( CsapConstants.SAME_LOCATION ) ) {

            targetFolder = zipLocation;

            if ( zipLocation.isFile( ) ) {

                // Hook when just a single file is being transferred
                targetFolder = zipLocation.getParentFile( );

            }

        }

        var result = "Specified Location does not exist: " + locationToZip + " on host: "
                + CsapApis.getInstance( ).application( ).getCsapHostName( );

        if ( zipLocation.exists( ) ) {

            transferManager.httpCopyViaCsAgent(
                    apiUser,
                    zipLocation,
                    Application.filePathAllOs( targetFolder ),
                    hostList,
                    chownUserid );

            results = transferManager.waitForCompleteJson( );

        } else {

            logger.error( result );

        }

        return results;

    }

    public void updateApplicationScriptPermissions( ) {

        var script = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "chmod 755 $csapDefinitionFolder/scripts/*",
                "" );

        try {

            var scriptOutput = osCommandRunner.runUsingDefaultUser( "cli_runner", script );
            logger.info( "Permissions updated: {}", scriptOutput );

        } catch ( Exception e ) {

            logger.info( "Failed to run script: {} , \n reason: {}", script,
                    CSAP.buildCsapStack( e ) );

        }
    }

    public String cli( String parameters ) {

        var outputDelimeter = "__output__delimiter__" + System.currentTimeMillis( );

        var script = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "echo " + outputDelimeter,
                parameters,
                "" );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingDefaultUser( "cli_runner", script, null, null, false );

            logger.debug( "scriptOutput: {}", scriptOutput );

            var stubResultFile = "helm/show-values.txt";

            if ( parameters.contains( "calico" ) ) {

                stubResultFile = "linux/calico-endpoints.txt";

            } else if ( parameters.contains( "top" ) ) {

                stubResultFile = "linux/ps-top-results.txt";

            } else if ( parameters.contains( "releases" ) ) {

                stubResultFile = "helm/releases.txt";

            } else if ( parameters.contains( "show readme" ) ) {

                stubResultFile = "helm/show-readme.txt";

            } else if ( parameters.contains( "nmap" ) ) {

                stubResultFile = "linux/nmap.txt";

            }


            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput, stubResultFile );

            // trim header
            var deleteStart = scriptOutput.indexOf( outputDelimeter );

            if ( deleteStart > 10 ) {

                deleteStart += outputDelimeter.length( ) + 1;

                if ( deleteStart < scriptOutput.length( ) - 1 ) {

                    scriptOutput = scriptOutput.substring( deleteStart );

                }

            }

            logger.debug( "output from: {}  , \n{}", script, scriptOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to run script: {} , \n reason: {}", script,
                    CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " +
                    e.getClass( ).getName( );

        }

        return scriptOutput;

    }

    public String systemStatus( ) {

        List< String > lines = osCommands.getSystemServiceListing( CsapApis.getInstance( ).application( )
                .getCsapInstallFolder( ).getAbsolutePath( ) );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "OsManagerProcessTree", lines );

            scriptOutput = CsapApis.getInstance( ).application( ).check_for_stub( scriptOutput,
                    "linux/systemctl-status.txt" );

            logger.debug( "output from: {}  , \n{}", lines, scriptOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
                    CSAP.buildCsapStack( e ) );
            scriptOutput += ", reason: " + e.getMessage( ) + " type: " +
                    e.getClass( ).getName( );

        }

        return scriptOutput;

    }

    public ObjectNode buildProcessReport(
            String pids,
            String description
    ) {

        logger.info( "Running report on : {}", pids );

        var processReport = jsonMapper.createObjectNode( );

        var reportOuput = new StringBuilder( );

        reportOuput.append( CsapApplication.header( "Process arguments" ) + buildProcessReports( pids ) );

        reportOuput.append( CsapApplication.header( "Process trees" ) + runProcessTree( pids, description ) );

        processReport.put( C7.response_plain_text.val( ), reportOuput.toString( ) );

        return processReport;

    }

    String buildProcessReports( String commaSeparatedPids ) {

        var script = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "print_command \"ps -o pcpu,pid,args\" \"$(ps -o pcpu,pid,args -p " + commaSeparatedPids + " | sed 's/-D/\\n\\t -D/g' | sed 's/-classpath/\\n\\t -classpath/g' | sed 's/-X/\\n\\t -X/g')\"",
                "" );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "on-demand-ps", script );
            logger.debug( "output from: {}  , \n{}", script, scriptOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to run : {} , \n reason: {}", script,
                    CSAP.buildCsapStack( e ) );

        }

        return scriptOutput;

    }

    String runProcessTree(
            String commaSeparatedPids,
            String serviceName
    ) {

        var psTreeScript = new ArrayList< String >( );

        psTreeScript.addAll( List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "" ) );

        for ( var pid : commaSeparatedPids.split( "," ) ) {

            psTreeScript.addAll( List.of(

                    "print_command \"pstree for pid " + pid + " \" \"$(pstree -slp " + pid + " | head -1 ; "
                            + "echo -e ; "
                            + "pstree -sla " + pid + ")\"",
                    "" ) );

        }

        psTreeScript.addAll( List.of(
                "print_command \"pstree: full\" \"$(pstree)\"",
                "" ) );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "OsManagerProcessTree", psTreeScript );
            logger.debug( "output from: {}  , \n{}", psTreeScript, scriptOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to run: {} , \n reason: {}", psTreeScript,
                    CSAP.buildCsapStack( e ) );

        }

        return scriptOutput;

    }

    public ObjectNode runProcMountCommand( ) {

        var results = jsonMapper.createObjectNode( );

        var lines = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),

                "print_command \"cat /proc/mounts | column --table\" \"$(cat /proc/mounts | column --table)\"",
                ""
        );

        var scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "on-demand-proc-mount", lines );

            results.put( C7.response_plain_text.val( ), scriptOutput );
            logger.debug( "output from: {}  , \n{}", lines, scriptOutput );

        } catch ( Exception e ) {

            logger.info( "Failed to run: {} , \n reason: {}", lines,
                    CSAP.buildCsapStack( e ) );
            results.put( "error", CSAP.buildCsapStack( e ) );


        }

        return results;

    }

    public ObjectNode runOsTopCommand( String sortBy ) {

        var results = jsonMapper.createObjectNode( );

        var lines = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),

                "print_with_head Running top in batch mode top -b -o +%" + sortBy + " -d 2 -n 1 -c",
                "export COLUMNS=500; top -b -o +%" + sortBy + " -d 2 -n 1 -c",
                ""
        );

        String scriptOutput = "Failed to run";

        try {

            scriptOutput = osCommandRunner.runUsingRootUser( "on-demand-top", lines );

            results.put( C7.response_plain_text.val( ), scriptOutput );
            logger.debug( "output from: {}  , \n{}", lines, scriptOutput );

        } catch ( Exception e ) {

            logger.info( "Failed to run: {} , \n reason: {}", lines,
                    CSAP.buildCsapStack( e ) );
            results.put( "error", CSAP.buildCsapStack( e ) );


        }

        return results;

    }

    public int kubernetes_certs_expiration_days( int numDays ) {

        int nearestExpirationDays = -1;

        try {

            var script = List.of(
                    "#!/bin/bash",
                    sourceCommonFunctions( ),
                    "export minimumExpirationDays=" + numDays,
                    CsapApis.getInstance( ).application( ).csapPlatformPath( "bin/csap-check-certificates.sh" )
                            .getAbsolutePath( ),
                    "" );

            String scriptOutput = "Failed to run";

            try {

                scriptOutput = OsCommandRunner.trimHeader( osCommandRunner.runUsingDefaultUser( "kubeadm-check-certs",
                        script ) );

                if ( scriptOutput.contains( "apiserver-kubelet-client" ) ) {

                    logger.warn( "Certificates are nearing expiration: {}", scriptOutput );
                    String[] serviceLines = scriptOutput.split( LINE_SEPARATOR );

                    var shortestExpiration = Arrays.stream( serviceLines )
                            .filter( StringUtils::isNotEmpty )
                            .filter( line -> line.contains( "__required-action-days__:" ) )
                            .mapToInt( line -> {

                                String[] cols = line.trim( ).split( " " );

                                if ( cols.length == 2 ) {

                                    return Integer.parseInt( cols[ 1 ] );

                                }

                                return -1;

                            } )
                            .findFirst( );

                    if ( shortestExpiration.isPresent( ) ) {

                        nearestExpirationDays = shortestExpiration.getAsInt( );

                    }

                } else {

                    logger.warn( "Failed to find expected output: ", scriptOutput );

                }

                // scriptOutput = CsapApis.getInstance().application().check_for_stub(
                // scriptOutput,
                // "linux/systemctl-status.txt" ) ;

                logger.debug( "output from: {}  , \n{}", script, scriptOutput );

            } catch ( Exception e ) {

                logger.info( "Failed to run certificate checks: {} , \n reason: {}", script,
                        CSAP.buildCsapStack( e ) );

            }

        } catch ( Exception e ) {

            logger.info( "Failed to run kubeadm-check-certs: {}", CSAP.buildCsapStack( e ) );

        }

        return nearestExpirationDays;

    }

    public ObjectNode run_kernel_limits( ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        try {

            List< String > lines = Files.readAllLines( CsapApis.getInstance( ).application( ).csapPlatformPath(
                            "bin/admin-show-limits.sh" )
                    .toPath( ) );
            results.put( C7.response_plain_text.val( ),
                    osCommandRunner.runUsingRootUser( "adminlimits", lines ) );

        } catch ( Exception e ) {

            logger.info( "Failed to run run_kernel_limits: {}",
                    CSAP.buildCsapStack( e ) );

            results.put( C7.response_plain_text.val( ), CSAP.buildCsapStack( e ) );

        }

        return results;

    }

    public ObjectNode latest_process_discovery_results( ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        results.put( C7.response_plain_text.val( ),
                processMapper.getLatestProcessSummary( )
                        + "\n\n" + CsapApplication.LINE
                        + "\n\n linux ps output: \n\n"
                        + lastProcessStatsCollected( )
                        + "\n\n" + CsapApplication.LINE
                        + "\n Container Discovery: \n\n"
                        + processMapper.containerSummary( ) );

        return results;

    }

    public int getCachedLinuxPackageCount( ) {

        return cachedLinuxPackageCount;

    }

    public void setCachedLinuxPackageCount(
            int cachedLinuxPackageCount
    ) {

        this.cachedLinuxPackageCount = cachedLinuxPackageCount;

    }

    public int getCachedLinuxServiceCount( ) {

        return cachedLinuxServiceCount;

    }

    public int getCachedChefCookbookCount( ) {

        return cachedChefCookbookCount;

    }

    public void setCachedLinuxServiceCount(
            int cachedLinuxServiceCount
    ) {

        this.cachedLinuxServiceCount = cachedLinuxServiceCount;

    }

    public ObjectNode cleanImages(
            int days,
            int minutes
    ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        var containerService = CsapApis.getInstance( ).application( ).findFirstServiceInstanceInLifecycle(
                C7.dockerService.val( ) );

        if ( CsapApis.getInstance( ).isCrioInstalledAndActive( ) ) {

            containerService = CsapApis.getInstance( ).application( ).findFirstServiceInstanceInLifecycle( "crio" );

        }

        if ( containerService == null ) {

            results.put( "error", true );
            results.put( "reason", "no container service available (docker|crio)" );
            return results;

        }

        var cleanScript = List.of( "echo no container available" );

        if ( CsapApis.getInstance( ).isCrioInstalledAndActive( ) ) {

            var crioWorking = containerService
                    .getWorkingDirectory( )
                    .getAbsolutePath( );

            cleanScript = List.of(
                    "#!/bin/bash",
                    sourceCommonFunctions( ),
                    crioWorking + "/scripts/crio-gc.sh" );

        } else {

            var dockerWorking = containerService
                    .getWorkingDirectory( )
                    .getAbsolutePath( );

            int numSeconds = days * (24 * 60 * 60) + minutes * (60);

            cleanScript = List.of(
                    "#!/bin/bash",
                    sourceCommonFunctions( ),
                    "print_section Cleaning: " + days + " days and " + minutes + " minutes : " + numSeconds
                            + " seconds",
                    "dockerDir=" + dockerWorking,
                    "export PID_DIR=$dockerDir/scripts",
                    "export STATE_DIR=$dockerDir/docker-gc-state",
                    "export GRACE_PERIOD_SECONDS=" + numSeconds,
                    "$dockerDir/scripts/docker-gc.sh" );

        }

        try {

            String cleanOutput = osCommandRunner.runUsingDefaultUser( "docker-image-clean", cleanScript );
            // cleanOutput = CsapApis.getInstance().application().check_for_stub( ioOutput,
            // "linux/proc-net-dev.txt" );

            results.put( C7.response_plain_text.val( ), cleanOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to run docker nsenter: {} , \n reason: {}", cleanScript,
                    CSAP.buildCsapStack( e ) );

            results.put( "error", true );

        }

        return results;

    }

    public ObjectNode killProcess(
            int pid,
            String signal
    ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        var killScript = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "print_with_date killing pid: " + pid,
                "listing=$(ps -ef | grep " + pid + ")",
                "print_with_head \"pre-kill listing: $listing \"",
                "kill " + signal + " " + pid,
                "listing=$(ps -ef | grep " + pid + ")",
                "print_with_head \"post-kill listing (should be empty): \n'$listing' \"" );

        try {

            String cleanOutput = osCommandRunner.runUsingRootUser( "docker-image-clean", killScript );
            // cleanOutput = CsapApis.getInstance().application().check_for_stub( ioOutput,
            // "linux/proc-net-dev.txt" );

            results.put( C7.response_plain_text.val( ), cleanOutput );

        } catch ( IOException e ) {

            logger.info( "Failed to run docker nsenter: {} , \n reason: {}", killScript,
                    CSAP.buildCsapStack( e ) );

            results.put( "error", true );

        }

        return results;

    }

    public String sourceCommonFunctions( ) {

        return osCommandRunner.sourceCommonFunctions( );

    }

    public OsCommands getOsCommands( ) {

        return osCommands;

    }

    public void setOsCommands(
            OsCommands osSettings
    ) {

        this.osCommands = osSettings;

    }

    public int getCachedNetworkInterfaceCount( ) {

        return cachedNetworkInterfaceCount;

    }

    public void setCachedNetworkInterfaceCount(
            int networkInterfaceCount
    ) {

        this.cachedNetworkInterfaceCount = networkInterfaceCount;

    }

    public ObjectNode kubernetesShell(
            String operation,
            File commandScript,
            C7 responseType
    ) {

        var results = jsonMapper.createObjectNode( );

        var runCommands = List.of(
                "#!/bin/bash",
                sourceCommonFunctions( ),
                "chmod 755 " + commandScript.getAbsolutePath( ),
                commandScript.getAbsolutePath( ) + " " + operation );

        var commandResults = "failed to run";

        try {

            commandResults = OsCommandRunner.trimHeader(
                    kuberernetesRunner.runUsingDefaultUser( "kubernetes-shell", runCommands ) );

        } catch ( Exception e ) {

            commandResults += CSAP.buildCsapStack( e );

        }

        results.put( responseType.val( ), commandResults );

        return results;

    }

    private File helmCliFile = null;

    private static final String HELM_COMMAND = "helm";

    public JsonNode helmCli( String command ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        if ( helmCliFile == null ) {

            helmCliFile = new File( CsapApis.getInstance( ).application( ).getCsapInstallFolder( ), "/bin/"
                    + HELM_COMMAND );

        }

        if ( !helmCliFile.canExecute( ) ) {

            results.put( "error", "helm not found in expected location: " + helmCliFile.getAbsolutePath( ) );
            return results;

        }

        var parmList = List.of( "bash", "-c", HELM_COMMAND + " " + command );
        results.put( "command", parmList.toString( ) );

        var commandResults = OsCommandRunner.trimHeader(
                kuberernetesRunner.executeString( parmList, CsapApis.getInstance( ).application( )
                        .getCsapSavedFolder( ) ) );

        if ( CsapApis.getInstance( ).application( ).isDesktopHost( ) ) {

            if ( command.contains( "repo list" ) ) {

                commandResults = CsapApis.getInstance( ).application( ).check_for_stub( commandResults,
                        "helm/repos.json" );

            } else if ( command.contains( "search" ) ) {

                commandResults = CsapApis.getInstance( ).application( ).check_for_stub( commandResults,
                        "helm/repo-search.json" );

            } else if ( command.contains( "list" ) ) {

                commandResults = CsapApis.getInstance( ).application( ).check_for_stub( commandResults,
                        "helm/releases.json" );

            } else {

                commandResults = CsapApis.getInstance( ).application( ).check_for_stub( commandResults,
                        "helm/status.json" );

            }

        }

        results.put( "output", commandResults );

        try {

            var commandReport = jsonMapper.readTree( commandResults );
            results.set( "result", commandReport );

        } catch ( Exception e ) {

            var err = CSAP.buildCsapStack( e );
            logger.warn( err );
            results.put( "error", err );

        }

        return results;

    }

    public ObjectNode kubernetesCli(
            String command,
            C7 responseType
    ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        List< String > parmList = List.of( "bash", "-c", KubernetesIntegration.CLI_COMMAND + " " + command );

        var commandResults = OsCommandRunner.trimHeader(
                kuberernetesRunner.executeString( parmList, CsapApis.getInstance( ).application( )
                        .getCsapSavedFolder( ) ) );

        if ( CsapApis.getInstance( ).application( ).isDesktopHost( ) ) {

            if ( command.contains( "get -o=yaml" ) ) {

                commandResults = CsapApis.getInstance( ).application( ).check_for_stub( commandResults,
                        "linux/pod-get.yml" );

            } else {

                commandResults = CsapApis.getInstance( ).application( ).check_for_stub( commandResults,
                        "linux/pod-describe.txt" );

            }

        }

        results.put( responseType.val( ), commandResults );

        return results;

    }

    public ObjectNode dockerCli(
            String command,
            C7 responseType
    ) {

        ObjectNode results = jsonMapper.createObjectNode( );

        List< String > parmList = List.of( "bash", "-c", ContainerIntegration.CLI_COMMAND + " " + command );

        results.put(
                responseType.val( ),
                OsCommandRunner.trimHeader(
                        kuberernetesRunner.executeString( parmList, CsapApis.getInstance( ).application( )
                                .getCsapSavedFolder( ) ) ) );

        return results;

    }

    public InfrastructureRunner getInfraRunner( ) {

        return infraRunner;

    }

    public ServiceJobRunner getJobRunner( ) {

        return jobRunner;

    }

    public LogRollerRunnable getLogRoller( ) {

        return logRoller;

    }

    public ResourceCollector getServiceResourceRunnable( ) {

        return serviceResourceRunnable;

    }

    public void setServiceManager( ServiceOsManager serviceManager ) {

        this.serviceManager = serviceManager;

    }

    public String getCachedPssMemoryStatistics( ) {

        return cachedPssMemoryStatistics;

    }

}
