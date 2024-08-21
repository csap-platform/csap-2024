package org.csap.agent.ui.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CsapApis;
import org.csap.agent.CsapConstants;
import org.csap.agent.CsapTemplates;
import org.csap.agent.container.C7;
import org.csap.agent.container.kubernetes.K8;
import org.csap.agent.integrations.CsapEvents;
import org.csap.agent.integrations.VersionControl;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.model.*;
import org.csap.agent.services.HostKeys;
import org.csap.agent.stats.service.JavaCollectionAttributes;
import org.csap.agent.ui.editor.ServiceResources;
import org.csap.agent.ui.explorer.OsExplorer;
import org.csap.agent.ui.rest.file.CsapFileManager;
import org.csap.agent.ui.windows.CorePortals;
import org.csap.alerts.AlertSettings;
import org.csap.docs.CsapDoc;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.integations.CsapInformation;
import org.csap.security.CsapUser;
import org.csap.security.CustomRememberMeService;
import org.csap.security.config.CsapSecuritySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.ui.ModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping ( CsapConstants.APP_BROWSER_URL )
public class ApplicationBrowser {

    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    CsapApis csapApis;
    ObjectMapper jsonMapper;
    CsapInformation csapInformation;
    CorePortals corePortals;

    public ApplicationBrowser(
            CsapApis csapApis,
            ObjectMapper jsonMapper,
            CsapInformation csapInformation,
            CorePortals corePortals
    ) {

        this.csapApis = csapApis;
        this.jsonMapper = jsonMapper;
        this.csapInformation = csapInformation;
        this.corePortals = corePortals;

    }

    //
    // Top level browsers for services
    //
    final static String PREFERENCES_COOKIE = "csap-preferences";

    String adminVersion;

    @GetMapping
    public ModelAndView applicationBrowser(
            HttpServletRequest request,
            HttpSession session,
            String layout,
            @RequestParam ( value = CsapConstants.PROJECT_PARAMETER, required = false ) String csapProjectName,
            @CookieValue ( value = PREFERENCES_COOKIE, required = false ) String preferences
    ) {

        ModelAndView mav = new ModelAndView( "app-browser/browser-main" );

        var activeProject = csapProjectName;

        if ( StringUtils.isEmpty( csapProjectName ) ) {

            activeProject = csapApis.application( ).getActiveProjectName( );

        }

        JsonNode userPrefs = jsonMapper.createObjectNode( );
        var theme = "auto";

        if ( StringUtils.isNotEmpty( preferences ) ) {

            try {

                logger.debug( "preferences: {}", preferences );
                userPrefs = jsonMapper.readTree( URLDecoder.decode( preferences, "UTF-8" ) );
                theme = userPrefs.path( "csap-theme" ).asText( theme );

            } catch ( Exception e ) {

                logger.warn( "Failed parsing preferences: {}", CSAP.buildCsapStack( e ) );

            }

        }

        mav.getModelMap( ).addAttribute( "agentProfile", csapApis.application( ).isAgentProfile( ) );

        if ( csapApis.application( ).isAgentProfile( ) ) {

            // only used while browser is loading - then preferences.js sets based on
            // criteria
            theme = "theme-dark agent";

            if ( System.getenv( "dockerHostFqdn" ) != null ) {

                theme = "theme-dark agent theme-forest";

            }

            if ( csapApis.isCrioInstalledAndActive( ) ) {

                mav.getModelMap( ).addAttribute( "crio", true );

            }

        }

        var newSession = csapApis.application( ).getActiveUsers( ).addTrail( "ApplicationBrowser" );

        if ( newSession ) {

            csapApis.events( ).publishUserEvent(
                    CsapEvents.CSAP_UI_CATEGORY + CsapConstants.APP_BROWSER_URL,
                    CsapUser.currentUsersID( ), "portal accessed", "" );

        }

//		mav.getModelMap( ).addAttribute( "isFirstAccess", newSession ) ;
        // bypasss login location
        mav.getModelMap( ).addAttribute( "isFirstAccess", false );

        mav.getModelMap( ).addAttribute( "theme", theme );
        mav.getModelMap( ).addAttribute( "preferences", userPrefs );

        var agentService = csapApis.application( ).findServiceByNameOnCurrentHost( CsapConstants.AGENT_NAME );

        if ( csapApis.application( ).environmentSettings( ).getHostCommands( agentService ).keySet( ).size( ) > 0 ) {

            mav.getModelMap( ).addAttribute( "appCommandMap",
                    csapApis.application( ).environmentSettings( ).getHostCommands( agentService ) );

        }

        var deployedArtifact = csapApis.application( ).getLocalAgent( ).getDefaultContainer( )
                .getDeployedArtifacts( );

        if ( csapApis.application( ).isAdminProfile( ) ) {

            if ( StringUtils.isEmpty( adminVersion ) ) {

                // need to use version for host
                var adminOnHost = csapApis.application( ).findServiceByNameOnCurrentHost( CsapConstants.ADMIN_NAME );

                if ( adminOnHost == null ) {

                    logger.warn( "Did not find {} on host: {}", CsapConstants.ADMIN_NAME,
                            csapApis.application( ).getServicesOnHost( ) );

                } else {

                    var adminReport = csapApis.application( ).getHostStatusManager( ).serviceCollectionReport(
                            List.of( adminOnHost.getHostName( ) ),
                            adminOnHost.getName( ),
//							adminOnHost.getServiceName_Port( ),
                            null );
                    logger.debug( "adminReport: {}", CSAP.jsonPrint( adminReport ) );

                    if ( adminReport != null ) {

                        var adminDeployed = adminReport.findValue( "deployedArtifacts" );

                        if ( adminDeployed != null ) {

                            adminVersion = adminDeployed.asText( "" );

                        }

                    }

                }

            }

            deployedArtifact = adminVersion;

        }

        if ( StringUtils.isEmpty( deployedArtifact ) ) {

            deployedArtifact = "csap";

            if ( Application.isRunningOnDesktop( ) ) {

                // deployedArtifact = "desktop-9901" ;

            }

        }

        mav.getModelMap( ).addAttribute( "deployedArtifact", deployedArtifact );

        String secureUrl = null;

        if ( request.getScheme( ).equals( "http" ) ) {

            secureUrl = csapApis.application( ).configuration( ).getCsapWebServer( ).getSecureUrl( request );

        }

        mav.getModelMap( ).addAttribute( "secureUrl", secureUrl );

        mav.getModelMap( ).addAttribute( "activeProject", activeProject );
        mav.getModelMap( ).addAttribute( "applicationName", csapApis.application( ).getName( ) );
        mav.getModelMap( ).addAttribute( "projectNames", csapApis.application( ).getPackageNames( ) );

        var analyticsUrl = csapApis.application( ).rootProjectEnvSettings( )
                .getAnalyticsUiUrl( )
                + "?life=" + csapApis.application( ).getCsapHostEnvironmentName( );

        mav.getModelMap( ).addAttribute( "analyticsUrl", analyticsUrl );

        var sortedPackages = csapApis.application( ).getRootProject( ).releasePackagesRootFirst( )
                .collect( Collectors.toList( ) );

        mav.getModelMap( ).addAttribute( "sortedPackages", sortedPackages );

        mav.getModelMap( ).addAttribute( "applicationId", csapApis.application( ).rootProjectEnvSettings( )
                .getEventDataUser( ) );

        setCommonAttributes( mav.getModelMap( ), session );

        //
        // Used for ui development on desktop - get more graph data on hostdashboard,
        // host.html, java.html,...
        //
        var isSimulateLiveEnv = true;
        mav.getModelMap( ).addAttribute( "isSimulateLiveEnv", isSimulateLiveEnv );

        if ( isSimulateLiveEnv && csapApis.application( ).isDesktopHost( ) ) {

            if ( csapApis.application( ).isAgentProfile( ) ) {

                var testHost = ( String ) session.getAttribute( TESTHOST );

                if ( StringUtils.isEmpty( testHost ) ) {

                    // testHost = "csap-dev04" ;
                    testHost = CsapConstants.TEST_REMOTE_HOST;

                }

                mav.getModelMap( ).addAttribute( "testHostOnDesktop", testHost );
                mav.getModelMap( ).addAttribute( "testHostForceLocalHost", true );
                mav.getModelMap( ).addAttribute( "kubernetesApiUrl", "http://" + testHost + ".csap.org:8014" );

            } else {

                mav.getModelMap( ).addAttribute( "analyticsUrl",
                        "http://localhost.csap.org:8022/csap-admin/os/performance"
                                + "?life=" + csapApis.application( ).getCsapHostEnvironmentName( ) );

            }

            // mav.getModelMap( ).addAttribute( "applicationId", "demo-xxxCsap" ) ;
            // mav.getModelMap( ).addAttribute( "graphReleasePackage", "CSAP Platform" ) ;

            logger.info( CsapApplication.testHeader( "desktop simulate active" ) );

        }

        if ( StringUtils.isNotEmpty( layout ) ) {

            mav.setViewName( "app-browser/layout" );

        }

        return mav;

    }

