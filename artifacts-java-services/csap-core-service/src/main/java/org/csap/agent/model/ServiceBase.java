package org.csap.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.agent.CustomProperty;
import org.csap.agent.integrations.HttpdIntegration;
import org.csap.agent.integrations.VersionControl;
import org.csap.agent.ui.editor.ServiceResources;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * metadata associated with each service
 *
 * @author someDeveloper
 * <p>
 * JsonIgnoreProperties: provides backwards compatability when instance
 * metrics are updated
 */
public abstract class ServiceBase {

    public static final String CSAP_FOLDER_NOT_CONFIGURED = "/csap-folder-not-configured";

    public static final String CONTAINER_PIDS = "CONTAINER_PIDS";

    boolean addChildProcesses = false;
    boolean autoKillEnabled = false;

    private String context = "";
    private String defaultLogToShow = "";

    private String defaultBranch = "";

    // This is the configured via definition
    private int osProcessPriority = 0;
    private String hostFilter = "";
    private String hostName = "";
    private int autoStart = -1; // < 0 indicates disabled

    static int MINIMUM_VALID_AUTOSTART = 1 ;

    // Comparator to push autoStart disabled to the last in the list
    public static <T> Comparator<T> csapAutoStartComparing( ToIntFunction<? super T> keyExtractor ) {
//		logger.info( "csapAutoStartComparing " );

        var theFunction = ( Comparator<T> & Serializable )
                ( c1, c2 ) -> {
                    var firstInt = keyExtractor.applyAsInt( c1 );
                    if ( firstInt < MINIMUM_VALID_AUTOSTART ) {
                        // autostart disabled
                        firstInt = Integer.MAX_VALUE;
                    }
                    var secondInt = keyExtractor.applyAsInt( c2 );
                    if ( secondInt < 0 ) {
                        // autostart disabled
                        secondInt = Integer.MAX_VALUE;
                    }
                    return Integer.compare( firstInt, secondInt );
                };

        return theFunction;

    }


    private String deployTimeOutMinutes = "5";

    //
    // process Mapping
    //
    private String processFilter = null;
    private String processGroup = "";

    private boolean runUsingDocker = false;
    private boolean dataStore = false;
    private boolean messaging = false;
    private boolean tomcatAjp = false;
    private boolean kubernetesMaster = false;
    private ArrayNode kubernetesMasterHostNames = null;
    private String kubernetesNamespace = null;
    private String kubernetesMasterDns = "not-specified";

    private String cookieName = "";
    private String cookieDomain = "";
    private String cookiePath = "";
    private String compression = "";
    private String compressableMimeType = "text/html,text/xml,text/javascript,text/css";
    private String docUrl = "";
    private String description = "";
    private String deploymentNotes = "";
    private String jmxPort = "";

    private String lifecycle = "";
    private String logDirectory = "logs";
    private String logRegEx = ".*";
    private String logJournalServices = "";
    private String mavenId = "";
    private String mavenSecondary = null;
    private boolean allowReleaseFileToOverride = true;
    private String mavenRepo = "";
    private String metaData = "";
    private String platformVersion = "";
    private String port = "0";

    //
    // filesystem links
    //
    private String appDirectory = "";
    private String statsDirectory = "";
    private String libDirectory = "";
    private String propDirectory = "";


    private String versionCommand = "";

    private String scm = VersionControl.ScmProvider.git.key;
    private String scmLocation = "";
    private String scmBuildLocation = "";

    /**
     * @return the collectPort
     */
    public String getCollectPort( ) {

        return collectPort;

    }

    public File getWorkingDirectory( ) {

        return CsapApis.getInstance( ).application( ).getCsapWorkingSubFolder( getName( ) );

    }

    /**
     * @param collectPort the collectPort to set
     */
    public void setCollectPort( String collectPort ) {

        this.collectPort = collectPort;

    }

    /**
     * @return the jmxRemoteHost
     */
    public String getCollectHost( ) {

        return collectHost;

    }

    /**
     * @param collectHost the jmxRemoteHost to set
     */
    public void setCollectHost( String collectHost ) {

        this.collectHost = collectHost;

    }

    /**
     * @return the isAllowReleaseFileToOverride
     */
    public boolean isIsAllowReleaseFileToOverride( ) {

        return allowReleaseFileToOverride;

    }

    /**
     * @param allowReleaseFileToOverride the isAllowReleaseFileToOverride to set
     */
    public void setAllowReleaseFileToOverride( boolean allowReleaseFileToOverride ) {

        this.allowReleaseFileToOverride = allowReleaseFileToOverride;

    }

    final Logger logger = LoggerFactory.getLogger( ServiceBase.class );

    //
    public static final String USER_STOP = "userStop";
    static public final String NOT_DEPLOYED = "-";

    public static String getBootFolder( boolean isLegacy ) {

        if ( isLegacy ) {

            return "jarExtract/";

        } else {

            return "jarExtract/BOOT-INF/";

        }

    }

    public String getScmBuildLocation( ) {

        return scmBuildLocation;

    }

    public void setScmBuildLocation( String scmBuildSubDir ) {

        this.scmBuildLocation = scmBuildSubDir;

    }

    private String scmVersion = "";

    private String name = "";
    // private String servletThreadCount = "50" ;
    // private String servletMaxConnections = "50" ;
    // // ajp nio connector collides with large headers -1 disables timeouts seems
    // // to address
    // private String servletTimeoutMs = "30000" ;
    // private String servletAccept = "0" ;
    private String url = "";
    private String user = null;
    private String disk = "";
    private ClusterType clusterType = ClusterType.SIMPLE;
    private boolean multiplePortsOnHost = false;

    public String getDefaultLogToShow( ) {

        if ( defaultLogToShow.length( ) != 0 ) {

            return defaultLogToShow;

        }

        if ( is_springboot_server( ) ) {

            return "console.log";

        }

        if ( is_docker_server( ) ) {

            return getServiceName_Port( );

        }

        return "catalina.out";

    }

    public void setDefaultLogToShow( String defaultLogToShow ) {

        this.defaultLogToShow = defaultLogToShow;

    }

    public boolean isUpToDate( ) {

        if ( is_springboot_server( ) ) {

            return true;

        }

        if ( getRuntime( ).equals( "tomcat8-5.x" ) ) {

            return true;

        }

        if ( getRuntime( ).equals( "tomcat9.x" ) ) {

            return true;

        }

        return false;

    }

    public boolean isGenerateWebMappings( ) {

        if ( StringUtils.isNotEmpty( getMetaData( ) )
                && getMetaData( ).contains( HttpdIntegration.GENERATE_WORKER_PROPERTIES ) )
            return true;

        return false;

    }

    public boolean isJavaJmxCollectionEnabled( ) {

        return !isSkipJmxCollection( );

    }

    public boolean isSkipJmxCollection( ) {

        if ( is_files_only_package( ) ) {

            return true;

        }

        if ( StringUtils.isEmpty( getJmxPort( ) )
                || getJmxPort( ).equals( "-1" )
                || getJmxPort( ).equals( "disabled" ) ) {

            return true;

        }

        return false;

    }

    public boolean is_tomcat_ajp_secure( ) {

        if ( StringUtils.isNotEmpty( getMetaData( ) )
                && getMetaData( ).contains( "secure" ) )
            return true;

        return false;

    }

    public boolean is_tomcat_nio( ) {

        if ( StringUtils.isNotEmpty( getMetaData( ) )
                && getMetaData( ).contains( "nio" ) )
            return true;

        return false;

    }

    public int getOsProcessPriority( ) {

        return osProcessPriority;

    }

    public void setOsProcessPriority( int osProcessPriority ) {

        this.osProcessPriority = osProcessPriority;

    }

    public boolean isAutoStart( ) {

        if ( autoStart < MINIMUM_VALID_AUTOSTART ) {

            return false;

        }

        return true;

    }

    public boolean is_csap_api_server( ) {

        // getProcessRuntime() == ProcessRuntime.csap_api ||
        return getProcessRuntime( ) == ProcessRuntime.csap_api;

    }

    public boolean is_springboot_server( ) {

        return getProcessRuntime( ) == ProcessRuntime.springboot;

    }

    public boolean is_docker_server( ) {

        return getProcessRuntime( ) == ProcessRuntime.docker;

    }

    public boolean is_unregistered_service( ) {

        return getProcessRuntime( ) == ProcessRuntime.unregistered;

    }

    public String getTomcatJmxName( ) {

        if ( isCsapApiTomcat( ) ) {

            return getName( );

        }

        if ( is_springboot_server( ) ) {

            return "Tomcat";

        }

        return "Catalina";

    }

    public String getDeployFileName( ) {

        String ext = ".zip";

        if ( isTomcatPackaging( ) ) {

            ext = ".war";

        }

        if ( is_springboot_server( ) ) {

            ext = ".jar";

        }

        if ( is_docker_server( ) ) {

            ext = ".repo";

        }

//		if ( hasProcessGroup( ) ) {
//			return getProcessGroup( ) + ext ;
//		}

        return getName( ) + ext;

    }

    public boolean is_os_process_monitor( ) {

        if ( getRuntime( ).equals( ProcessRuntime.os.getId( ) ) ) {

            return true;

        }

        return false;

    }

    public boolean isCsapApiTomcat( ) {

        return getMetaData( ).contains( "collectTomcat" );

    }

    public boolean is_not_os_process_monitor( ) {

        return !is_os_process_monitor( );

    }

    private String collectHost = null;
    private String collectPort = null;

    public boolean is_java_application_server( ) {

        if ( isCsapApiTomcat( ) ) {

            return true;

        }

        return ( getProcessRuntime( ) != null ) &&
                getProcessRuntime( ).isJava( );

    }

    public boolean isTomcatPackaging( ) {

        if ( is_springboot_server( ) ) {

            return false;

        }

        return getProcessRuntime( ).isJava( );

    }

    @JsonIgnore
    public String getProcessFilter( ) {

        if ( StringUtils.isEmpty( processFilter ) &&
                ( is_docker_server( ) || isRunUsingDocker( ) ) ) {

            return CONTAINER_PIDS;

        }

        return processFilter;

    }

    public boolean isUseContainerPids( ) {

        if ( getProcessFilter( ) == null )
            return false;
        return getProcessFilter( ).equals( CONTAINER_PIDS );

    }

    public void setProcessFilter( String processFilter ) {

        if ( processFilter != null ) {

            if ( processFilter.equalsIgnoreCase( "none" ) ) {

                return; // ignore scripts

            }

            this.processFilter = resolveTemplateVariables( processFilter );

        } else {

            this.processFilter = null;

        }

    }

    public boolean is_files_only_package( ) {

        return StringUtils.isEmpty( getProcessFilter( ) );

    }

    public String getDeployTimeOutMinutes( ) {

        return deployTimeOutMinutes;

    }

    public int getDeployTimeOutSeconds( ) {

        int timeAllowedInSeconds = 120;

        try {

            timeAllowedInSeconds = Integer.parseInt( getDeployTimeOutMinutes( ) ) * 60;

        } catch ( NumberFormatException e ) {

            logger.error( "Failed to parse deployment timeout for " + toString( ) );

        }

        return timeAllowedInSeconds;

    }

    public void setDeployTimeOutMinutes( String deployTimeOutMinutes ) {

        this.deployTimeOutMinutes = deployTimeOutMinutes;

    }

    public String getCookieName( ) {

        return cookieName;

    }

    public void setCookieName( String cookieName ) {

        this.cookieName = cookieName;

    }

    public String getCookieDomain( ) {

        return cookieDomain;

    }

    public void setCookieDomain( String cookieDomain ) {

        this.cookieDomain = cookieDomain;

    }