    final static String TESTHOST = "testHost";

    String title;

    public void setCommonAttributes(
            ModelMap modelMap,
            HttpSession session
    ) {

        corePortals.addSecurityAttributes( modelMap, session );

        corePortals.setViewConstants( modelMap );

        if ( title == null ) {

//			title = csapApis.application( ).getName( ) ;
            title = csapApis.application( ).getCsapHostEnvironmentName( );

            if ( csapApis.application( ).isAgentProfile( ) ) {

                title = csapApis.application( ).getCsapHostName( );

            }

        }

        modelMap.addAttribute( "pageTitle", title );

        modelMap.addAttribute( "toolsMap", csapInformation.buildToolsMap( ) );
        modelMap.addAttribute( "helpMap", csapApis.application( ).getHelpMenuMap( ) );

        try {

            var loggedInId = CsapUser.currentUsersID( );
            modelMap.addAttribute( "csapUser", loggedInId );
            modelMap.addAttribute( "userid", loggedInId );
            modelMap.addAttribute( "scmUser", loggedInId );

            var principleReport = CsapUser.getPrincipleInfo( );
            // logger.info( "logged in: {} ", CSAP.jsonPrint( principleReport ) ) ;

            var scmUser = loggedInId + VersionControl.AT_BB;

//			var scmUser = principleReport.at( "/cn/0" ).asText( loggedInId ) ;
//
//			// oath given
//			if ( principleReport.has( "givenName" ) ) {
//
//				scmUser = principleReport.path( "givenName" ).asText( loggedInId ) ;
//
//			}

            modelMap.addAttribute( "scmUser", scmUser
                    .toLowerCase( )
                    .replaceAll( " ", "" )
                    .replaceAll( Matcher.quoteReplacement( "'" ), "" ) );

        } catch ( Exception e ) {

            logger.error( "Failed to get security principle {}", CSAP.buildCsapStack( e ) );

        }

        modelMap.addAttribute( "environmentSettings", csapApis.application( ).environmentSettings( ) );
        modelMap.addAttribute( "HISTORY_URL", csapApis.application( ).environmentSettings( ).getHistoryUiUrl( ) );
        modelMap.addAttribute( "METRICS_URL", csapApis.application( ).rootProjectEnvSettings( )
                .getEventMetricsUrl( ) );

        // editor
        modelMap.addAttribute( "applicationBranch", csapApis.application( ).getSourceBranch( ) );
        modelMap.addAttribute( "addHostUrl", csapApis.application( ).getRootProject( ).getInfraAddHost( ) );

        // Host Dashboard
        modelMap.addAttribute( "explorerUrl", OsExplorer.EXPLORER_URL );

        modelMap.addAttribute( "activityUrl", csapApis.application( ).rootProjectEnvSettings( )
                .getHostActivityUrl( ) );
        modelMap.addAttribute( "healthUrl", csapApis.application( ).rootProjectEnvSettings( ).getHostHealthUrl( ) );

        modelMap.addAttribute( "vsphereEnabled", csapApis.application( ).rootProjectEnvSettings( )
                .isVsphereConfigured( ) );

        if ( csapApis.isContainerProviderInstalledAndActive( ) ) {

            modelMap.addAttribute( "containerUrl", csapApis.containerIntegration( ).getSettings( ).getUrl( ) );
            modelMap.addAttribute( "dockerRepository", csapApis.containerIntegration( ).getSettings( )
                    .getTemplateRepository( ) );
            modelMap.addAttribute( "referenceImages", csapApis.application( ).getDockerUiDefaultImages( ) );

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            ServiceInstance kubernetesInstance = csapApis.application( ).kubeletInstance( );

            var urls = kubernetesInstance.getUrl( ).split( "," );
            var apiUrl = urls[ 0 ];

            if ( urls.length >= 2 ) {

                apiUrl = urls[ 1 ];

            }

            modelMap.addAttribute( "kubernetesApiUrl", apiUrl );
            modelMap.addAttribute( "kubernetesNamespaces", csapApis.kubernetes( ).nameSpaces( ) );
            modelMap.addAttribute( "kubernetesServiceTypes", K8.k8TypeList( ) );

        }

    }

    @PostMapping ( "/preferences" )
    public String preferences(
            String preferences,
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws Exception {

        ResponseCookie cookie = ResponseCookie
                .from( PREFERENCES_COOKIE,
                        URLEncoder.encode( preferences, "UTF-8" ) )
                .maxAge( 60 * 60 * 24 * 365 * 10 )
                .domain( CustomRememberMeService.getSingleSignOnDomain( request ) )
                .sameSite( "Lax" )
                .path( "/" )
                .build( );

        response.addHeader( "Set-Cookie", cookie.toString( ) );

        // Cookie cookie = new Cookie(
        // PREFERENCES_COOKIE,
        // URLEncoder.encode( preferences, "UTF-8" ) ) ;
        //
        // logger.info( "preferences: {}", preferences );
        // cookie.setHttpOnly( true ) ;
        // cookie.setDomain( CustomRememberMeService.getSingleSignOnDomain( request ) )
        // ;
        // cookie.setPath( "/" ) ;
        // cookie.setMaxAge( 60 * 60 * 24 * 365 * 10 ) ;
        // response.addCookie( cookie ) ;

        return "cookie stored";

    }

    @GetMapping ( "/host-configuration" )
    public JsonNode hostConfiguration(
            String fromFolder,
            String location,
            String extractDir,
            @RequestParam ( value = "serviceName", required = false, defaultValue = "" ) String serviceName_port
    ) {

        var configReport = jsonMapper.createObjectNode( );

        if ( fromFolder == null ) {

            fromFolder = Application.FileToken.HOME.value;

        }

        var requestedFileOrFolder = csapApis.application( ).getRequestedFile( fromFolder, serviceName_port, false );

        var defaultLocation = requestedFileOrFolder.getAbsolutePath( );

        if ( location != null ) {

            defaultLocation = location;

            if ( Application.isRunningOnDesktop( ) ) {

                defaultLocation.replaceAll( StringEscapeUtils.escapeJava( "\\" ),
                        StringEscapeUtils.escapeJava( "\\\\" ) );

            }

        }

        if ( extractDir != null ) {

            defaultLocation = extractDir;

        }

        configReport.put( "defaultLocation", defaultLocation );

        if ( requestedFileOrFolder != null ) {

            configReport.put( "locationName", requestedFileOrFolder.getName( ) );

        }

        //
        // CSAP command runner
        //
        configReport.put( "csapEnvFile", csapApis.application( ).csapPlatformPath( "bin/csap-environment.sh" )
                .getAbsolutePath( ) );
        configReport.put( "userid", CsapUser.currentUsersID( ) );
        configReport.put( "scriptBase", csapApis.application( ).getScriptToken( ) );
        configReport.set( "clusterHostsMap", csapApis.application( ).buildClusterByPackageInActiveLifecycleReport( ) );
        configReport.set( "allHosts",
                jsonMapper.convertValue( csapApis.application( ).getAllHostsInAllPackagesInCurrentLifecycle( ),
                        ArrayNode.class ) );
        configReport.set( "allHosts",
                jsonMapper.convertValue( csapApis.application( ).getAllHostsInAllPackagesInCurrentLifecycle( ),
                        ArrayNode.class ) );

        var serviceOnHost = csapApis.application( ).findServiceByNameOnCurrentHost( serviceName_port );

        if ( serviceOnHost != null ) {

            configReport.set( "serviceHosts",
                    jsonMapper.convertValue(
                            csapApis.application( ).getActiveProject( ).findHostsForService( serviceOnHost.getName( ) ),
                            ArrayNode.class ) );

        } else {

            configReport.set( "serviceHosts", null );

        }

        if ( requestedFileOrFolder.isFile( )
                && commandRunnerSupportedFiles( requestedFileOrFolder.getName( ) ) ) {

            try {

                var contents = FileUtils.readFileToString( requestedFileOrFolder );
                configReport.put( "fileContents", contents );

            } catch ( Exception e ) {

                logger.warn( "Failed loading contents", CSAP.buildCsapStack( e ) );

            }

        }

        configReport.set( "commandRunnerFiles", buildTemplates( ) );

        return configReport;

    }

    /**
     * @see OsCommandRunner#runCancellable(int, String, File,
     * org.csap.agent.linux.OutputFileMgr, String, Map)
     */
    private boolean commandRunnerSupportedFiles( String fileName ) {

        return fileName.endsWith( ".sh" )
                || fileName.endsWith( ".ksh" )
                || fileName.endsWith( ".py" )
                || fileName.endsWith( ".pl" );

    }

    private ArrayNode buildTemplates( ) {

        var templates = jsonMapper.createArrayNode( );

        var scriptsFolder = CsapTemplates.shell_scripts.getFile( );

        var scriptFolder = new File( csapApis.application( ).getDefinitionFolder( ), "scripts" );

        var scriptNames = scriptFolder.list(
                ( File dir, String name ) -> commandRunnerSupportedFiles( name ) );

        if ( scriptNames != null ) {

            for ( var scriptName : scriptNames ) {

                var template = templates.addObject( );

                template.put( "source", scriptName );
                template.put( "command", scriptName );
                template.put( "description", "project script" );

            }

        }

        if ( scriptsFolder.exists( ) && scriptsFolder.isDirectory( ) ) {

            var templateFiles = Arrays.asList( scriptsFolder.listFiles( ) );

            Collections.sort( templateFiles );

            templateFiles.stream( )
                    .filter( file -> {


                        if ( file.length( ) >= CsapFileManager.getMaxEditSize( ) ) {
                            logger.warn( "Size exceed: {}, Skipping {} ", file.getName( ), CsapFileManager.getMaxEditSize( ) );

                            var template = templates.addObject( );
                            template.put( "source", file.getName( ) );
                            template.put( "error", true );
                            template.put( "reason", "Size Limit: " + file.length( ) + " exceeded: " + CsapFileManager.getMaxEditSize( ) + "" );
                        }

                        return file.length( ) < CsapFileManager.getMaxEditSize( );

                    } )
                    .forEach( scriptFile -> {

                        var template = templates.addObject( );
                        template.put( "source", scriptFile.getName( ) );
                        template.put( "command", scriptFile.getName( ) );
                        template.put( "description", "-" );
                        try ( Stream<String> lines = Files.lines( scriptFile.toPath( ) ) ) {

                            logger.debug( "Reading: {}", scriptFile.getAbsolutePath( ) );


                            // List<String> lines = Files.readAllLines(
                            // scriptFile.toPath() );
                            Optional<String> firstLine = lines.limit( 1 ).findFirst( );

                            if ( firstLine.isPresent( ) ) {

                                var csapHeaderLine = firstLine.get( );

                                if ( csapHeaderLine.length( ) > 0 ) {
                                    var fields = csapHeaderLine.substring( 1 ).split( ",", 2 );

                                    if ( fields.length == 2 ) {

                                        template.put( "command", fields[ 0 ].trim( ) );
                                        template.put( "description", fields[ 1 ].trim( ) );

                                    }
                                }

                                // template.set( "lines", jacksonMapper.convertValue( lines, ArrayNode.class )
                                // );
                            }

                        } catch ( Exception e ) {

                            var reason = CSAP.buildCsapStack( e );
                            template.put( "error", true );
                            template.put( "reason", "Failed to read: " + reason );

                            logger.warn( "Failed to get first line from: {} {}",
                                    scriptFile,
                                    reason );

                        }

                    } );

        }

        return templates;

    }

    @GetMapping ( "/trend/definition" )
    public JsonNode trendingDefinition( ) {

        return csapApis.application( ).environmentSettings( ).getTrendingConfig( );

    }

    public final static String HELM_INFO_URL = "/helm/info";

    @GetMapping ( HELM_INFO_URL )
    public JsonNode helmInfoReport(
            String project,
            String command,
            String chart,
            boolean showAll
    ) {

        var infoReport = jsonMapper.createObjectNode( );


        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );
                urlVariables.set( "project", project );
                urlVariables.set( "command", command );
                urlVariables.set( "chart", chart );
                urlVariables.set( "showAll", Boolean.toString( showAll ) );

                String url = CsapConstants.APP_BROWSER_URL + HELM_INFO_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                infoReport.put( "error", "kubernetes not available" );
                return infoReport;

            }

        }

        var serviceInstance = csapApis.application( ).findServiceByNameOnCurrentHost( chart );

        if ( serviceInstance != null
                && serviceInstance.isHelmConfigured( ) ) {

            chart = serviceInstance.getHelmChartName( );

        }

        var helmCommand = "helm show values " + chart;

        if ( showAll ) {

            helmCommand = "helm show all " + chart;

        }

        if ( command.equals( "helm-readme" ) ) {

            helmCommand = "helm show readme " + chart;

        }

        logger.info( helmCommand );

        var cliResults = csapApis.osManager( ).cli( helmCommand );

        if ( command.equals( "helm-readme" ) ) {

            cliResults = csapApis.fileUtils( ).convertMarkdownToHtml( cliResults, "helm" );

            infoReport.put( C7.response_html.val( ), cliResults );
            infoReport.put( "source", "helm show readme" );

        } else {

            infoReport.put( C7.response_yaml.val( ), cliResults );

        }

        return infoReport;

    }


    @GetMapping ( "/readme" )
    public JsonNode readme(
            String serviceName,
            String readmeName,
            String project
    ) {

        var readMeReport = jsonMapper.createObjectNode( );

        var readMeMarkDown = "\n> ***Readme file not found***, add to csap application definition folder\n";

        var readMeSource = "Application Definition";

//		logger.info( "serviceName: {}, readmeName: {} ", serviceName, readmeName ) ;

        try {

            var restTemplate = new RestTemplate( );

            var theService = csapApis.application( ).findFirstServiceInstanceInLifecycle( serviceName );

            var theServiceResources = DefinitionConstants.serviceResourcesFolder.key( ) + "/" + theService.getName( );

            if ( StringUtils.isEmpty( readmeName ) ) {

                readmeName = "README.md";

            }

            var theServiceLocalReadme = new File(
                    csapApis.application( ).getDefinitionFolder( ),
                    theServiceResources + "/" + readmeName );

//			logger.info( "theServiceLocalReadme: {} ", theServiceLocalReadme ) ;

            if ( !theServiceLocalReadme.exists( ) ) {

                theServiceLocalReadme = new File(
                        csapApis.application( ).getDefinitionFolder( ),
                        theServiceResources + "/read-me.md" );

            }

            var allMdFileNames = findFileNames( theServiceLocalReadme.getParentFile( ), ".md" );
            readMeReport.set( "mdFiles", allMdFileNames );

            if ( theService.isHelmConfigured( ) ) {
                allMdFileNames.add( "helm-readme" );
            }

            if ( theServiceLocalReadme.exists( ) ) {

                readMeSource += " - " + theServiceLocalReadme.getName( );
                readMeMarkDown = Application.readFile( theServiceLocalReadme );

            } else {

                var readme = theService.getReadme( );

                // check for definition overrides

                if ( readme.startsWith( "http" ) ) {

                    readMeMarkDown = restTemplate.getForObject( readme, String.class );
                    readMeSource = readme;

                } else if ( theService.isHelmConfigured( ) ) {

                    readMeMarkDown = helmInfoReport(
                            project,
                            "helm-readme",
                            serviceName,
                            false ).path( C7.response_html.val( ) ).asText( );
                    ;

                } else {

                    readMeMarkDown = readme;

                }

            }

        } catch ( Exception e ) {

            logger.warn( "Failed to get readme: {}", CSAP.buildCsapStack( e ) );

        }

        readMeReport.put( C7.response_html.val( ), csapApis.fileUtils( ).convertMarkdownToHtml( readMeMarkDown, readMeSource ) );
        readMeReport.put( "source", readMeSource );

        return readMeReport;

    }

    private ArrayNode findFileNames(
            File folder,
            String fileExtension
    ) throws Exception {

        if ( !folder.exists( ) ) {
            return jsonMapper.createArrayNode( );
        }

        var matchedFileNames = ( List ) new ArrayList<String>( );

        try ( var walk = Files.walk( folder.toPath( ) ) ) {

            matchedFileNames = walk
                    .filter( p -> !Files.isDirectory( p ) )
                    .map( filepath -> filepath.getFileName( ).toString( ) )
                    // this is a path, not string,
                    // this only test if path end with a certain path
                    // .filter(p -> p.endsWith(fileExtension))
                    // convert path to string first
                    .filter( f -> f.endsWith( fileExtension ) )
                    .collect( Collectors.toList( ) );

        }

        return jsonMapper.convertValue( matchedFileNames, ArrayNode.class );

    }

    public final static String REALTIME_REPORT_URL = "/kubernetes/realtime";

    @GetMapping ( REALTIME_REPORT_URL )
    public JsonNode kubernetesRealtimeReport(
            String project,
            boolean blocking
    ) {

        var kubernetesMetricsReport = jsonMapper.createObjectNode( );
        kubernetesMetricsReport.put( "error", "kubernetes not available" );

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            if ( blocking ) {

                csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

            }

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );
                urlVariables.set( "project", project );

                String url = CsapConstants.APP_BROWSER_URL + REALTIME_REPORT_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return kubernetesMetricsReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            kubernetesMetricsReport = csapApis.kubernetes( ).metricsBuilder( ).cachedKubeletReport( );

        }

        return kubernetesMetricsReport;

    }

    public final static String VOLUME_REPORT_URL = "/kubernetes/volumes";

    @GetMapping ( VOLUME_REPORT_URL )
    public JsonNode kubernetesVolumeReport(
            String project,
            boolean blocking,
            String filter,
            String apiUser
    ) {

        var volumeReport = jsonMapper.createArrayNode( );

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            if ( blocking ) {

                csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

            }

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );

                String url = CsapConstants.APP_BROWSER_URL + VOLUME_REPORT_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return volumeReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            var userName = CsapUser.currentUsersID( );

            if ( StringUtils.isNotEmpty( apiUser ) ) {

                userName = apiUser;

            }

            if ( csapApis.application( ).getActiveUsers( ).addTrail( userName, "browser-volume-report" ) ) {

                csapApis.events( ).publishUserEvent(
                        CsapEvents.CSAP_OS_CATEGORY + "/accessed",
                        userName,
                        "browser-volume-report", "" );

            }

            volumeReport = csapApis.kubernetes( ).reportsBuilder( ).volumeReport( );

        } else {

            // not installed
        }

        return volumeReport;

    }

    public final static String NODE_REPORT_URL = "/kubernetes/nodes";

    @GetMapping ( NODE_REPORT_URL )
    public JsonNode kubernetesNodeReport(
            String project,
            boolean blocking,
            String filter,
            String apiUser
    ) {

        var nodeReport = jsonMapper.createArrayNode( );

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            if ( blocking ) {

                csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

            }

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );

                String url = CsapConstants.APP_BROWSER_URL + NODE_REPORT_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return nodeReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            var userName = CsapUser.currentUsersID( );

            if ( StringUtils.isNotEmpty( apiUser ) ) {

                userName = apiUser;

            }

            if ( csapApis.application( ).getActiveUsers( ).addTrail( userName, "browser-pod-report" ) ) {

                csapApis.events( ).publishUserEvent(
                        CsapEvents.CSAP_OS_CATEGORY + "/accessed",
                        userName,
                        "browser-node-report", "" );

            }

            nodeReport = csapApis.kubernetes( ).reportsBuilder( ).nodeReports( );

        } else {

            // not installed
        }

        return nodeReport;

    }

    public final static String POD_LOG_URL = "/kubernetes/pod/logs";

    @GetMapping ( POD_LOG_URL )
    public JsonNode kubernetesPodLogs(
            String namespace,
            String podName,
            String containerName,
            boolean previousTerminated,
            @RequestParam ( defaultValue = "500" ) int numberOfLines,
            @RequestParam ( defaultValue = "0" ) int since,
            boolean blocking,
            String project,
            String apiUser
    ) {

        var podLogReport = jsonMapper.createObjectNode( );

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            if ( blocking ) {

                csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

            }

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "namespace", namespace );
                urlVariables.set( "podName", podName );
                urlVariables.set( "containerName", containerName );
                urlVariables.set( "previousTerminated", Boolean.toString( previousTerminated ) );
                urlVariables.set( "numberOfLines", Integer.toString( numberOfLines ) );
                urlVariables.set( "since", Integer.toString( since ) );
                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );

                String url = CsapConstants.APP_BROWSER_URL + POD_LOG_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return podLogReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            var userName = CsapUser.currentUsersID( );

            if ( StringUtils.isNotEmpty( apiUser ) ) {

                userName = apiUser;

            }

            if ( csapApis.application( ).getActiveUsers( ).addTrail( userName, "browser-pod-report" ) ) {

                csapApis.events( ).publishUserEvent(
                        CsapEvents.CSAP_OS_CATEGORY + "/accessed",
                        userName,
                        "browser-pod-report", "" );

            }

            podLogReport = csapApis.kubernetes( ).podContainerTail(
                    namespace, podName, containerName,
                    previousTerminated, numberOfLines, since );

        } else {

            // not installed
        }

        return podLogReport;

    }

    public final static String POD_REPORT_URL = "/kubernetes/pods";

    @GetMapping ( POD_REPORT_URL )
    public JsonNode kubernetesPodReport(
            String project,
            String namespace,
            boolean blocking,
            String podName,
            String apiUser
    ) {

        JsonNode podNamespaceReport = jsonMapper.createArrayNode( );

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            if ( blocking ) {

                csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

            }

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "namespace", namespace );
                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );
                urlVariables.set( "podName", podName );

                String url = CsapConstants.APP_BROWSER_URL + POD_REPORT_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return podNamespaceReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            var userName = CsapUser.currentUsersID( );

            if ( StringUtils.isNotEmpty( apiUser ) ) {

                userName = apiUser;

            }

            if ( csapApis.application( ).getActiveUsers( ).addTrail( userName, "browser-pod-report" ) ) {

                csapApis.events( ).publishUserEvent(
                        CsapEvents.CSAP_OS_CATEGORY + "/accessed",
                        userName,
                        "browser-pod-report", "" );

            }

            if ( StringUtils.isEmpty( podName ) ) {

                podNamespaceReport = csapApis.kubernetes( ).reportsBuilder( ).podSummaryReport(
                        namespace, null );

            } else {

                //
                // Used on kubernetes summary view to view pod container information
                //
                podNamespaceReport = csapApis.kubernetes( ).podContainerMetricsReport( namespace,
                        podName );

            }

        } else {

            logger.info( "Kubernetes not running" );

        }

        return podNamespaceReport;

    }

    public final static String POD_RESOURCE_URL = "/kubernetes/namespace/pods";

    @GetMapping ( POD_RESOURCE_URL )
    public JsonNode kubernetesResourceReport(
            String project,
            String kubernetesNamespace,
            boolean blocking,
            String filter,
            String apiUser
    ) {

        logger.info( "running report namespace: {}, podName: {}", kubernetesNamespace );

        var namespaceReport = jsonMapper.createArrayNode( );

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( blocking && csapApis.application( ).isAdminProfile( ) ) {

            csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

        }

        if ( csapApis.application( ).isAdminProfile( ) ) {

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( project );

            // logger.info( "hostReport: {}", CSAP.jsonPrint( allHostReport ) );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "project", project );
                urlVariables.set( "namespace", kubernetesNamespace );
                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );

                String url = CsapConstants.APP_BROWSER_URL + POD_RESOURCE_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );

                logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return namespaceReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            namespaceReport = csapApis.kubernetes( ).reportsBuilder( ).podNamespaceSummaryReport(
                    kubernetesNamespace );

        } else {

            logger.debug( "Kubernetes not running service: {} current host: {}, mapping: {}",
                    csapApis.kubernetes( ).getDiscoveredServiceName( ),
                    csapApis.application( ).getCsapHostName( ),
                    csapApis.application( ).getActiveProject( ).getHostToServicesMap( ) );

        }

        return namespaceReport;

    }

    @GetMapping ( "/hosts" )
    public JsonNode hostsReport(
            String project,
            boolean blocking,
            String filter
    ) {

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( blocking && csapApis.application( ).isAdminProfile( ) ) {

            csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

        }

        return csapApis.application( ).healthManager( ).build_host_report( project );

    }

    @PostMapping ( "/userEvent" )
    public ObjectNode addUserEvent(
            String id,
            String summary,
            @RequestParam ( defaultValue = "no details provided", required = false ) String eventDetails
    ) {

        var addResults = jsonMapper.createObjectNode( );

        var userShort = CsapUser.currentUsersID( );

        if ( csapApis.application( ).isDesktopHost( )
                && userShort.equals( "admin" ) ) {

            userShort = "desktop.test";

        }

        if ( userShort.length( ) > 4 ) {

            var names = userShort.toUpperCase( ).split( "\\." );

            if ( names.length == 2 ) {

                userShort = "";

                for ( var name : names ) {

                    userShort += name.charAt( 0 );

                }

            } else {

                userShort = userShort.substring( 0, 3 );

            }

        }

        var summaryLine = "*** " + userShort + "-" + id + "  " + summary;

        csapApis.events( ).publishUserEvent(
                CsapEvents.CSAP_UI_CATEGORY + "/note",
                CsapUser.currentUsersID( ),
                summaryLine,
                eventDetails );

        addResults.put( "results", "event added" );
        addResults.put( "summary", summaryLine );

        return addResults;

    }

    @GetMapping ( "/launchers" )
    public ArrayNode launchers( ) {

        ArrayNode launchers = jsonMapper.createArrayNode( );

        var demoLauncher = launchers.addObject( );
        demoLauncher.put( "service", "csap-analytics" );
        demoLauncher.put( "label", "CSAP: Analytics" );
        demoLauncher.put( "description", "Launches CSAP Performance Portal" );

        var definitionLaunchers = csapApis.application( ).environmentSettings( ).getQuickLaunchers( );
        var resolvedLaunchers = definitionLaunchers.deepCopy( );

        launchers.addAll( resolvedLaunchers );
        CSAP.jsonStream( resolvedLaunchers )
                .filter( JsonNode::isObject )
                .map( launcher -> ( ObjectNode ) launcher )
                .filter( launcher -> launcher.path( "label" ).asText( ).contains( CsapConstants.CSAP_VARIABLE_PREFIX ) )
                .forEach( launcher -> {

                    var agentService = csapApis.application( )
                            .getServiceInstanceCurrentHost(
                                    CsapConstants.AGENT_NAME );
                    launcher.put( "label", agentService.resolveRuntimeVariables(
                            launcher.path( "label" ).asText( ) ) );

                } );

        logger.debug( "pre and post: \n\t definitionLaunchers: {}\n\t resolvedLaunchers: {} ", definitionLaunchers,
                launchers );

        return launchers;

    }

    @GetMapping ( "/logParsers" )
    public ArrayNode logParsers( ) throws Exception {

        return csapApis.application( ).environmentSettings( ).getLogParsers( );

    }

    @GetMapping ( "/launch/{serviceName}" )
    public ObjectNode serviceLauncher(
            @PathVariable String serviceName,
            @RequestParam ( defaultValue = "-1", required = false ) int launcherIndex
    ) {

        var launchReport = jsonMapper.createObjectNode( );

        String location = null;

        if ( serviceName.equals( "csap-analytics" ) ) {

            location = csapApis.application( ).rootProjectEnvSettings( ).getAnalyticsUiUrl( )
                    + "?life="
                    + csapApis.application( ).getCsapHostEnvironmentName( );

        }

        var instanceReports = service_instances( false, serviceName ).path( "instances" );

        var activeLauncher = launchers( ).path( launcherIndex );

        logger.info( CSAP.buildDescription( "launch request",
                "serviceName", serviceName,
                "launcherIndex", launcherIndex,
                "location", location,
                "instanceReports", instanceReports
        ) );


        if ( instanceReports.isArray( )
                && instanceReports.size( ) > 0 ) {

            var firstInstanceReport = instanceReports.path( 0 );

            var launchCred = firstInstanceReport.path( "launchCred" ).asText( );

            location = firstInstanceReport.path( "launchUrl" ).asText( );


            if ( csapApis.application( ).isDesktopHost( ) ) {

                logger.info( CsapApplication.testHeader("desktop custom logic") ) ;

                var optionalLaunch = CSAP.jsonStream( instanceReports )
                        .filter( instance -> !instance.path( "host" ).asText( ).equals( "localhost" ) )
                        .filter( instance -> instance.path( "running" ).asBoolean( ) )
                        .map( instance -> instance.path( "launchUrl" ).asText( ) )
                        .findFirst( );

                if ( optionalLaunch.isPresent( ) ) {

                    location = optionalLaunch.get( );

                }

            }

            //
            // Fallback: allow launcher to override the specified launch url for the
            // services
            //

            logger.info( CSAP.buildDescription( "Launch Settings",
                    "credential", launchCred,
                    "launchUrl", firstInstanceReport.path( "launchUrl" ).asText( ),
                    "launch definition", activeLauncher ));

            if ( activeLauncher.isObject( ) ) {

                if ( activeLauncher.path( "password" ).isTextual( )
                        && StringUtils.isNotEmpty( launchCred ) ) {

                    launchReport.put( "launchCred", launchCred );

                }

                var hostSpecifier = activeLauncher.path( "host" ) ;
                if ( hostSpecifier.isTextual( ) ) {
                    var optionalLaunch = CSAP.jsonStream( instanceReports )
                            .filter( instance -> instance.path( "host" ).asText( ).equals( hostSpecifier.asText() ) )
                            .map( instance -> instance.path( "launchUrl" ).asText( ) )
                            .findFirst( );
                    if ( optionalLaunch.isPresent( ) ) {
                        location = optionalLaunch.get( );
                    } else {
                        launchReport.put( "reason", "request host not found: " + hostSpecifier.asText() );
                    }
                }

                var optionalUrl = activeLauncher.path( "url" );

                if ( optionalUrl.isTextual( ) ) {

                    location = optionalUrl.asText( );

                }

            }

            var firstServiceInstance = csapApis.application( ).findFirstServiceInstanceInLifecycle( serviceName );

            if ( firstServiceInstance != null ) {

                location = firstServiceInstance.resolveRuntimeVariables( location );

            }

        }

        if ( location == null ) {

            var firstInstance = csapApis.application( ).findFirstServiceInstanceInLifecycle( serviceName );

            if ( firstInstance == null ) {

                launchReport.put( "reason", "Service was not found in lifecycle" );

            } else {

                launchReport.put( "reason", "unable to map to location" );

            }

        } else {

            var agentInstance = csapApis.application( ).findFirstServiceInstanceInLifecycle( CsapConstants.AGENT_NAME );
            location = agentInstance.resolveRuntimeVariables( location );
            // handle multiple urls
            launchReport.put( "location", location.split( "," )[ 0 ] );

        }

        return launchReport;

    }

    public ObjectNode service_instances(
            boolean blocking,
            String name
    ) {
        return service_instances( blocking, name, null, null );
    }

    @GetMapping ( "/service/instances" )

    @CsapDoc ( notes = "Summary information for lifecycle, including services, hosts, scoreCard, errors ", //
            linkTests = {
                    "default package", "releasePackageExample"
            }, //
            linkGetParams = {
                    "blocking=true", CsapConstants.PROJECT_PARAMETER + "=projectName,blocking=true"
            }, //
            produces = {
                    MediaType.APPLICATION_JSON_VALUE
            } )
    public ObjectNode service_instances(
            boolean blocking,
            String name,
            String cluster,
            @RequestParam ( value = CsapConstants.PROJECT_PARAMETER, required = false ) String csapProject
    ) {

        if ( blocking && csapApis.application( ).isAdminProfile( ) ) {

            csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

        }

        if ( csapProject == null ) {

            csapProject = csapApis.application( ).getActiveProjectName( );

        }

        var servicesReport = jsonMapper.createObjectNode( );
        servicesReport.put( "host-time",
                LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) );

        var serviceInstances = csapApis.application( ).serviceInstancesByName(
                csapProject,
                name );

        if ( serviceInstances == null
                && name.equals( ProcessRuntime.unregistered.getId( ) ) ) {

            // handle unregistered services

            if ( csapApis.application( ).isAdminProfile( ) ) {

                var hostsInProject = csapApis.application( )
                        .getProject( csapProject )
                        .getHostsInActiveLifecycleStream( )
                        .collect( Collectors.toList( ) );

                logger.debug( "csapProject: {} hostsInProject: {}", csapProject, hostsInProject );

                serviceInstances = csapApis.application( )
                        .getProject( csapProject )
                        .getHostsInActiveLifecycleStream( )
                        .map( csapApis.application( ).getHostStatusManager( )::findUnregisteredServices )
                        .flatMap( List::stream )
                        .collect( Collectors.toList( ) );

            } else {

                var unregisteredContainers = csapApis.osManager( ).findUnregisteredContainerNames( );

                serviceInstances = unregisteredContainers.stream( )
                        .map( serviceName -> {

                            return ServiceInstance.buildUnregistered(
                                    csapApis.application( ).getCsapHostName( ),
                                    serviceName );

                        } )
                        .collect( Collectors.toList( ) );

            }

        }