    public String getCookiePath( ) {

        return cookiePath;

    }

    public void setCookiePath( String cookiePath ) {

        this.cookiePath = cookiePath;

    }

    public String getCompression( ) {

        return compression;

    }

    public void setCompression( String compression ) {

        this.compression = compression;

    }

    public String getCompressableMimeType( ) {

        return compressableMimeType;

    }

    public void setCompressableMimeType( String compressableMimeType ) {

        this.compressableMimeType = compressableMimeType;

    }

    public String getDocUrl( ) {

        if ( docUrl.isEmpty( ) && !getScmLocation( ).isEmpty( ) ) {

            String target = getScmLocation( );

            if ( target.endsWith( ".git" ) ) {

                target = target.substring( 0, target.length( ) - 4 );

            }

            if ( !getScmBuildLocation( ).isEmpty( ) && getScmLocation( ).contains( "github.com" ) ) {

                target += "/tree/master" + getScmBuildLocation( );

            }

            return target;

        }

        return docUrl;

    }

    public void setDocUrl( String docUrl ) {

        this.docUrl = docUrl;

    }

    public String getDescription( ) {

        if ( description.length( ) == 0 ) {

            return getName( );

        }

        return description;

    }

    public void setDescription( String description ) {

        this.description = description;

    }

    public String getMavenSecondary( ) {

        return mavenSecondary;

    }

    public void setMavenSecondary( String mavenSecondary ) {

        this.mavenSecondary = mavenSecondary;

    }

    // public String getServletMaxConnections () {
    // return servletMaxConnections ;
    // }
    //
    // public void setServletMaxConnections ( String servletMaxConnections ) {
    // this.servletMaxConnections = servletMaxConnections ;
    // }
    //
    // public String getServletTimeoutMs () {
    // return servletTimeoutMs ;
    // }
    //
    // public void setServletTimeoutMs ( String servletTimeoutMs ) {
    // this.servletTimeoutMs = servletTimeoutMs ;
    // }
    //
    // public String getServletAccept () {
    // return servletAccept ;
    // }
    //
    // public void setServletAccept ( String servletAccept ) {
    // this.servletAccept = servletAccept ;
    // }

    public ClusterType getClusterType( ) {

        return clusterType;

    }

    public String getClusterTypeAsString( ) {

        return clusterType.getJson( );

    }

    @JsonIgnore
    public void setClusterType( ClusterType partitionType ) {

        this.clusterType = partitionType;

    }

    protected String resolveTemplateVariables( String input ) {

        if ( input == null ) {

            return null;

        }

        String result = input.trim( );

        try {

            result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_BASE ),
                    CsapApis.getInstance( ).application( ).getInstallationFolderAsString( ) );

            result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_WORKING ),
                    CsapApis.getInstance( ).application( ).getCsapWorkingFolder( ).getAbsolutePath( ) );

            result = result.replaceAll(
                    Matcher.quoteReplacement( CsapConstants.CSAP_AGENT_URL ),
                    Matcher.quoteReplacement( CsapApis.getInstance( ).application( ).getAgentUrl( getHostName( ),
                            "" ) ) );

            result = result.replaceAll(
                    Matcher.quoteReplacement( CsapConstants.K8_DASHBOARD ),
                    Matcher.quoteReplacement( CsapApis.getInstance( ).application( ).getAgentUrl( getHostName( ),
                            "/location/dashboard" ) ) );

            result = result.replaceAll(
                    Matcher.quoteReplacement( CsapConstants.K8_NODE_PORT ),
                    Matcher.quoteReplacement( CsapApis.getInstance( ).application( ).getAgentUrl( getHostName( ),
                            "/location/nodeport" ) ) );

            result = result.replaceAll(
                    Matcher.quoteReplacement( CsapConstants.K8_INGRESS ),
                    Matcher.quoteReplacement( CsapApis.getInstance( ).application( ).getAgentUrl( getHostName( ),
                            "/location/ingress" ) ) );

            if ( CsapApis.getInstance( ).isKubernetesInstalledAndActive( ) && is_cluster_kubernetes( ) ) {

                var kubeletWorking = CsapApis.getInstance( ).application( )
                        .getCsapWorkingSubFolder( CsapApis.getInstance( ).application( ).kubeletInstance( )
                                .getName( ) );
                result = result.replaceAll(
                        Matcher.quoteReplacement( CsapConstants.K8_CONFIG ),
                        Matcher.quoteReplacement( kubeletWorking.getAbsolutePath( ) + "/configuration" ) );

            }

        } catch ( Exception e ) {

            logger.warn( "Failed processing kubelet variables {}", CSAP.buildCsapStack( e ) );

        }

//		if ( getName( ).equals( "multijvm1" )) {
//			logger.info( "serviceCustomProperties: {}", serviceCustomProperties ) ;
//		}

        for ( var serviceProperty : serviceCustomProperties ) {

            var propertyValue = serviceProperty.getValue( );

            if ( StringUtils.isEmpty( serviceProperty.getValue( ) ) ) {

                propertyValue = "variable-not-set";

            }

            result = result.replaceAll(
                    Matcher.quoteReplacement( serviceProperty.getName( ) ),
                    propertyValue );

        }

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_FQDN_HOST ),
                CsapApis.getInstance( ).application( ).getHostUsingFqdn( getHostName( ) ) );

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_HOST ), getHostName( ) );

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_ENV ),
                CsapApis.getInstance( ).application( ).getCsapHostEnvironmentName( ) );

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.SERVICE_ENV ), getPlatformLifecycle( ) );

        if ( StringUtils.isNotEmpty( getPort( ) )
                && !getPort( ).endsWith( "x" )
                && !getPort( ).contains( CsapConstants.CSAP_VARIABLE_PREFIX ) ) {

            result = result.replaceAll( Pattern.quote( CsapConstants.CSAP_DEF_PORT + "+1" ),
                    addToString( CsapConstants.CSAP_DEF_PORT, getPort( ), 1 ) );
            result = result.replaceAll( Pattern.quote( CsapConstants.CSAP_DEF_PORT + "+2" ),
                    addToString( CsapConstants.CSAP_DEF_PORT, getPort( ), 2 ) );
            result = result.replaceAll( Pattern.quote( CsapConstants.CSAP_DEF_PORT + "+3" ),
                    addToString( CsapConstants.CSAP_DEF_PORT, getPort( ), 3 ) );
            result = result.replaceAll( Pattern.quote( CsapConstants.CSAP_DEF_PORT + "+4" ),
                    addToString( CsapConstants.CSAP_DEF_PORT, getPort( ), 4 ) );
            result = result.replaceAll( Pattern.quote( CsapConstants.CSAP_DEF_PORT + "+5" ),
                    addToString( CsapConstants.CSAP_DEF_PORT, getPort( ), 5 ) );

        }

        // MUST be done afterwards
        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_PORT ), getPort( ) );
        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_NAME ), getName( ) );

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_AJP_PORT ), getAjpPort( ) );