//		logger.debug( "serviceInstances: {}", serviceInstances) ;

        if ( serviceInstances != null
                && serviceInstances.size( ) >= 1 ) {

            var envHosts = serviceInstances.stream( )
                    .map( ServiceInstance::getHostName )
                    .collect( Collectors.joining( "," ) );
            servicesReport.put( "envHosts", envHosts );

            var firstInstance = serviceInstances.get( 0 );

            servicesReport.put( "docUrl", firstInstance.getDocUrl( ) );
            servicesReport.put( "processFilter", firstInstance.getProcessFilter( ) );

            if ( firstInstance.hasProcessGroup( ) ) {

                servicesReport.put( "processGroup", firstInstance.getProcessGroup( ) );

                // switch to primary host for cluster if available
                var clusters = serviceInstances.stream( )
                        .map( ServiceInstance::getCluster )
                        .collect( Collectors.toList( ) );

                logger.info( "clusters: {}", clusters );

                if ( StringUtils.isNotEmpty( cluster ) ) {

                    logger.info( "checking for cluster: {}", cluster );

                    var matchedInstance = serviceInstances.stream( )
                            .filter( service -> service.getLifecycle( ).equals( cluster ) )
//							.filter( service -> service.getCluster( ).equals( cluster ) )
                            .findFirst( );

                    if ( matchedInstance.isPresent( ) ) {

                        firstInstance = matchedInstance.get( );
                        logger.info( "Updated firstInstance {}", firstInstance );

                    }

                }

            }

            if ( firstInstance.isHttpCollectionEnabled( ) ) {

                var httpCollectionUrl = firstInstance.getHttpCollectionSettings( )
                        .path( ModelJson.httpCollectionUrl.jpath( ) ).asText( );

                servicesReport.put( "http-collect-url-raw", httpCollectionUrl );

                httpCollectionUrl = firstInstance.resolveRuntimeVariables( httpCollectionUrl );

                servicesReport.put( "http-collect-url", httpCollectionUrl );

            }

            servicesReport.put( ServiceAttributes.description.json( ), firstInstance.getDescription( ) );
            servicesReport.put( ServiceAttributes.deploymentNotes.json( ), firstInstance.getDeploymentNotes( ) );
            servicesReport.put( "csapApi", firstInstance.is_csap_api_server( ) );
            servicesReport.put( "csapApi", firstInstance.is_csap_api_server( ) );
            servicesReport.put( "tomcat", firstInstance.isTomcatPackaging( ) );
            servicesReport.put( "javaJmx", firstInstance.isJavaJmxCollectionEnabled( ) );
            servicesReport.put( "datastore", firstInstance.isDataStore( ) );
            servicesReport.put( "killWarnings", firstInstance.isKillWarnings( ) );
            servicesReport.put( "filesOnly", firstInstance.is_files_only_package( ) );
            servicesReport.put( "statsConfigured", StringUtils.isNotEmpty( firstInstance.getStatsDirectory( ) ) );
            servicesReport.put( "javaJmxCollection", firstInstance.isJavaJmxCollectionEnabled( ) );
            servicesReport.put( "mavenId", firstInstance.getMavenId( ) );
            servicesReport.put( "scmLocation", firstInstance.getScmLocation( ) );
            servicesReport.put( "scmFolder", firstInstance.getScmBuildLocation( ) );
            servicesReport.put( "scmBranch", firstInstance.getDefaultBranch( ) );
            servicesReport.put( "kubernetes", firstInstance.is_cluster_kubernetes( ) );
            servicesReport.put( "helm", firstInstance.isHelmConfigured( ) );
            servicesReport.put( "springboot", firstInstance.is_springboot_server( ) );
//			servicesReport.put( "readme", firstInstance.isReadmeConfigured( ) ) ;
            servicesReport.put( "javaCollection", firstInstance.isJavaCollectionEnabled( ) );

            var alertReport = csapApis.application( ).healthManager( ).buildServiceAlertReport( csapProject, name );
            servicesReport.set( "alertReport", alertReport );

            long count = 0;

            try {

                servicesReport.set( "performanceConfiguration", firstInstance.getPerformanceConfiguration( ) );
                servicesReport.set( "javaLabels", JavaCollectionAttributes.graphLabels( ) );
                servicesReport.set( "jobs", firstInstance.getJobsDefinition( ) );
                servicesReport.set( "serviceLimits",
                        ServiceAlertsEnum.getAdminUiLimits( firstInstance, csapApis.application( )
                                .rootProjectEnvSettings( ) ) );
                servicesReport.put( "parameters", firstInstance.getParameters( ) );
                servicesReport.set( "dockerSettings", firstInstance.getDockerSettings( ) );

                count = fileCount( ServiceResources.serviceResourceFolder(
                        firstInstance.getName( ) ).getAbsoluteFile( ) );

            } catch ( Exception e ) {

                logger.warn( "Failed to count files: {}", CSAP.buildCsapStack( e ) );

            }

            servicesReport.put( "resourceCount", count );

            var allServiceContainers = servicesReport.putArray( "instances" );

            if ( firstInstance.hasProcessGroup( ) ) {

                var processGroupFilter = firstInstance.getProcessGroup( );

                var processGroupInstances = csapApis.application( ).getProject( csapProject ).getServicesOnHost(
                                firstInstance.getHostName( ) )
                        .filter( ServiceInstance::hasProcessGroup )
                        .filter( serviceInstance -> serviceInstance.getProcessGroup( ).equals( processGroupFilter ) )
                        .collect( Collectors.toList( ) );

                logger.info( "{} Found processGroup for service: {}", firstInstance.toSummaryString( ),
                        processGroupInstances );

                serviceInstances = processGroupInstances;

            }

            for ( var serviceInstance : serviceInstances ) {

                //
                // Hook: LIVE data used when in agent mode to collect local variable resolutions
                //
                if ( csapApis.application( ).isAgentProfile( )
                        && !serviceInstance.getHostName( ).equals( csapApis.application( ).getCsapHostName( ) ) ) {

                    continue;

                }

                var serviceContainers = csapApis.application( )
                        .healthManager( )
                        .buildServiceRuntimes(
                                serviceInstance );

                allServiceContainers.addAll( serviceContainers );
                logger.debug( "allServiceContainers: {}, serviceContainers: {}", allServiceContainers.size( ),
                        serviceContainers.size( ) );

            }

        }

        return servicesReport;

    }

    long fileCount( File folderToCount ) throws Exception {

        if ( !folderToCount.exists( ) ) {

            return 0;

        }

        return Files.walk( folderToCount.toPath( ) )
                .parallel( )
                .filter( p -> !p.toFile( ).isDirectory( ) )
                .count( );

    }

    @GetMapping ( "/health/settings" )
    public ObjectNode healthSettings( )
            throws Exception {

        ObjectNode healthSettings = jsonMapper.createObjectNode( );

        AlertSettings alertSettings = csapApis.application( ).configuration( ).getAlerts( );
        HashMap<String, String> settings = new HashMap<>( );
        settings.put( "Health Report Interval", alertSettings.getReport( ).getIntervalSeconds( ) + " seconds" );
        settings.put( "Maximum items to store", alertSettings.getRememberCount( ) + "" );
        settings.put( "Email Notifications", alertSettings.getNotify( ).toString( ) );
        settings.put( "Alert Throttles", alertSettings.getThrottle( ).toString( ) );

        healthSettings.set( "settings", jsonMapper.convertValue( settings, ObjectNode.class ) );

        Map<String, Map<String, String>> healthUrlsByService = csapApis.application( ).healthManager( )
                .buildHealthUrls( );

        healthSettings.set( "healthUrlsByServiceByInstance", jsonMapper.convertValue( healthUrlsByService,
                ObjectNode.class ) );

        return healthSettings;

    }

    @GetMapping ( "/service/summary" )

    @CsapDoc ( notes = "Summary information for lifecycle, including services, hosts, scoreCard, errors ", //
            linkTests = {
                    "default package", "releasePackageExample"
            }, //
            linkGetParams = {
                    "blocking=true", CsapConstants.PROJECT_PARAMETER + "=projectName,blocking=true"
            }, //
            produces = {
                    MediaType.APPLICATION_JSON_VALUE
            } )
    public ObjectNode service_summary(
            boolean blocking,
            @RequestParam ( value = CsapConstants.PROJECT_PARAMETER, required = false ) String csapProject,
            String cluster,
            HttpSession session
    )
            throws Exception {

        ObjectNode servicesReport = jsonMapper.createObjectNode( );

        String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );
        servicesReport.put( "host-time", now );

        if ( StringUtils.isEmpty( csapProject ) ) {

            csapProject = csapApis.application( ).getActiveProjectName( );

        }

        Project requestedProject = csapApis.application( ).getProject( csapProject );

        var healthReport = csapApis.application( ).healthManager( ).build_health_report(
                ServiceAlertsEnum.ALERT_LEVEL, true,
                requestedProject );

        ObjectNode errorsByService = servicesReport.putObject( "errorsByService" );
        JsonNode detailByHost = healthReport.path( HealthManager.HEALTH_DETAILS );
        CSAP.jsonStream( detailByHost )
                .filter( JsonNode::isArray )
                .flatMap( CSAP::jsonStream )
                .filter( alert -> alert.has( "source" ) )
                .filter( alert -> alert.path( "category" ).asText( "none" ).startsWith( "os-process" ) )
                .forEach( alert -> {

                    var source = alert.path( "source" ).asText( "-" );
                    var count = 1 + errorsByService.path( source ).asInt( 0 );
                    errorsByService.put( source, count );

                } );

        // legacy serviceRequest support
        var legacyFilter = csapApis.application( ).getCsapHostEnvironmentName( );

        if ( StringUtils.isNotEmpty( cluster ) ) {

            legacyFilter = csapApis.application( ).getCsapHostEnvironmentName( )
                    + ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER
                    + cluster;

        }

        List<String> environmentHosts = new ArrayList<>( );

        try {

            environmentHosts = requestedProject.getHostsForEnvironment( legacyFilter );

        } catch ( Exception e ) {

            logger.warn( "Failed to find hosts for: {}", CSAP.buildCsapStack( e ) );

        }

        logger.debug( "cluster: {}, lifeCycleHostList: {}, getLifeCycleToHostMap: {} ", cluster, environmentHosts,
                requestedProject.getLifeCycleToHostMap( ) );

//		Collections.sort( environmentHosts ) ;

        logger.debug( "Sorted host map to ensure kubernetes master status processing: {}", environmentHosts );

        var activeServicesReport = servicesReport.putObject( "servicesActive" );
        var serviceTotalCountMap = new TreeMap<String, Integer>( );
        var serviceTypeMap = new TreeMap<String, String>( );
        var serviceRuntimeMap = new TreeMap<String, String>( );
        var processGroupClusterReport = jsonMapper.createObjectNode( );

        if ( csapApis.application( ).isAgentProfile( ) ) {

            csapApis.osManager( ).checkForProcessStatusUpdate( );

            ObjectNode hostMapNode = servicesReport.putObject( HostKeys.host_status.json( ) );

            csapApis.application( ).healthManager( ).agent( ).service_summary(
                    servicesReport, blocking, legacyFilter, activeServicesReport,
                    serviceTotalCountMap,
                    serviceTypeMap, serviceRuntimeMap,
                    processGroupClusterReport,
                    hostMapNode );

            servicesReport.set( "users",
                    csapApis.application( ).getActiveUsers( ).updateUserAccessAndReturnAllActive(
                            securitySettings.getRoles( ).getUserIdFromContext( ),
                            true ) );

            servicesReport.put( "lastOp", csapApis.application( ).getLastOpMessage( ) );

        } else {

            csapApis.application( ).healthManager( ).admin( ).service_summary(
                    servicesReport, blocking,
                    csapProject, legacyFilter,
                    environmentHosts,
                    activeServicesReport, serviceTotalCountMap,
                    serviceTypeMap, serviceRuntimeMap,
                    processGroupClusterReport,
                    null );

            csapApis.application( ).getActiveUsers( ).updateUserAccessAndReturnAllActive(
                    securitySettings.getRoles( ).getUserIdFromContext( ),
                    true );

        }

        servicesReport.set( "processGroupClusterReport", processGroupClusterReport );
        servicesReport.set( "servicesTotal", jsonMapper.valueToTree( serviceTotalCountMap ) );
        servicesReport.set( "servicesType", jsonMapper.valueToTree( serviceTypeMap ) );
        servicesReport.set( "servicesRuntime", jsonMapper.valueToTree( serviceRuntimeMap ) );

        var serviceStartOrder = jsonMapper.createObjectNode( );
        serviceTotalCountMap.keySet( ).stream( )
                .forEach( serviceName -> {

                    var serviceInstance = csapApis.application( ).findFirstServiceInstanceInLifecycle( serviceName );

                    if ( serviceInstance != null ) {

                        serviceStartOrder.put( serviceName, serviceInstance.startOrder( ) );

                    } else {

                        serviceStartOrder.put( serviceName, -1 );

                    }

                } );
        ;

        servicesReport.set( "startOrder", serviceStartOrder );

        var clusters = requestedProject.getClustersToServicesMapInCurrentLifecycle( );
        servicesReport.set( "clusters", jsonMapper.convertValue( clusters, ObjectNode.class ) );

        return servicesReport;

    }

    @Autowired ( required = false )
    CsapSecuritySettings securitySettings;

    @GetMapping ( "/status-report" )
    public JsonNode statusReport(
            boolean blocking,
            String project,
            String filter
    ) {

        if ( StringUtils.isEmpty( project ) ) {

            project = csapApis.application( ).getActiveProjectName( );

        }

        if ( StringUtils.isEmpty( filter ) ) {

            filter = ".*";

        }
        // ArrayList<String> lifeCycleHostList = csapApp
        // .getLifeCycleToHostMap().get(clusterFilter);

        Project requestedProject = csapApis.application( ).getProject( project );

        if ( blocking && csapApis.application( ).isAdminProfile( ) ) {

            csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null );

        }

        var statusReport = csapApis.application( ).healthManager( ).build_host_summary_report( project );

        var includeKubernetesCheck = false;

        if ( csapApis.application( ).isAdminProfile( ) ) {

            includeKubernetesCheck = true; // detects k8s crashed processes

        }

        var healthReport = csapApis.application( ).healthManager( ).build_health_report(
                ServiceAlertsEnum.ALERT_LEVEL, includeKubernetesCheck,
                requestedProject );

        logger.debug( "healthReport: {}", healthReport );

        var alerts = statusReport.putArray( "alerts" );
        // JsonNode detailByHost = healthReport.path( HealthManager.HEALTH_DETAILS ) ;
        JsonNode summaryByHost = healthReport.path( HealthManager.HEALTH_SUMMARY );
        CSAP.jsonStream( summaryByHost )
                .filter( JsonNode::isArray )
                .flatMap( CSAP::jsonStream )
                .map( JsonNode::asText )
                .forEach( desc -> {

                    alerts.add( desc );

                } );

        if ( csapApis.application( ).isAgentProfile( ) ) {

            try {

                csapApis.application( ).healthManager( ).addCpuMetrics( statusReport );
                var interval = csapApis.application( ).metricManager( ).firstHostCollectionInterval( );
                var servicesOnHost = csapApis.application( ).metricManager( ).getOsProcessCollector( interval )
                        .buildServicesAvailableReport( );
                // statusReport.set( "serviceNames", servicesOnHost ) ;

                // map kubernetes services to serviceIds
                var serviceIdMapping = CSAP.jsonStream( servicesOnHost )

                        .map( JsonNode::asText )

                        .collect( Collectors.toMap(
                                serviceOsCollectId -> serviceOsCollectId,
                                serviceOsCollectId -> {

                                    var service = csapApis.application( ).flexFindFirstInstanceCurrentHost(
                                            serviceOsCollectId );

                                    if ( service != null ) {

                                        return service.getName( );

                                    }

                                    return "service-not-found";

                                },
                                ( a, b ) -> a, // merge function should never be used
                                ( ) -> new TreeMap<String, String>( String.CASE_INSENSITIVE_ORDER ) ) );

                statusReport.set( "serviceIdMapping", jsonMapper.convertValue( serviceIdMapping, ObjectNode.class ) );
                // csapApis.application().flexFindFirstInstance( svcName_port_or_name ) ;

                statusReport.set( "servicesWithHealth",
                        jsonMapper.convertValue( csapApis.application( ).healthManager( ).getHealthServiceIds( ),
                                ArrayNode.class ) );

            } catch ( Exception e ) {

                logger.warn( "Failed to build agent services: {}", CSAP.buildCsapStack( e ) );

            }

        }

        statusReport.set( "users",
                csapApis.application( ).getActiveUsers( ).updateUserAccessAndReturnAllActive(
                        securitySettings.getRoles( ).getUserIdFromContext( ),
                        true ) );

        statusReport.put( "kubernetes-master", requestedProject.getKubernetesMasterHost(
                csapApis.application( ).getCsapHostEnvironmentName( ) ) );

        statusReport.put( "kubernetes-service",
                requestedProject.getKubernetesServiceName(
                        csapApis.application( ).getCsapHostEnvironmentName( ) ) );

        var containerService = C7.dockerService.val( );

        if ( csapApis.application( ).findFirstServiceInstanceInLifecycle( "podman-system-service" ) != null ) {

            containerService = "podman-system-service";

        }

        if ( csapApis.application( ).findFirstServiceInstanceInLifecycle( containerService ) == null
                && csapApis.application( ).findFirstServiceInstanceInLifecycle( "docker-monitor" ) != null ) {

            containerService = "docker-monitor";

        }

        statusReport.put( "container-service", containerService );

        return statusReport;

    }

    @Inject
    @Qualifier ( "csapEventsService" )
    private RestTemplate csapEventsService;

    public final static String CSAP_EVENT_REPORT_URL = "/events/csap";

    @GetMapping ( CSAP_EVENT_REPORT_URL )
    public JsonNode csapEvents(
            @RequestParam ( value = CsapConstants.PROJECT_PARAMETER, required = false ) String csapProject,
            @RequestParam ( defaultValue = "100" ) int count,
            @RequestParam ( defaultValue = CsapEvents.CSAP_UI_CATEGORY
                    + "/*" ) String category,
            String from,
            String to,
            boolean isGlobal
    ) {

        logger.info( "category: '{}' from: '{}', to: '{}'", category, from, to );

        if ( StringUtils.isEmpty( csapProject ) ) {

            csapProject = csapApis.application( ).getActiveProjectName( );

        }

        var projectParam = ",project=" + csapProject;

        if ( csapProject.equals( CsapConstants.ALL_PACKAGES ) ) {

            projectParam = "";

        }

        var fromParam = "";

        if ( StringUtils.isNotEmpty( from ) ) {

            fromParam = ",from=" + from;

        }

        var toParam = "";

        if ( StringUtils.isNotEmpty( from ) ) {

            toParam = ",to=" + to;

        }

        var eventReport = jsonMapper.createObjectNode( );

        var projectSettings = csapApis.application( ).rootProjectEnvSettings( );

        if ( count > 2000 ) {

            count = 2000;

        }

        var filteredEvents = eventReport.putArray( "events" );

        if ( !csapApis.application( ).rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

            logger.info( "Stubbing out data for trends - add csap events services" );
            eventReport.put( "count", "disabled" );
            eventReport.put( "message", "csap-event-service disabled - using stub data" );
            return eventReport;

        }

        var eventUrl = projectSettings.getEventQueryUrl( )
                + "?start=0&length=" + count + "&searchText=";

        try {

            var appId = "appId=" + projectSettings.getEventDataUser( );
            var environment = ",lifecycle=" + csapApis.application( ).getCsapHostEnvironmentName( );

            if ( isGlobal ) {
                appId = "";
                environment = "";
            }

//            if ( csapApis.application( ).isDesktopHost( ) ) {
//
//                // appId = "demo-xxxCsap" ;
////				appId = CsapConstants.TEST_REMOTE_APPID ;
//                appId = "";
//                environment = "";
//                logger.warn( CsapApplication.testHeader( "updated appId {}, and using all envs" ), appId );
//
//            }

            var searchTextParam = "appId=" + appId
                    + projectParam
                    + environment
                    + ",simpleSearchText=" + category
                    + ",isDataRequired=false"
                    + fromParam
                    + toParam;

            eventUrl += searchTextParam;

            eventReport.put( "source", eventUrl );

            buildCsapEvents( eventUrl, filteredEvents );

            // eventReport.setAll( eventData ) ;
        } catch ( Exception e ) {

            logger.error( "Failed getting activity count from url: {}, reason: {}", eventUrl, CSAP.buildCsapStack(
                    e ) );
            eventReport.put( "url", eventUrl );
            eventReport.put( "message", "Error during Access: " + e.getMessage( ) );

        }

        return eventReport;

    }

    private void buildCsapEvents(
            String eventUrl,
            ArrayNode filteredEvents
    ) {

        var today = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) );
        var this_year = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy" ) );

        logger.info( "restUrl: {}", eventUrl );

        var eventResponse = csapEventsService.getForObject( eventUrl, ObjectNode.class );

        if ( logger.isDebugEnabled( ) ) {

            logger.info( "eventResponse: {}", CSAP.jsonPrint( eventResponse ) );

        }

        var csapEvents = eventResponse.path( "data" );

        if ( csapEvents.isArray( ) ) {

            CSAP.jsonStream( csapEvents )
                    .filter( JsonNode::isObject )
                    .map( event -> ( ObjectNode ) event )
                    .forEach( rawEvent -> {

                        logger.debug( "rawEvent: {}", rawEvent );
                        var event = filteredEvents.addObject( );
                        var eventDate = rawEvent.path( "createdOn" ).path( "date" ).asText( );
                        var date = eventDate;

                        // used for graph launches
                        var sourceDate = "";

                        try {

                            var dateParsed = LocalDate.parse( date );
                            sourceDate = dateParsed.format( DateTimeFormatter.ofPattern( "MM-dd-yyyy" ) );

                        } catch ( Exception e ) {

                            logger.warn( "Failed parsing event date: {}, {}", date, CSAP.buildCsapStack( e ) );
                            date = "";

                        }

//                        if ( date.equals( today ) ) {
//
//                            date = "";
//
//                        } else if ( date.startsWith( this_year ) ) {

                        try {

                            LocalDate dateParsed = LocalDate.parse( date );
                            date = dateParsed.format( DateTimeFormatter.ofPattern( "MMM dd" ) );
                            if ( eventDate.equals( today ) ) {
                                date = date + " (Today)";
                            }

                        } catch ( Exception e ) {

                            logger.warn( "Failed parsing event date: {}, {}", date, CSAP.buildCsapStack( e ) );
                            date = "";

                        }

//                        }

                        event.put( "sourceDate", sourceDate );
                        event.put( "date", date );
                        event.set( "time", rawEvent.path( "createdOn" ).path( "time" ) );
                        event.set( "host", rawEvent.path( "host" ) );
                        event.set( "summary", rawEvent.path( "summary" ) );
                        event.set( "appId", rawEvent.path( "appId" ) );
                        event.set( "lifecycle", rawEvent.path( "lifecycle" ) );
                        event.set( "category", rawEvent.path( "category" ) );
                        event.set( "user", rawEvent.path( "metaData" ).path( "uiUser" ) );
                        event.set( "id", rawEvent.path( "_id" ).path( "$oid" ) );

                    } );
            ;

        }

    }

    public final static String KUBERNETES_EVENT_REPORT_URL = "/events/kubernetes";

    @GetMapping ( KUBERNETES_EVENT_REPORT_URL )
    public JsonNode kubernetesEvents(
            @RequestParam ( value = CsapConstants.PROJECT_PARAMETER, required = false ) String csapProject,
            @RequestParam ( defaultValue = "100" ) int count
    ) {

        if ( StringUtils.isEmpty( csapProject ) ) {

            csapProject = csapApis.application( ).getActiveProjectName( );

        }

        ObjectNode eventReport = jsonMapper.createObjectNode( );

        if ( count > 2000 ) {

            count = 2000;

        }

        var filteredEvents = eventReport.putArray( "events" );

        if ( csapApis.application( ).isAdminProfile( ) ) {
            // if ( blocking ) {
            // csapApis.application().getHostStatusManager().refreshAndWaitForComplete( null
            // ) ;
            // }

            var allHostReport = csapApis.application( ).healthManager( ).build_host_report( csapProject );

            var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
                    .filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
                    .map( hostReport -> hostReport.path( "name" ).asText( ) )
                    .findFirst( );

            if ( kubernetesHostOptional.isPresent( ) ) {

                var hostName = kubernetesHostOptional.get( );
                MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( );

                urlVariables.set( "apiUser", CsapUser.currentUsersID( ) );
                urlVariables.set( "count", Integer.toString( count ) );

                String url = CsapConstants.APP_BROWSER_URL + KUBERNETES_EVENT_REPORT_URL;
                List<String> hosts = new ArrayList<>( );
                hosts.add( hostName );
                eventReport.put( "source", url );
                logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables );

                JsonNode remoteCall = csapApis.osManager( ).getServiceManager( ).remoteAgentsGet(
                        hosts,
                        url,
                        urlVariables );

                return remoteCall.path( hostName );

            } else {

                logger.warn( "Failed to locate kubernetes host" );
                return eventReport;

            }

        }

        if ( csapApis.isKubernetesInstalledAndActive( ) ) {

            buildKubernetesEvents( filteredEvents, count );

        }

        return eventReport;

    }

    //String shortK8ParseFormat = "MMM dd" ; // "MMM dd" old
    String shortCsapParseFormat = "MM/dd"; // "MMM dd" old

    private void buildKubernetesEvents(
            ArrayNode filteredEvents,
            int maxEvents
    ) {

        var eventReport = csapApis.kubernetes( ).reportsBuilder( ).eventReport( null, maxEvents );
        var today = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( shortCsapParseFormat ) );
        logger.debug( "today: {}", today );
        // String this_year = LocalDateTime.now().format( DateTimeFormatter.ofPattern(
        // "yyyy" ) ) ;

        if ( eventReport.isArray( ) ) {

            CSAP.jsonStream( eventReport )
                    .filter( JsonNode::isObject )
                    .map( event -> ( ObjectNode ) event )
                    .forEach( rawEvent -> {

                        logger.debug( "rawEvent: {}", rawEvent );
                        var event = filteredEvents.addObject( );
                        // "11/01 17:45:32"
                        var rawDate = rawEvent.path( "dateTime" ).asText( );
                        var dateTime = rawDate.split( " ", 2 );
                        var date = "";
                        var time = "";

                        if ( dateTime.length == 2 ) {

                            date = dateTime[ 0 ];
                            time = dateTime[ 1 ];

                            if ( date.equals( today ) ) {

                                date = ""; // leave blank for easier reading

                            } else {

                                try {

                                    LocalDate dateParsed = LocalDate.parse( date );
                                    //
                                    date = dateParsed.format( DateTimeFormatter.ofPattern( shortCsapParseFormat ) );

                                } catch ( Exception e ) {

                                    logger.warn( "Failed parsing event date: source: {}, parsed: {}, error: {}", rawDate, date, CSAP.buildCsapStack( e ) );
                                    date = "";

                                }

                            }

                        }

                        event.put( "date", date );
                        event.put( "time", time );
                        event.put( "host", rawEvent.path( "host" ).asText( ) );
                        event.put( "reason", rawEvent.path( "reason" ).asText( ) );
                        event.put( "simpleName", rawEvent.path( "simpleName" ).asText( ) );
                        event.put( "component", rawEvent.path( "component" ).asText( ) );

                        var summary = rawEvent.path( "message" ).asText( );
                        var count = rawEvent.path( "count" ).asInt( );
                        event.put( "summary", summary );
                        event.put( "count", count );
                        event.set( "namespace", rawEvent.path( "namespace" ) );
                        event.set( "kind", rawEvent.path( "kind" ) );
                        event.set( "type", rawEvent.path( "type" ) );
                        event.set( K8.apiPath.val( ), rawEvent.path( K8.apiPath.val( ) ) );

                    } );
            ;

        }

    }

    @GetMapping ( value = "/event" )
    public ObjectNode get_event( String id ) {

        var projectSettings = csapApis.application( ).rootProjectEnvSettings( );

        String eventUrl = projectSettings.getEventQueryUrl( )
                + "/getById?id=" + id;

        logger.info( "restUrl: {}", eventUrl );

        ObjectNode eventResponse = csapEventsService.getForObject( eventUrl, ObjectNode.class );

        return eventResponse;

    }

}