//		if ( isJavaJmxCollectionEnabled( )
//				&& !getJmxPort( ).contains( Pattern.quote("$$" )) ) {
//
//			// legacy test only
//			result = result.replaceAll( Pattern.quote( "$jmxPortPlus1" ), addToString( getJmxPort( ), 1 ) ) ;
//			result = result.replaceAll( Pattern.quote( "$jmxPortPlus2" ), addToString( getJmxPort( ), 2 ) ) ;
//			result = result.replaceAll( Pattern.quote( "$jmxPort+1" ), addToString( getJmxPort( ), 1 ) ) ;
//			result = result.replaceAll( Pattern.quote( "$jmxPort+2" ), addToString( getJmxPort( ), 2 ) ) ;
//
//		}

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_JMX_PORT ), getJmxPort( ) );

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_INSTANCE ),
                getServiceName_Port( ) );

        var serviceWorkingFolder = CsapApis.getInstance( ).application( ).getCsapWorkingSubFolder( getName( ) )
                .getAbsolutePath( );

//		if ( Application.isRunningOnDesktop( ) ) {
//
//			serviceWorkingFolder = "/opt/csap/csap-platform/working/" + getServiceName_Port( ) ;
//
//		}

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_WORKING ), serviceWorkingFolder );

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_CONTEXT ), getContext( ) );

        var resourceFolder = ServiceResources.serviceResourceFolder( getName( ) ).getAbsolutePath( );

        if ( Application.isRunningOnDesktop( ) ) {

            resourceFolder = resourceFolder.replaceAll( Pattern.quote( "\\" ), "/" );

        }

        result = result.replaceAll( Matcher.quoteReplacement( CsapConstants.CSAP_DEF_RESOURCE ), resourceFolder );

        return result;

    }

    private String launchCred = "";

    public String getLaunchCred( ) {

        return launchCred;

    }

    private List<CustomProperty> serviceCustomProperties = new ArrayList<>( );

    public void postProcessCustomProperties( StringBuilder processResults ) {

        try {

            var customProperties = CsapApis.getInstance( ).application( )
                    .environmentSettings( ).getCustomProperties( );

//		logger.info( CsapApplication.arrowMessage( "{} {}" ), getName( ), customProperties ) ;

            var quickLaunchers = CsapApis.getInstance( ).application( )
                    .environmentSettings( ).getQuickLaunchers( );

            serviceCustomProperties.clear( );

            logger.debug( CsapApplication.arrowMessage( "loading properties {}" ), getName( ), customProperties );

            var lastFileContents = "";
            var lastFileName = "";

            for ( var customProperty : customProperties ) {

                var propertyValue = customProperty.getValue( );
                var propFilePath = customProperty.getFile( );

                if ( StringUtils.isNotEmpty( propFilePath ) ) {

                    // resolve any variables

                    propFilePath = resolveTemplateVariables( propFilePath );

                    var sourceFile = new File( propFilePath );

                    if ( sourceFile.exists( ) ) {

                        if ( !propFilePath.equals( lastFileName ) ) {

                            lastFileContents = Application.readFile( sourceFile );
                            lastFileName = propFilePath;

                        }

                        // file path now updated.... read the file and find the property
                        var patternToMatch = Pattern.compile( "\n" + customProperty.getKey( ) + "=([^\n]*)" );
                        var regExMatcher = patternToMatch.matcher( lastFileContents );

                        propertyValue = "not-found";

                        if ( regExMatcher.find( ) ) {

                            propertyValue = regExMatcher.group( 1 );

                        }

                        //
                        // Update credential for passwords so they can be shared. Test: ots only?
                        //
                        customProperty.setValue( propertyValue );

                    }

                }

                var serviceCustom = new CustomProperty( );
                serviceCustom.setName( customProperty.getRawName( ) );
                serviceCustom.setValue( propertyValue );
                serviceCustom.setFile( propFilePath );
                serviceCustomProperties.add( serviceCustom );

                //
                // Updated credential on launchers
                //
                CSAP.jsonStream( quickLaunchers )
                        .filter( JsonNode::isObject )
                        .map( launcher -> ( ObjectNode ) launcher )
                        .filter( launcher -> launcher.path( "service" ).asText( ).equals(
                                getName( ) ) )
                        .forEach( launcher -> {

                            if ( launcher.path( "password" ).asText( ).equals(
                                    serviceCustom.getName( ) ) ) {

                                launchCred = serviceCustom.getValue( );

                            }

                        } );

            }

            //
            // Update application and prop folder with globals
            //
            setAppDirectory( resolveTemplateVariables( getAppDirectory( ) ) );
            setPropDirectory( resolveTemplateVariables( getPropDirectory( ) ) );
            setLogDirectory( resolveTemplateVariables( getLogDirectory( ) ) );
            setStatsDirectory( resolveTemplateVariables( getStatsDirectory( ) ) );

            setVersionCommand( resolveTemplateVariables( getVersionCommand( ) ) );
            ;

            setJmxPort( resolveTemplateVariables( getJmxPort( ) ) );
            setPort( resolveTemplateVariables( getPort( ) ) );

            logger.debug( "{} serviceCustomProperties {}", getName( ), serviceCustomProperties );
        } catch ( Exception e ) {
            if ( logger.isDebugEnabled( ) ) {
                logger.debug( "{} Failed post processing, {}", getName( ), CSAP.buildCsapStack( e ) );
            }
            processResults.append( getName( ) + "Failed variable resolution" + CSAP.buildCsapStack( e ) );
        }

        if ( getAggregateErrorListing( ).length( ) > 0 ) {
            processResults.append( CsapApplication.arrowMessage( getName( ) ) );
            processResults.append( getAggregateErrorListing( ) );
        }

    }

    private String addToString(
            String variableName,
            String source,
            int i
    ) {

        String s = "-1";

        try {

            if ( StringUtils.isNotEmpty( source ) && !source.equals( "none" ) ) {

                s = Integer.toString( Integer.parseInt( source ) + i );

            }

        } catch ( NumberFormatException e ) {

            if ( logger.isDebugEnabled( ) ) {
                logger.warn( "{} Failed parsing port: '{}',  {}", getName( ), source, CSAP.buildCsapStack( e ) );
            }

            if ( getAggregateErrorListing( ).length( ) > MAX_ERROR_LENGTH ) {
                getAggregateErrorListing( ).delete( 0, MAX_ERROR_LENGTH / 3 );
                getAggregateErrorListing( ).append( CsapApplication.arrowMessage( "Truncating" ) );
            }
            var errMessage = "\n Failed processing variable " + variableName
                    + " source: " + CSAP.pad( source ) + CSAP.pad( "reason: " ) + e.getMessage( );

            if ( getAggregateErrorListing( ).indexOf( errMessage ) == -1 ) {
                getAggregateErrorListing( ).append( errMessage );
            } else {
                getAggregateErrorListing( ).append( "." );
            }

        }

        return s;

    }

    public static int MAX_ERROR_LENGTH = 10000;

    public StringBuilder getAggregateErrorListing( ) {
        return aggregateErrorListing;
    }

    StringBuilder aggregateErrorListing = new StringBuilder( );

    public String getContext( ) {

        return context;

    }

    public String getDefaultBranch( ) {

        // Temp fix because many places have wrong entry for svn
        if ( defaultBranch.equalsIgnoreCase( "head" ) && getScm( ).equalsIgnoreCase( "svn" ) ) {

            return "trunk";

        }

        return defaultBranch;

    }

    public String getHostFilter( ) {

        return hostFilter;

    }

    public String getHostName( ) {

        return hostName;

    }

    public String getJmxPort( ) {

        return jmxPort;

    }

    public String getLifecycle( ) {

        return lifecycle;

    }

    public String getCluster( ) {

        if ( lifecycle.contains( ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ) ) {

            return lifecycle.substring( lifecycle.indexOf( ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ) + 1 );

        } else {

            return "all";

        }

    }

    public String getPlatformLifecycle( ) {

        if ( lifecycle.contains( ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ) ) {

            return lifecycle.substring( 0, lifecycle.indexOf( ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ) );

        }

        return lifecycle;

    }

    public String getLogDirectory( ) {

        return logDirectory;

    }

    public String getLogRegEx( ) {

        return logRegEx;

    }

    public String getMavenId( ) {

        return mavenId;

    }

    public String getMavenVersion( ) {

        String version = "none";

        try {

            String[] mavenArray = mavenId.split( ":" );
            version = mavenArray[ 2 ];

        } catch ( Exception e ) {

            logger.debug( "Failed to parse mavenId: {}", mavenId, e );

        }

        return version;

    }

    public String getMavenRepo( ) {

        if ( StringUtils.isEmpty( mavenRepo ) ) {

            logger.debug( "info: {}", CsapApis.getInstance( ).application( ).rootProjectEnvSettings( ) );
            return CsapApis.getInstance( ).application( ).rootProjectEnvSettings( ).getMavenRepositoryUrl( );

        }

        return mavenRepo;

    }

    public String getMetaData( ) {

        return metaData;

    }

    public String getPlatformVersion( ) {

        return platformVersion;

    }

    public String getPort( ) {

        return port;

    }

    public String getDebugPort( ) {

        if ( getPort( ).length( ) >= 3 ) {

            return getPort( ).substring( 0, 3 ) + "9";

        }

        return port;

    }

    public String getAjpPort( ) {

        if ( getPort( ).length( ) >= 3 ) {

            return getPort( ).substring( 0, 3 ) + "2";

        }

        return port;

    }

    public String getLibDirectory( ) {

        if ( libDirectory.length( ) == 0 ) {

            if ( is_springboot_server( ) ) {

                return getBootFolder( false ) + "lib";

            }

        }

        return libDirectory;

    }

    public String getPropDirectory( ) {

        if ( propDirectory.length( ) == 0 ) {

            var defaultPropertyFolder = "/etc";

            if ( is_java_application_server( ) ) {

                if ( is_springboot_server( ) ) {

                    defaultPropertyFolder = getBootFolder( false ) + "classes";

                } else {

                    defaultPropertyFolder = "WEB-INF/classes";

                }

            }

            return defaultPropertyFolder;

        }

        return propDirectory;

    }

    public String paddedId( ) {

//		return StringUtils.rightPad( getName( ), 25 ) + " " + StringUtils.leftPad( getPort( ), 6 ) ;
        return StringUtils.rightPad( getName( ) + "-" + getPort( ), 30 )
                + " " + StringUtils.leftPad( getRawAutoStart() + "", 6 );

    }

    public String getServiceName_Port( ) {

        return name + "_" + getPort( );

    }

    public String getStoppedFileName( ) {

        return name + ".stopped";

    }

    /**
     * Hooks for UI
     *
     * @return
     */
    public String getServerQualifedType( ) {

        if ( is_files_only_package( ) ) {

            return ProcessRuntime.script.getId( );

        }

        return getProcessRuntime( ).getId( );

    }

    public String getServerUiIconType( ) {

        if ( is_files_only_package( ) ) {

            return "package";

        }

        if ( isGenerateWebMappings( ) ) {

            return "webServer";

        }

        if ( isMessaging( ) ) {

            return "messaging";

        }

        if ( isDataStore( ) ) {

            return "database";

        }

        if ( is_os_process_monitor( ) ) {

            return "monitor";

        }

        if ( is_cluster_kubernetes( ) ) {

            return "kubernetes";

        }

        if ( is_csap_api_server( ) ) {

            return "runtime";

        }

        return getRuntime( );

    }

    public String getScm( ) {

        return scm;

    }

    public boolean isGit( ) {

        return getScm( ).equals( VersionControl.ScmProvider.git.key );

    }

    public String getScmLocation( ) {

        return scmLocation;

    }

    public String getScmVersion( ) {

        return scmVersion;

    }

    public String getRuntime( ) {

        return getProcessRuntime( ).getId( );

    }

    public String getUiRuntime( ) {

        var runtime = getProcessRuntime( ).getId( );

        return runtime;

    }

    // legacy - keep it until junits and js updated
    @JsonProperty ( "serviceName" )
    public String getName( ) {

        return name;

    }

    public String getUser( ) {

        if ( StringUtils.isEmpty( user ) && !is_springboot_server( ) &&
                ( is_docker_server( ) || isRunUsingDocker( ) ) ) {

            return "root";

        }

        return user;

    }

    public void setContext( String context ) {

        // if ( getServiceName().equals( "Cssp3ReferenceMq" ) ) {
        // Thread.dumpStack();
        // }
        this.context = resolveTemplateVariables( context );

    }

    public void setDefaultBranch( String defaultBranch ) {

        this.defaultBranch = resolveTemplateVariables( defaultBranch );

    }

    // public void addSocketCount(int socketCount) {
    // this.socketCount += socketCount;
    // }
    public void setHostFilter( String hostFilter ) {

        this.hostFilter = resolveTemplateVariables( hostFilter );

    }

    final static public String HOSTNAME_JSON = "host";

    @JsonProperty ( HOSTNAME_JSON )
    public void setHostName( String hostOptionalFqdn ) {

        // logger.info( "Splitting: {}", hostOptionalFqdn );
        this.hostName = resolveTemplateVariables( hostOptionalFqdn );

    }

    public void setJmxPort( String jmxPort ) {

        this.jmxPort = resolveTemplateVariables( jmxPort );

    }

    public void setLifecycle( String lifecycle ) {

        this.lifecycle = resolveTemplateVariables( lifecycle );

    }

    public void setLogDirectory( String logDirectory ) {

        String hname[] = hostName.split( "-" );

        String suffix = "checkHostSuffix";

        if ( hname.length == 2 ) {

            suffix = hname[ 1 ];

        }

        this.logDirectory = resolveTemplateVariables( logDirectory ).replaceAll( "\\$hsuffix", suffix );

    }

    public void setLogRegEx( String logRegEx ) {

        this.logRegEx = logRegEx;

    }

    public void setMavenId( String mavenId ) {

        this.mavenId = resolveTemplateVariables( mavenId );

    }

    public void setMavenRepo( String mavenRepo ) {

        this.mavenRepo = resolveTemplateVariables( mavenRepo );

    }

    public void setMetaData( String metaData ) {

        this.metaData = metaData;

    }

    public void setPlatformVersion( String platformVersion ) {

        this.platformVersion = resolveTemplateVariables( platformVersion );

    }

    public void setPort( String port ) {

        this.port = resolveTemplateVariables( port );

    }

    public void setPropDirectory( String propDirectory ) {

        this.propDirectory = resolveTemplateVariables( propDirectory );

    }

    public void setScm( String scm ) {

        this.scm = resolveTemplateVariables( scm );

    }

    public void setScmLocation( String scmLocation ) {

        this.scmLocation = resolveTemplateVariables( scmLocation );

    }

    public void setScmVersion( String scmVersion ) {

        this.scmVersion = resolveTemplateVariables( scmVersion );

    }

    public void setScmVersionNotDeployed( ) {

        this.scmVersion = NOT_DEPLOYED;

        if ( hasProcessGroup( ) ) {
            this.scmVersion = "process-group";
        }

    }

    public boolean isScmDeployed( ) {

        return this.scmVersion.length( ) > 0 && !this.scmVersion.equals( NOT_DEPLOYED );

    }

    public final static String RUNTIME = "serverType";

    @JsonProperty ( RUNTIME )
    public void setProcessRuntime( String server ) {

        // this.serverType = resolve_variables( serverType ) ;
        this.processRuntime = ProcessRuntime.findById( server );

    }

    private ProcessRuntime processRuntime;

    @JsonProperty ( RUNTIME )
    public ProcessRuntime getProcessRuntime( ) {

        return processRuntime;

    }

    public void setName( String serviceName ) {

        // this.serviceName = resolve_variables( serviceName );
        this.name = serviceName;

    }

    // public void setServletThreadCount ( String servletThreadCount ) {
    // this.servletThreadCount = servletThreadCount ;
    // }

    public void setUrl( String url ) {

        // this.url = resolveTemplateVariables( url ) ;
        this.url = url;

    }

    public String getUrl( ) {

        if ( url.contains( CsapConstants.CSAP_VARIABLE_PREFIX ) ) {

            // lazy instance based on late configuration of namespace in project loader
            logger.trace( "{} url: {}", getName( ), url );
            url = CsapApis.getInstance( ).application( ).resolveDefinitionVariables( url, ( ServiceInstance ) this );

        }

        return url;

    }

    public void setUser( String user ) {

        this.user = user;

    }

    /**
     * Factorys are a hook to setup a unique context by appending hostName to the
     * regular service context.
     * <p>
     * skipFactoryConfig in metaData is a way for jvm to opt out
     *
     * @return
     */
    public boolean is_cluster_single_host_modjk( ) {

        if ( ( clusterType.equals( ClusterType.SHARED_NOTHING ) )
                && !getName( ).equalsIgnoreCase(
                CsapConstants.AGENT_NAME ) ) {

            return true;

        }

        return false;

    }

    public boolean is_cluster_multi_host_modjk( ) {

        if ( ( clusterType.equals( ClusterType.MULTI_SHARED_NOTHING ) )
                && !getName( ).equalsIgnoreCase(
                CsapConstants.AGENT_NAME ) ) {

            return true;

        }

        return false;

    }

    public boolean is_cluster_modjk( ) {

        if ( clusterType.equals( ClusterType.MODJK ) || clusterType.equals( ClusterType.ENTERPRISE ) ) {

            return true;

        }

        return false;

    }

    public boolean is_cluster_kubernetes( ) {

        return clusterType == ClusterType.KUBERNETES;

    }

    public String toSummaryString( ) {

        return name + "@" + hostName + ":" + port;

    }

    /**
     * deterministic sorting: include service name in case multiple same levels are
     * used
     *
     * @return
     */
    public String getAutoStart( ) {

        if ( autoStart > 0 ) {

            return autoStart + getName( ) + getPort( );

        }

        return Integer.toString( autoStart );

    }

    public int startOrder( ) {

        return autoStart;

    }

    public int getRawAutoStart( ) {

        return autoStart;

    }

    @JsonIgnore
    public void setAutoStart( int autoStart ) {

        this.autoStart = autoStart;

    }

    protected String getRawDisk( ) {

        return disk;

    }

    protected String getDisk( ) {

        if ( disk.length( ) == 0 ) {

            return CsapConstants.CSAP_DEF_WORKING;

        }

        return disk;

    }

    public void setDisk( String disk ) {

        // single space separation
        this.disk = resolveTemplateVariables( disk.replaceAll( "( )+", " " ) );

    }

    /**
     * @return the multiplePortsOnHost
     */
    private boolean isMultiplePortsOnHost( ) {

        return multiplePortsOnHost;

    }

    public String getPerformanceId( ) {

        if ( isMultiplePortsOnHost( ) ) {

            return getServiceName_Port( );

        }

        return getName( );

    }

    /**
     * @param multiplePortsOnHost the multiplePortsOnHost to set
     */
    public void setMultiplePortsOnHost( boolean multiplePortsOnHost ) {

        this.multiplePortsOnHost = multiplePortsOnHost;

    }

    /**
     * @param libDirectory the libDirectory to set
     */
    public void setLibDirectory( String libDirectory ) {

        this.libDirectory = libDirectory;

    }

    public String getLogJournalServices( ) {

        return logJournalServices;

    }

    public void setLogJournalServices( String logJournalServices ) {

        this.logJournalServices = logJournalServices;

    }

    public boolean isRunUsingDocker( ) {

        return runUsingDocker;

    }

    public void setRunUsingDocker( boolean runUsingDocker ) {

        this.runUsingDocker = runUsingDocker;

    }

    public void setMessaging( boolean messaging ) {

        this.messaging = messaging;

    }

    // public boolean isTomcatAjp () {
    // return tomcatAjp;
    // }

    public void setTomcatAjp( boolean tomcatAjp ) {

        this.tomcatAjp = tomcatAjp;

    }

    public void setDataStore( boolean dataStore ) {

        this.dataStore = dataStore;

    }

    public boolean isDataStore( ) {

        return dataStore;

    }

    public boolean isMessaging( ) {

        return messaging;

    }

    public boolean isKillWarnings( ) {

        return isDataStore( ) || isMessaging( );

    }

    public boolean isTomcatAjp( ) {

        return tomcatAjp;

    }

    public boolean isKubernetesMaster( ) {

        return kubernetesMaster;

    }

    public boolean isKubernetesPrimaryMaster( ) {

        if ( isKubernetesMaster( ) ) {

            return getHostName( ).equals( getKubernetesPrimaryMaster( ) );

        }

        return false;

    }

    public void setKubernetesMaster( boolean kubernetesMaster ) {

        this.kubernetesMaster = kubernetesMaster;

    }

    public ArrayNode getKubernetesMasterHostNames( ) {

        return kubernetesMasterHostNames;

    }

    public String getKubernetesPrimaryMaster( ) {

        if ( kubernetesMasterHostNames != null && kubernetesMasterHostNames.size( ) > 0 ) {

            return kubernetesMasterHostNames.get( 0 ).asText( );

        }

        return "not-specified";

    }

    public void setKubernetesMasterHostNames( ArrayNode kubernetesMasterHostNames ) {

        if ( this.kubernetesMasterHostNames == null || this.kubernetesMasterHostNames.size( ) == 0 ) {

            this.kubernetesMasterHostNames = kubernetesMasterHostNames;

        } else {

            logger.info( "Preserving primary kubernetes host: {}. Not adding: {}", this.kubernetesMasterHostNames,
                    kubernetesMasterHostNames );

        }

    }

    @Override
    public String toString( ) {

        StringBuilder builder = new StringBuilder( );
        builder.append( "ServiceBase [context=" );
        builder.append( context );
        builder.append( ", defaultLogToShow=" );
        builder.append( defaultLogToShow );
        builder.append( ", defaultBranch=" );
        builder.append( defaultBranch );
        builder.append( ", osProcessPriority=" );
        builder.append( osProcessPriority );
        builder.append( ", hostFilter=" );
        builder.append( hostFilter );
        builder.append( ", hostName=" );
        builder.append( hostName );
        builder.append( ", autoStart=" );
        builder.append( autoStart );
        builder.append( ", deployTimeOutMinutes=" );
        builder.append( deployTimeOutMinutes );
        builder.append( ", runUsingDocker=" );
        builder.append( runUsingDocker );
        builder.append( ", dataStore=" );
        builder.append( dataStore );
        builder.append( ", messaging=" );
        builder.append( messaging );
        builder.append( ", tomcatAjp=" );
        builder.append( tomcatAjp );
        builder.append( ", kubernetesMaster=" );
        builder.append( kubernetesMaster );
        builder.append( ", kubernetesMasterHostName=" );
        builder.append( kubernetesMasterHostNames );
        builder.append( ", cookieName=" );
        builder.append( cookieName );
        builder.append( ", cookieDomain=" );
        builder.append( cookieDomain );
        builder.append( ", cookiePath=" );
        builder.append( cookiePath );
        builder.append( ", compression=" );
        builder.append( compression );
        builder.append( ", compressableMimeType=" );
        builder.append( compressableMimeType );
        builder.append( ", docUrl=" );
        builder.append( docUrl );
        builder.append( ", description=" );
        builder.append( description );
        builder.append( ", jmxPort=" );
        builder.append( jmxPort );
        builder.append( ", lifecycle=" );
        builder.append( lifecycle );
        builder.append( ", logDirectory=" );
        builder.append( logDirectory );
        builder.append( ", logRegEx=" );
        builder.append( logRegEx );
        builder.append( ", logJournalServices=" );
        builder.append( logJournalServices );
        builder.append( ", mavenId=" );
        builder.append( mavenId );
        builder.append( ", mavenSecondary=" );
        builder.append( mavenSecondary );
        builder.append( ", allowReleaseFileToOverride=" );
        builder.append( allowReleaseFileToOverride );
        builder.append( ", mavenRepo=" );
        builder.append( mavenRepo );
        builder.append( ", metaData=" );
        builder.append( metaData );
        builder.append( ", platformVersion=" );
        builder.append( platformVersion );
        builder.append( ", port=" );
        builder.append( port );
        builder.append( ", libDirectory=" );
        builder.append( libDirectory );
        builder.append( ", propDirectory=" );
        builder.append( propDirectory );
        builder.append( ", appDirectory=" );
        builder.append( appDirectory );
        builder.append( ", scm=" );
        builder.append( scm );
        builder.append( ", scmLocation=" );
        builder.append( scmLocation );
        builder.append( ", scmBuildLocation=" );
        builder.append( scmBuildLocation );
        builder.append( ", scmVersion=" );
        builder.append( scmVersion );
        builder.append( ", serviceName=" );
        builder.append( name );
        // builder.append( ", servletThreadCount=" ) ;
        // builder.append( servletThreadCount ) ;
        // builder.append( ", servletMaxConnections=" ) ;
        // builder.append( servletMaxConnections ) ;
        // builder.append( ", servletTimeoutMs=" ) ;
        // builder.append( servletTimeoutMs ) ;
        // builder.append( ", servletAccept=" ) ;
        // builder.append( servletAccept ) ;
        builder.append( ", url=" );
        builder.append( url );
        builder.append( ", user=" );
        builder.append( user );
        builder.append( ", disk=" );
        builder.append( disk );
        builder.append( ", clusterType=" );
        builder.append( clusterType );
        builder.append( ", multiplePortsOnHost=" );
        builder.append( multiplePortsOnHost );
        builder.append( ", collectHost=" );
        builder.append( collectHost );
        builder.append( ", collectPort=" );
        builder.append( collectPort );
        builder.append( ", processFilter=" );
        builder.append( processFilter );
        builder.append( ", processRuntime=" );
        builder.append( processRuntime );
        builder.append( "]" );

        return WordUtils.wrap( builder.toString( ), 140, "\n\t\t", false );

    }

    public String getKubernetesNamespace( ) {

        return kubernetesNamespace;

    }

    public void setKubernetesNamespace( String kubernetesNamespace ) {

        this.kubernetesNamespace = kubernetesNamespace;

    }

    public String getKubernetesMasterDns( ) {

        return kubernetesMasterDns;

    }

    public void setKubernetesMasterDns( String kubernetesDns ) {

        this.kubernetesMasterDns = kubernetesDns;

    }

    public String getDeploymentNotes( ) {

        return deploymentNotes;

    }

    public void setDeploymentNotes( String deploymentNotes ) {

        this.deploymentNotes = deploymentNotes;

    }

    public boolean isAddChildProcesses( ) {

        return addChildProcesses;

    }

    public void setAddChildProcesses( boolean addChildProcesses ) {

        this.addChildProcesses = addChildProcesses;

    }

    public boolean isAutoKillEnabled( ) {

        return autoKillEnabled;

    }

    public void setAutoKillEnabled( boolean autoKillEnabled ) {

        this.autoKillEnabled = autoKillEnabled;

    }

    public boolean hasProcessGroup( ) {

        return StringUtils.isNotEmpty( getProcessGroup( ) );

    }

    public String getProcessGroup( ) {

        return processGroup;

    }

    public void setProcessGroup( String groupName ) {

        this.processGroup = groupName;

    }

    public String getAppDirectory( ) {

        if ( appDirectory.length( ) == 0 ) {

            var defaultAppFolder = CSAP_FOLDER_NOT_CONFIGURED;
            return defaultAppFolder;

        }

        return appDirectory;

    }

    public void setAppDirectory( String appDirectory ) {

        this.appDirectory = resolveTemplateVariables( appDirectory );

    }

    public String getStatsDirectory( ) {

        return statsDirectory;

    }

    public void setStatsDirectory( String statsDirectory ) {

        this.statsDirectory = statsDirectory;

    }

    public String getVersionCommand( ) {
        return versionCommand;
    }

    public void setVersionCommand( String versionCommand ) {
        this.versionCommand = versionCommand;
    }
}
