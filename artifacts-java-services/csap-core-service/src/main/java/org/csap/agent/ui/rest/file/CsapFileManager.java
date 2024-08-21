package org.csap.agent.ui.rest.file;

import java.io.BufferedWriter ;
import java.io.DataInputStream ;
import java.io.File ;
import java.io.FileInputStream ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.io.PrintWriter ;
import java.nio.file.Files ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.stream.Collectors ;

import jakarta.inject.Inject ;
import jakarta.servlet.ServletOutputStream ;
import jakarta.servlet.http.HttpServletRequest ;
import jakarta.servlet.http.HttpServletResponse ;
import jakarta.servlet.http.HttpSession ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.Application.FileToken ;
import org.csap.agent.model.ServiceBase ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecurityRoles ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.MediaType ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.PutMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.server.ResponseStatusException ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * UI controller for browsing/viewing/editing files
 *
 * @author someDeveloper
 *
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 *
 *
 *
 */
@Controller
@RequestMapping ( CsapConstants.FILE_URL )
@CsapDoc ( title = "File Operations" , notes = {
		"File browser/manager, and associated rest operations. Includes viewing, saving, editing files",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class CsapFileManager {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static final String DOCKER_HOST_FILE_TOKEN = "//" ;

	@Inject
	public CsapFileManager (
			CsapApis csapApis,
			FileApiUtils fileApiUtils ) {

		this.csapApis = csapApis ;
		this.fileApiUtils = fileApiUtils ;

	}

	CsapApis csapApis ;
	FileApiUtils fileApiUtils ;

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;


	@RequestMapping ( "propertyEncoder" )
	public String propertyEncoder (
									ModelMap modelMap ,
									@RequestParam ( value = "path" , required = false , defaultValue = "none" ) String path ,
									HttpServletRequest request ,
									HttpSession session )
		throws IOException {

		fileApiUtils.setCommonAttributes( modelMap, request, "Property Encoder" ) ;
		modelMap.addAttribute( "name", csapApis.application( ).getCsapHostName( ) ) ;
		return "misc/property-encoder" ;

	}

	public static final String FILE_MANAGER = "/FileManager" ;

	@RequestMapping ( FILE_MANAGER )
	public String fileManager (
								@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String serviceFlexId ,
								@RequestParam ( value = "fromFolder" , defaultValue = "." ) String fromFolder ,
								@RequestParam ( value = "showDu" , required = false ) String showDu ,
								String nfs ,
								String containerName ,
								ModelMap modelMap ,
								HttpServletRequest request ,
								HttpSession session ) {

		logger.trace( CsapApplication.testHeader( ) ) ;

		ServiceInstance service = null ;

		if ( StringUtils.isNotEmpty( serviceFlexId ) ) {

			service = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceFlexId ) ;

		}

		if ( service != null ) {

			modelMap.addAttribute( "serviceName", service.getName( ) ) ;

		}

		if ( StringUtils.isNotEmpty( nfs ) ) {

			fromFolder = fileApiUtils.findNfsPath( fromFolder, nfs ) ;

		}

		if ( containerName != null ) {

			modelMap.addAttribute( "containerName", containerName ) ;

		}

		fileApiUtils.setCommonAttributes( modelMap, request, "File Manager" ) ;

		modelMap.addAttribute( "fromFolder", fromFolder ) ;

		// Tool tips
		Map<String, String> diskPathsForTips = new HashMap<>( ) ;
		modelMap.addAttribute( "diskPathsForTips", diskPathsForTips ) ;

		var workingFolder = csapApis.application( ).getRequestedFile( fromFolder, serviceFlexId, false ) ;

		if ( workingFolder.exists( ) ) {

			diskPathsForTips.put( "fromDisk", pathForTips( fromFolder, serviceFlexId ) ) ;

		} else {

			diskPathsForTips.put( "fromDisk", ServiceBase.CSAP_FOLDER_NOT_CONFIGURED ) ;

		}

		if ( StringUtils.isNotEmpty( nfs ) ) {

			// hook for nfs on ui in admin
			diskPathsForTips.put( "fromDisk", FileToken.ROOT.value + "/" + fromFolder ) ;

		}

		diskPathsForTips.put( "homeDisk", pathForTips( FileToken.HOME.value, serviceFlexId ) ) ;
		diskPathsForTips.put( "stagingDisk", pathForTips( FileToken.PLATFORM.value, serviceFlexId ) ) ;
		diskPathsForTips.put( "installDisk", pathForTips( FileToken.PLATFORM.value + "/..", serviceFlexId ) ) ;
		diskPathsForTips.put( "processingDisk", pathForTips( csapApis.application( ).getCsapWorkingFolder( )
				.getAbsolutePath( ),
				serviceFlexId ) ) ;

		if ( service != null ) {

//			diskPathsForTips.put( "appDisk", pathForTips( FileToken.ROOT.value, service.getAppDirectory( ) ) ) ;
//			diskPathsForTips.put( "propDisk", pathForTips( FileToken.PROPERTY.value, serviceFlexId ) ) ;

			diskPathsForTips.put( "fromDisk", service.getWorkingDirectory( ).getAbsolutePath( ) ) ;

			if ( ! service.getWorkingDirectory( ).exists( ) ) {

				diskPathsForTips.remove( "fromDisk" ) ;

			}

			if ( ! service.getAppDirectory( ).equals( ServiceBase.CSAP_FOLDER_NOT_CONFIGURED ) ) {

				diskPathsForTips.put( "appDisk", service.getAppDirectory( ) ) ;

			}

			// use fully resolved paths for configuration and app folders
			diskPathsForTips.put( "propDisk",
					csapApis.application( ).getRequestedFile(
							FileToken.PROPERTY.value, serviceFlexId, false )
							.getAbsolutePath( ) ) ;

			File jmeterReportFolder = new File( service.getWorkingDirectory( ), "logs/reports" ) ;
			logger.debug( "Checking: {}", jmeterReportFolder ) ;

			if ( jmeterReportFolder.exists( ) ) {

//				diskPathsForTips.put( "jmeterDisk", pathForTips( fromFolder + "/logs/reports", serviceFlexId ) ) ;
				diskPathsForTips.put( "jmeterDisk", jmeterReportFolder.getAbsolutePath( ) ) ;

			}

			
			if ( service.is_docker_server( )
					|| ( csapApis.containerIntegration( ) != null
							&& csapApis.containerIntegration( ).is_docker_folder( service ) ) ) {

				String baseDocker = containerName ;

				if ( StringUtils.isEmpty( containerName ) ) {

					// legacy
					baseDocker = csapApis.containerIntegration( ).determineDockerContainerName( service ) ;

				}

				modelMap.addAttribute( "dockerBase", baseDocker ) ;

				// String baseDocker = csapApis.containerIntegration( ).determineDockerContainerName( service
				// );

				var serviceProperyFolder = csapApis.application( ).resolveDefinitionVariables( service
						.getPropDirectory( ), service ) ;

				if ( serviceProperyFolder.startsWith( FileApiUtils.NAMESPACE_PVC_TOKEN ) ) {

					serviceProperyFolder = fileApiUtils.buildNamespaceListingPath( service, serviceProperyFolder ) ;

					diskPathsForTips.put( "propDisk", serviceProperyFolder ) ;

				} else if ( ! serviceProperyFolder.startsWith( DOCKER_HOST_FILE_TOKEN ) ) {

					diskPathsForTips.put( "propDisk", "dockerContainer:" + serviceProperyFolder ) ;

				} else {

					diskPathsForTips.put( "propDisk", serviceProperyFolder.substring( 1 ) ) ;

				}

				var serviceAppFolder = csapApis.application( ).resolveDefinitionVariables( service.getAppDirectory( ),
						service ) ;

				if ( serviceAppFolder.startsWith( FileApiUtils.NAMESPACE_PVC_TOKEN ) ) {

					serviceAppFolder = fileApiUtils.buildNamespaceListingPath( service, serviceAppFolder ) ;

					diskPathsForTips.put( "appDisk", serviceAppFolder ) ;

				} else if ( ! serviceAppFolder.startsWith( DOCKER_HOST_FILE_TOKEN ) ) {

					if ( serviceAppFolder.contains( ServiceBase.CSAP_FOLDER_NOT_CONFIGURED ) ) {

						serviceAppFolder = "/" ;

					}

					;

					diskPathsForTips.put( "appDisk", "dockerContainer:" + serviceAppFolder ) ;

					String appPath = "dockerContainer:" + serviceAppFolder ;
					diskPathsForTips.put( "appDisk", appPath ) ;

				} else {

					diskPathsForTips.put( "appDisk", serviceAppFolder.substring( 1 ) ) ;

				}

			}

		}

		if ( csapApis.isContainerProviderInstalledAndActive( ) ) {

			try {

				logger.trace( "container provider found" ) ;

				var dockerRoot = csapApis.containerIntegration( ).getCachedSummaryReport( ).path( "rootDirectory" ).asText(
						"_error_" ) ;
				diskPathsForTips.put( "dockerDisk", dockerRoot ) ;

				if ( isDockerFolder( fromFolder ) ) {

					String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
					diskPathsForTips.put( "fromDisk", dockerRoot + "~" + dockerTarget ) ;

				}

			} catch ( Exception e ) {

				logger.error( "Failed to parse docker information: {}", CSAP.buildCsapStack( e ) ) ;

			}

		} else {

			logger.trace( "container provider not found" ) ;

		}

		try {

			var containersFolder = new File( "/var/lib/containers" ) ;

			if ( containersFolder.isDirectory( ) ) {

				diskPathsForTips.put( "containersDisk", "/var/lib/containers" ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to parse docker information: {}", CSAP.buildCsapStack( e ) ) ;

		}

		try {

			var kubletFolder = new File( "/var/lib/kubelet" ) ;

			if ( kubletFolder.isDirectory( ) ) {

				diskPathsForTips.put( "kubernetesDisk", "/var/lib/kubelet" ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to parse docker information: {}", CSAP.buildCsapStack( e ) ) ;

		}

		try {

			var customFolders = new StringBuilder( ) ;
			var envFolders = CsapApis.getInstance( ).application( ).environmentSettings( )
					.getFileManagerShortcuts( ) ;

			var csapAgent = CsapApis.getInstance( ).application( ).getServiceInstanceCurrentHost(
					CsapConstants.AGENT_NAME ) ;

			for ( var folderLabel : envFolders.keySet( ) ) {

				if ( customFolders.length( ) > 0 ) {

					customFolders.append( ",,," ) ;

				}

				customFolders.append( folderLabel ) ;
				customFolders.append( ",," ) ;
				customFolders.append( csapAgent.resolveRuntimeVariables( envFolders.get( folderLabel ) ) ) ;

			}

			if ( customFolders.length( ) > 3 ) {

				diskPathsForTips.put( "customFolders", customFolders.toString( ) ) ;

			}

//			if ( StringUtils.isNotEmpty( csapApis.application( ).getCsapCoreService( ).getPerformanceFolder( ) ) ) {
//
//				var perfFolder = new File( csapApis.application( ).getCsapCoreService( ).getPerformanceFolder( ) ) ;
//
//				diskPathsForTips.put( "perfFolder", perfFolder.getAbsolutePath( ) ) ;
//
//			}
//
//			if ( StringUtils.isNotEmpty( csapApis.application( ).getCsapCoreService( ).getApplicationFolder( ) ) ) {
//
//				var appFolder = new File( csapApis.application( ).getCsapCoreService( ).getApplicationFolder( ) ) ;
//
//				diskPathsForTips.put( "appFolder", appFolder.getAbsolutePath( ) ) ;
//
//			}

			
			var userHomeFolder = "/home/" + csapApis.security( ).getRoles( ).getUserIdFromContext( ) ;

			if ( CsapApplication.isMacOs( ) ) {

				userHomeFolder = "/Users" ;

			}

			var userHomeFile = new File( userHomeFolder ) ;

			if ( userHomeFile.exists( ) ) {

				diskPathsForTips.put( "userFolder", userHomeFile.getAbsolutePath( ) ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to parse docker information: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return CsapConstants.FILE_URL + "/file-browser" ;

	}

	private String pathForTips ( String location , String serviceName ) {

		return csapApis.application( ).getRequestedFile( location, serviceName, false ).getAbsolutePath( ) ;

	}

	@RequestMapping ( "/browser/{browseId}" )
	public String fileBrowser (
								@PathVariable ( value = "browseId" ) String browseId ,
								@RequestParam ( value = "showDu" , required = false ) String showDu ,
								ModelMap modelMap ,
								HttpServletRequest request ,
								HttpSession session ,
								PrintWriter writer ) {

		fileApiUtils.setCommonAttributes( modelMap, request, "File Browser" ) ;
		JsonNode browseSettings = getBrowseSettings( browseId ) ;

		if ( browseSettings.isMissingNode( )
				|| ! browseSettings.has( "group" ) ) {

			// logger.info( "settingsNode: {}", settingsNode );
			writer.println( "requested browse group not found: " + browseId ) ;
			writer.println( "Contact administrator" ) ;
			return null ;

		}

		if ( csapApis.application( ).isAdminProfile( ) ) {
			// csapApis.application().getRootModel().getAllPackagesModel().getServiceInstances(
			// serviceName )

			String cluster = browseSettings.get( "cluster" ).asText( ) ;
			ArrayList<String> clusterHosts = csapApis.application( ).getActiveProject( ).getAllPackagesModel( )
					.getLifeClusterToHostMap( ).get( cluster ) ;

			logger.debug( "specified: {}, Keys: {}", cluster, csapApis.application( ).getActiveProject( )
					.getAllPackagesModel( )
					.getLifeClusterToHostMap( ).keySet( ) ) ;

			if ( clusterHosts == null || clusterHosts.size( ) == 0 ) {

				writer.println( "Incorrect browser configuration - very settings: " + browseSettings.get( "cluster" )
						.asText( ) ) ;
				return null ;

			}

			return "redirect:" + csapApis.application( ).getAgentUrl( clusterHosts.get( 0 ), "/file/browser/"
					+ browseId, false ) ;

		}

		csapApis.security( ).getRoles( ).addRoleIfUserHasAccess( session, browseSettings.get( "group" ).asText( ) ) ;

		if ( ! hasBrowseAccess( session, browseId ) ) {

			logger.info( "Permission denied for accessing {}, Confirm: {} is a member of: {}",
					browseId, csapApis.security( ).getRoles( ).getUserIdFromContext( ),
					browseSettings.get( "group" ).asText( ) ) ;
			return "csap/security/accessError" ;

		}

		modelMap.addAttribute( "serviceName", null ) ;
		modelMap.addAttribute( "browseId", browseId ) ;
		modelMap.addAttribute( "browseGroup", getBrowseSettings( browseId ).get( "group" ).asText( ) ) ;

		modelMap.addAttribute( "fromFolder", Application.FileToken.ROOT.value ) ;

		return CsapConstants.FILE_URL + "/file-browser" ;

	}

	private boolean hasBrowseAccess ( HttpSession session , String browseId ) {

		JsonNode browseSettings = getBrowseSettings( browseId ) ;

		if ( browseSettings.isMissingNode( )
				|| ! browseSettings.has( "group" ) ) {

			return false ;

		}

		logger.info( "Checking access: {}", browseSettings ) ;

		return csapApis.security( ).getRoles( ).hasCustomRole( session, browseSettings.get( "group" ).asText( ) ) ;

	}

	private JsonNode getBrowseSettings ( String browseId ) {

		JsonNode groupFileNode = (JsonNode) csapApis.application( ).rootProjectEnvSettings( ).getFileBrowserConfig( )
				.at( "/" + browseId ) ;
		return groupFileNode ;

	}


	@RequestMapping ( "/getFilesJson" )
	public void getFilesJson (
								@RequestParam ( value = "browseId" , required = true ) String browseId ,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder ,
								@RequestParam ( value = "showDu" , required = false ) String showDu ,
								@RequestParam ( defaultValue = "false" ) boolean useRoot ,
								@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String serviceId ,

								String containerName ,
								HttpSession session ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws IOException {

		logger.debug( CSAP.buildDescription(
				"File Listing",
				"fromFolder", fromFolder,
				"serviceId", serviceId,
				"showDu", showDu,
				"browseId", browseId,
				"fromFolder", fromFolder,
				"useRoot", useRoot ) ) ;

		response.setHeader( "Cache-Control", "no-cache" ) ;
		response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

		File targetFile = csapApis.application( ).getRequestedFile( fromFolder, serviceId, false ) ;

		fileApiUtils.auditTrail( targetFile, "listing" ) ;

		if ( StringUtils.isNotEmpty( browseId ) ) {
			// browse access requires explicit membership

			String browseFolder = getBrowseSettings( browseId ).get( "folder" ).asText( ) ;
			targetFile = new File( browseFolder,
					fromFolder.substring( browseId.length( ) ) ) ;

			if ( ! hasBrowseAccess( session, browseId )
					|| ( ! Application.isRunningOnDesktop( ) && ! targetFile.getCanonicalPath( ).startsWith(
							browseFolder ) ) ) {

				accessViolation( response, targetFile, browseFolder ) ;
				return ;

			}

		} else {

			// general access requires admin
			if ( ! csapApis.security( ).getRoles( ).getAndStoreUserRoles( session, null )
					.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

				accessViolation( response, targetFile, "/" ) ;
				return ;

			}

		}

		Map<String, String> duLines = new HashMap<>( ) ;

		if ( showDu != null ) {

			duLines = runDiskUsage( targetFile ) ;

		}

		ArrayNode fileListing ;

		ServiceInstance service = null ;

		if ( StringUtils.isNotEmpty( serviceId ) ) {

			service = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceId ) ;

		}

		// Special handling for allowing specification of docker app and property
		// folders

		var dockerContainerToken = "dockerContainer:/" ;
		var useDockerBrowser = false ;
		if ( service != null
				&& csapApis.containerIntegration( ) != null
				&& csapApis.containerIntegration( ).is_docker_folder( service ) ) {

			var prop_path = Application.FileToken.PROPERTY.value + "/" ;

			if ( fromFolder.equals( prop_path ) ) {

				var serviceProperyFolder = csapApis.application( ).resolveDefinitionVariables( service
						.getPropDirectory( ), service ) ;

				if ( serviceProperyFolder.startsWith( DOCKER_HOST_FILE_TOKEN ) ) {

					targetFile = new File( serviceProperyFolder,
							fromFolder.substring( FileToken.PROPERTY.value.length( ) ) ) ;

				} else {

					useDockerBrowser = true ;

				}

			} else if ( fromFolder.startsWith( dockerContainerToken ) ) {

				useDockerBrowser = true ;

			}

		}

		var propFolder = Application.FileToken.PROPERTY.value + "/" ;

		logger.info( CSAP.buildDescription(
				"File Browsing",
				"service", service,
				"useRoot", useRoot,
				"useDockerBrowser", useDockerBrowser,
				"targetFile", targetFile,
				"targetFile.exists( )", targetFile.exists( ),
				"fromFolder", fromFolder,
				"dockerContainerToken", dockerContainerToken ) ) ;

		if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

			fileListing = fileApiUtils.buildListingUsingDocker(
					fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ),
					duLines,
					fromFolder ) ;

		} else if ( useDockerBrowser ) {

			fileListing = buildListingUsingDockerBrowser(
					fromFolder,
					serviceId,
					containerName,
					duLines,
					service,
					dockerContainerToken,
					propFolder );

		} else if (  ! targetFile.isDirectory( ) && fileApiUtils.isCompressedFile( fromFolder ) ) {

			fileListing = fileApiUtils.buildListingUsingZip( targetFile, duLines, fromFolder ) ;

		} else if ( ! targetFile.exists( ) || ! targetFile.isDirectory( ) || useRoot ) {

			fileListing = fileApiUtils.buildListingUsingRoot( targetFile, duLines, fromFolder ) ;

		} else {

			logger.debug( "Getting files in {}", targetFile.getAbsolutePath( ) ) ;

			if ( ! targetFile.canRead( ) ) {

				logger.debug( "Building listing using root os command: {}" ) ;
				fileListing = fileApiUtils.buildListingUsingRoot( targetFile, duLines, fromFolder ) ;

			} else {

				logger.debug( "Building listing using java" ) ;
				fileListing = fileApiUtils.buildListingUsingJava( targetFile, fromFolder, duLines ) ;

			}

			logger.info( "Listing Size: {}", fileListing.size( ) ) ;

		}

		if ( csapApis.application( ).isDesktopHost( ) && fromFolder.contains( "pod--test" ) ) {

			var podJson = csapApis.application( ).check_for_stub( "", "linux/ls-pod.json" ) ;
			logger.info( podJson ) ;
			fileListing = (ArrayNode) jacksonMapper.readTree( podJson ) ;

		}

		//
		//  Treat jars,wars, zips as folders that can be explored
		//
//		if ( !fileApiUtils.isCompressedFile( fromFolder ) ) {
			CSAP.jsonStream( fileListing )
					.filter( JsonNode::isObject )
					.map( fileItem -> ( ObjectNode ) fileItem )
					.filter( fileReport -> !fileReport.path( "folder" ).asBoolean( ) )
					.filter( fileReport -> {
						return fileApiUtils.isCompressedFile(
								fileReport.path( "name" ).asText( ) );
					} )
					.forEach( compressedFileListing -> {
						compressedFileListing.put( "folder", true );
						compressedFileListing.put( "compressed", true );
						compressedFileListing.put( "lazy", true );
					} );
//		}

		response.getWriter( ).println(
				jacksonMapper.writeValueAsString( fileListing ) ) ;

	}

	private ArrayNode buildListingUsingDockerBrowser(
			String fromFolder,
			String serviceId,
			String containerName,
			Map<String, String> duLines,
			ServiceInstance service,
			String dockerContainerToken,
			String propFolder
	) {
		ArrayNode fileListing;
		// handle file browser of docker services properties and app folder
		String pathToRequested = containerName;

		if ( StringUtils.isEmpty( pathToRequested ) ) {

			var foundContainer = service.findContainerName( serviceId ) ;

			if ( StringUtils.isNotEmpty( foundContainer ) ) {

				pathToRequested = foundContainer ;

			}

			logger.info( "pathToRequested: {}", pathToRequested ) ;

			if ( StringUtils.isEmpty( pathToRequested ) ) {

				// handle kubernetes first service where id has been stripped
				pathToRequested = service.findContainerName( service.getName( ) + "-1" ) ;

			}

			// legacy
			if ( StringUtils.isEmpty( pathToRequested ) ) {

				pathToRequested = csapApis.containerIntegration( ).determineDockerContainerName( service ) ;

			}

			logger.info( "pathToRequested: {}", pathToRequested ) ;

		}
		// String pathToRequested = csapApis.containerIntegration( ).determineDockerContainerName(
		// service ) + "/";

		if ( fromFolder.startsWith( propFolder ) ) {

			pathToRequested += fromFolder.substring( propFolder.length( ) ) + service.getPropDirectory( ) + "/" ;

		} else if ( fromFolder.startsWith( dockerContainerToken ) ) {

//				pathToRequested += service.getAppDirectory( ) + "/" ;
			pathToRequested += fromFolder.substring( dockerContainerToken.length( ) - 1 ) ;

		}

		fileListing = fileApiUtils.buildListingUsingDocker(
				pathToRequested,
				duLines,
				FileToken.DOCKER.value + pathToRequested ) ;
		return fileListing;
	}


	private Map<String, String> runDiskUsage ( File targetFile )
		throws IOException {

		var collectScript = List.of(
				"#!/bin/bash",
				"timeout 60 du --summarize --block-size=1M --one-file-system "
						+ targetFile.getAbsolutePath( ) + "/*" ) ;
		
		

		var scriptOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "ls-du", collectScript ) ;
		scriptOutput = csapApis.application( ).check_for_stub( scriptOutput, "linux/ls-du-using-root.txt" ) ;

		var scriptOutputLines = scriptOutput.split( "\n" ) ;

		logger.debug( "scriptOutputLines size: {}, collectScript: {} ",
				scriptOutputLines.length, collectScript ) ;

		var diskNameToSizeMap = Arrays.stream( scriptOutputLines )
				.filter( StringUtils::isNotEmpty )
				.map( CsapConstants::singleSpace )
				.filter( line -> ! line.startsWith( "#" ) )
				.map( line -> line.split( " ", 2 ) )
				.filter( keyValueArray -> keyValueArray.length == 2 )
				.collect( Collectors.toMap( keyValueArray -> keyValueArray[1],
						keyValueArray -> keyValueArray[0] ) ) ;

		return diskNameToSizeMap ;

	}


	private void accessViolation ( HttpServletResponse response , File targetFile , String browseFolder )
		throws IOException ,
		JsonProcessingException {

		logger.debug( "Verify access: {} by {}", browseFolder, targetFile.getCanonicalPath( ) ) ;
		ArrayNode childArray = jacksonMapper.createArrayNode( ) ;
		ObjectNode itemJson = childArray.addObject( ) ;
		itemJson.put( "name", "permission denied" ) ;
		itemJson.put( "title", "permission denied" ) ;

		response.getWriter( ).println(
				jacksonMapper.writeValueAsString( childArray ) ) ;

	}

	DateTimeFormatter fileDateFormatter = DateTimeFormatter.ofPattern( "HH:mm:ss, MMM dd yyyy" ) ;

	public static int BYTE_DOWNLOAD_CHUNK = 1024 * 10 ;

	@RequestMapping ( "/downloadFile/{fileName:.+}" )
	public void downloadFile (
								@PathVariable ( value = "fileName" ) String doNotUseThisAsItIsNotUrlEncoded ,
								@RequestParam ( value = "browseId" , required = false , defaultValue = "" ) String browseId ,
								@RequestParam ( defaultValue = "false" ) boolean forceText ,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder ,
								@RequestParam ( value = "isBinary" , required = false , defaultValue = "false" ) boolean isBinary ,
								@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String svcName ,
								@RequestParam ( value = "isLogFile" , required = false , defaultValue = "false" ) boolean isLogFile ,
								HttpServletRequest request ,
								HttpServletResponse response ,
								HttpSession session )
		throws IOException {

		logger.info( "{} downloading service: {}, browseId: {} , fromFolder: {}, isBinary: {}",
				CsapUser.currentUsersID( ), svcName, browseId, fromFolder, isBinary ) ;

		if ( fromFolder.startsWith( Application.FileToken.JOURNAL.value ) ) {

			String journalService = fromFolder.substring( Application.FileToken.JOURNAL.value.length( ) + 1 ) ;
			String since = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) ;
			String target = "/os/journal?service="
					+ journalService
					+ "&since=" + since
					+ "&numberOfLines=500&reverse=false&json=false" ;

			response.sendRedirect( csapApis.application( ).getAgentUrl( csapApis.application( ).getCsapHostName( ),
					target ) ) ;
			return ;

		}

		File targetFile = csapApis.application( ).getRequestedFile( fromFolder, svcName, isLogFile ) ;

		// Restricted browse support
		if ( browseId != null && browseId.length( ) > 0 ) {
			// browse access requires explicit membership

			String browseFolder = getBrowseSettings( browseId ).get( "folder" ).asText( ) ;
			targetFile = new File( browseFolder,
					fromFolder.substring( browseId.length( ) ) ) ;

			if ( ! hasBrowseAccess( session, browseId ) || ! targetFile.getCanonicalPath( ).startsWith(
					browseFolder ) ) {

				if ( ! Application.isRunningOnDesktop( ) ) {

					accessViolation( response, targetFile, browseFolder ) ;
					return ;

				} else {

					logger.info( "Skipping access checks on desktop" ) ;

				}

			}

			if ( targetFile == null || ! targetFile.exists( ) ) {

				logger.warn( "Request file system does not exist: {}.  Check if {}  is bypassing security: ",
						targetFile.getCanonicalPath( ), CsapUser.currentUsersID( ) ) ;
				response.getWriter( ).println( "Invalid path " + fromFolder ) ;
				return ;

			}

		} else {

			if ( targetFile == null || ! targetFile.exists( ) ) {

				if ( ! csapApis.security( ).getRoles( )
						.getAndStoreUserRoles( session, null )
						.contains( CsapSecurityRoles.INFRA_ROLE ) ) {

					logger.warn( "Requested file does not exist: {}.  Check if {}  is bypassing security: ",
							targetFile.getCanonicalPath( ), CsapUser.currentUsersID( ) ) ;
					response.getWriter( ).println( "Invalid path " + fromFolder ) ;
					return ;

				} else {

					logger.info(
							"Requested file not readable by csap: {}. Attempt to access with restricted permissions",
							targetFile.getAbsolutePath( ) ) ;

				}

			}

			// if it is not an admin - only allow viewing of files in processing
			// folder
			if ( ! csapApis.security( ).getRoles( ).getAndStoreUserRoles( session, null )
					.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

				// run secondary check
				if ( ! targetFile.getCanonicalPath( ).startsWith(
						csapApis.application( ).getCsapWorkingFolder( ).getCanonicalPath( ) ) ) {

					logger.warn(
							"Attempt to access file system: {}. Only {} is permitted. Check if {}  is bypassing security: ",
							targetFile.getCanonicalPath( ), csapApis.application( ).getCsapWorkingFolder( )
									.getCanonicalPath( ),
							CsapUser.currentUsersID( ) ) ;
					response.getWriter( ).println( "*** Content protected: can be accessed by admins " + fromFolder ) ;
					return ;

				}

			}

			// Only allow infra admin to view security files.
			if ( ! csapApis.security( ).getRoles( ).getAndStoreUserRoles( session, null )
					.contains( CsapSecurityRoles.INFRA_ROLE ) ) {

				// run secondary check
				if ( fileApiUtils.isInfraOnlyFile( targetFile ) ) {

					logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security.", fromFolder,
							CsapUser.currentUsersID( ) ) ;
					response.getWriter( ).println( "*** Content masked: can be accessed by infra admins "
							+ fromFolder ) ;
					return ;

				}

			}

		}

		String contentType = MediaType.TEXT_PLAIN_VALUE ;

		boolean isHtml = false ;

		if ( forceText ) {

			contentType = MediaType.TEXT_PLAIN_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".html" ) ) {

			contentType = "text/html" ;
			isHtml = true ;

		} else if ( targetFile.getName( ).endsWith( ".xml" ) || targetFile.getName( ).endsWith( ".jmx" ) ) {

			contentType = MediaType.APPLICATION_XML_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".json" ) ) {

			contentType = MediaType.APPLICATION_JSON_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".gif" ) ) {

			contentType = MediaType.IMAGE_GIF_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".png" ) ) {

			contentType = MediaType.IMAGE_PNG_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".jpg" ) ) {

			contentType = MediaType.IMAGE_JPEG_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".gz" )
				|| targetFile.getName( ).endsWith( ".zip" )) {

			isBinary = true ;

		}

		// User is downloading to their desktop
		if ( isBinary ) {

			contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE ;
			response.setContentLength( (int) targetFile.length( ) ) ;
			response.setHeader( "Content-disposition", "attachment; filename=\""
					+ targetFile.getName( ) + "\"" ) ;

		}

		response.setContentType( contentType ) ;
		response.setHeader( "Cache-Control", "no-cache" ) ;

		logger.debug( "file: {}", targetFile.getAbsolutePath( ) ) ;
		fileApiUtils.auditTrail( targetFile, "download" ) ;
		// csapApis.events().publishUserEvent( CsapEvents.CSAP_OS_CATEGORY +
		// "/file/download",
		// CsapUser.currentUsersID(),
		// csapApis.events().fileName( targetFile, 100 ),
		// targetFile.getAbsolutePath() ) ;

		if ( forceText && targetFile.length( ) > getMaxEditSize( ) ) {

			String contents = "Error: selected file has size " + targetFile.length( ) / 1024
					+ " kb; it exceeds the max allowed of: " + getMaxEditSize( ) / 1024
					+ "kb\n Use view or download to access on your desktop;  optionally CSAP upload can be used to update." ;

			logger.warn( "Failed to get file {}, reason: {}", targetFile.getAbsolutePath( ), contents ) ;

			response.getOutputStream( ).print( contents ) ;

		} else {

			if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

				String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
				String[] dockerContainerAndPath = fileApiUtils.splitDockerTarget( dockerTarget ) ;

				long maxSizeForDocker = getMaxEditSize( ) ; // getMaxEditSize()
															// ;

				if ( ! forceText ) {

					maxSizeForDocker = 500 * maxSizeForDocker ; // still limit to
																// avoid heavy
																// reads

				}

				if ( csapApis.isCrioInstalledAndActive( )
						&& dockerTarget.contains( OsManager.CRIO_DELIMETER ) ) {

					var containerName = dockerContainerAndPath[0] ;

					if ( containerName.startsWith( "/" ) ) { // docker listings

						containerName = containerName.substring( 1 ) ;

					}

					csapApis.crio( ).writeContainerFileToHttpResponse(
							isBinary,
							containerName,
							dockerContainerAndPath[1],
							response,
							maxSizeForDocker,
							FileApiUtils.CHUNK_SIZE_PER_REQUEST ) ;

				} else {

					csapApis.containerIntegration( ).writeContainerFileToHttpResponse(
							isBinary,
							dockerContainerAndPath[0],
							dockerContainerAndPath[1],
							response,
							maxSizeForDocker,
							FileApiUtils.CHUNK_SIZE_PER_REQUEST ) ;

				}

			} else {

				if ( fileApiUtils.isCompressedFileInPath( fromFolder) ) {
					targetFile = fileApiUtils.extractFileFromZip( targetFile ) ;
				}

				if ( ! targetFile.canRead( ) ) {

					fileApiUtils.addUserReadPermissions( targetFile ) ;

				}

				if ( csapApis.application( ).isAgentProfile( ) && isHtml && ! isBinary ) {

					csapApis.application( ).create_browseable_file_and_redirect_to_it( response, targetFile ) ;

				} else {

					writeFileToOutputStream( response, targetFile ) ;

				}

			}

		}

	}

	private void writeFileToOutputStream ( HttpServletResponse response , File targetFile )
		throws IOException {

		try ( DataInputStream in = new DataInputStream( new FileInputStream(
				targetFile.getAbsolutePath( ) ) );
				ServletOutputStream servletOutputStream = response.getOutputStream( ); ) {

			// if ( isHtml ) {
			// addHtmlBrowsingSupport( servletOutputStream, targetFile );
			// }

			byte[] bbuf = new byte[ BYTE_DOWNLOAD_CHUNK ] ;

			int numBytesRead ;
			long startingMax = targetFile.length( ) ;
			long totalBytesRead = 0L ; // hook for files that are being updated

			while ( ( in != null ) && ( ( numBytesRead = in.read( bbuf ) ) != -1 )
					&& ( startingMax > totalBytesRead ) ) {

				totalBytesRead += numBytesRead ;
				servletOutputStream.write( bbuf, 0, numBytesRead ) ;
				servletOutputStream.flush( ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn(
					"Failed accessing file - suspected nfs - using root tail to load 3mb as a workaround. reason: {}",
					reason ) ;
			response.getWriter( ).println( CsapConstants.CONFIG_PARSE_ERROR + "Failed accessing file: " + targetFile
					+ ". Attempting using root..." ) ;

			var tailScript = List.of(
					"#!/bin/bash",
					"tail -c 3m " + targetFile.getAbsolutePath( ) + " 2>&1" ) ;

			String tailOutput = "" ;

			try {

				logger.debug( "Running: {}", tailScript ) ;
				tailOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "permissions", tailScript ) ;

			} catch ( Exception tailE ) {

				logger.warn( "Failed running: {}", CSAP.buildCsapStack( tailE ) ) ;

			}

			response.getWriter( ).println( tailOutput ) ;

		}

	}

	public final static String EDIT_URL = "/editFile" ;
	public final static String SAVE_URL = "/saveChanges" ;
	public final static String FILE_SYSTEM_URL = "/filesystem" ;

	private final boolean isDockerFolder ( String fromFolder ) {

		return fromFolder.startsWith( Application.FileToken.DOCKER.value ) ;

	}

	@PutMapping ( value = FILE_SYSTEM_URL , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	@ResponseBody
	public ObjectNode filesystem_rename (
											String fromFolder ,
											String newName ,
											boolean rename ,
											boolean root ,
											@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String svcNamePort )
		throws IOException {

		ObjectNode updateResults = jacksonMapper.createObjectNode( ) ;

		logger.info( CSAP.buildDescription( "file rename",
				"fromFolder", fromFolder,
				"newName", newName,
				"rename", rename,
				"root", root ) ) ;

		File workingItem = csapApis.application( ).getRequestedFile( fromFolder, svcNamePort, false ) ;

		File updatedItem = csapApis.application( ).getRequestedFile( newName, svcNamePort, false ) ;

		if ( newName.startsWith( "/" ) ) {

			updatedItem = new File( newName ) ;

		}

		if ( workingItem.getAbsolutePath( ).startsWith( csapApis.application( ).getDefinitionFolder( )
				.getAbsolutePath( ) ) ) {

			csapApis.application( ).getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		var operation = "copy" ;

		if ( rename ) {

			operation = "rename" ;

		}

		csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/" + operation, CsapUser
				.currentUsersID( ),
				"Source: " + workingItem.getName( ) + " destination: " + updatedItem.getName( ),
				"Previous: " + workingItem.getAbsolutePath( ) + "\n New: " + updatedItem.getAbsolutePath( ) ) ;

		var message = operation + ": " + workingItem.getAbsolutePath( ) + " to: " + updatedItem.getAbsolutePath( ) ;

		try {

			if ( rename ) {

				if ( root ) {

					var lines = List.of(
							"\\mv --verbose --force " + workingItem.getAbsolutePath( ) + " " + updatedItem
									.getAbsolutePath( ) ) ;

					var scriptOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "file-move", lines ) ;

					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					if ( workingItem.renameTo( updatedItem ) ) {

						message += "  - SUCCESS" ;

					} else {

						message += "  - FAILED. Verify: csap user has r/w. Alternately - use command runner" ;

					}

				}

			} else {

				if ( root ) {

					var lines = List.of(
							"\\cp --recursive --verbose --force " + workingItem.getAbsolutePath( ) + " " + updatedItem
									.getAbsolutePath( ) ) ;

					var scriptOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "filesystem-copy", lines ) ;

					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					if ( workingItem.isDirectory( ) ) {

						FileUtils.copyDirectory( workingItem, updatedItem ) ;
						message += " Folder copy - SUCCESS" ;

					} else {

						Files.copy( workingItem.toPath( ), updatedItem.toPath( ) ) ;
						message += " File copy - SUCCESS" ;

					}

				}

			}

		} catch ( Exception e ) {

			var errMessage = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed renaming items: {}", errMessage ) ;

			message += " - FAILED " + "\n" + errMessage ;

		}

		updateResults.put( "plain-text", message ) ;

		return updateResults ;

	}

	@DeleteMapping ( value = FILE_SYSTEM_URL , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	@ResponseBody
	public ObjectNode filesystem_delete (
											String fromFolder ,
											boolean recursive ,
											boolean root ,
											@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String svcNamePort )
		throws IOException {

		var deleteReport = jacksonMapper.createObjectNode( ) ;

		logger.info( CSAP.buildDescription( "file delete",
				"fromFolder", fromFolder,
				"recursive", recursive,
				"root", root ) ) ;

		var workingItem = csapApis.application( ).getRequestedFile( fromFolder, svcNamePort, false ) ;

		if ( workingItem.getAbsolutePath( ).startsWith( csapApis.application( ).getDefinitionFolder( )
				.getAbsolutePath( ) ) ) {

			csapApis.application( ).getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		var message = "Deleting: " + workingItem.getAbsolutePath( ) ;

		try {

			csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/delete",
					CsapUser.currentUsersID( ),
					csapApis.events( ).fileName( workingItem, 60 ),
					workingItem.getAbsolutePath( ) ) ;

			if ( root ) {

				var recursiveOption = "" ;

				if ( recursive ) {

					recursiveOption = " --recursive " ;

				}

				List<String> lines = List.of(
						"\\rm --verbose --force " + recursiveOption + workingItem.getAbsolutePath( ) ) ;

				String scriptOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "filesystem-remove", lines ) ;

				message = "command: " + lines + "\n output:" + scriptOutput ;

			} else {

				if ( recursive ) {

					FileUtils.forceDelete( workingItem ) ;
					message += " (recursive)" ;

				} else {

					if ( workingItem.delete( ) ) {

						message += "  - SUCCESS" ;

					} else {

						message += "  - FAILED. Verify: csap user has r/w, folders are empty(or recurse=true). Alternately - use delete++" ;

					}

				}

			}

		} catch ( Exception e ) {

			var errMessage = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed deleting items: {}", errMessage ) ;

			message += " - FAILED " + "\n" + errMessage ;

		}

		logger.info( message ) ;

		deleteReport.put( "plain-text", message ) ;

		return deleteReport ;

	}

	@PostMapping ( FILE_SYSTEM_URL )
	@ResponseBody
	public ObjectNode filesystem_create (
											String fromFolder ,
											String newFolder ,
											String newFile ,
											boolean root ,
											@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String svcNamePort )
		throws IOException {

		var createReport = jacksonMapper.createObjectNode( ) ;

		logger.info( CSAP.buildDescription( "file create",
				"fromFolder", fromFolder,
				"newFolder", newFolder,
				"newFile", newFile,
				"root", root ) ) ;

		var workingFolder = csapApis.application( ).getRequestedFile( fromFolder, svcNamePort, false ) ;

		if ( workingFolder.getAbsolutePath( ).startsWith( csapApis.application( ).getDefinitionFolder( )
				.getAbsolutePath( ) ) ) {

			csapApis.application( ).getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		var originalName = workingFolder.getName( ) ;

		var message = "Creating: " ;

		csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/add", CsapUser.currentUsersID( ),
				"New Folder: " + newFolder + " file: " + newFile + " in " + workingFolder.getName( ),
				workingFolder.getAbsolutePath( ) ) ;

		try {

			if ( StringUtils.isNotEmpty( newFolder ) ) {

				workingFolder = new File( workingFolder, newFolder ) ;

				if ( root ) {

					var lines = List.of(
							"mkdir --parents --verbose " + workingFolder.getAbsolutePath( ) ) ;

					String scriptOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "filesystem-newfolder", lines ) ;
					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					message += "folder: " + newFolder + " " ;
					workingFolder.mkdirs( ) ;

				}

			}

			if ( StringUtils.isNotEmpty( newFile ) ) {

				File createdFile = new File( workingFolder, newFile ) ;

				if ( root ) {

					var lines = List.of(
							"touch " + createdFile.getAbsolutePath( ) ) ;

					String scriptOutput = fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "filesystem-new-file", lines ) ;
					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					message += "file: " + newFile + " " ;
					createdFile.createNewFile( ) ;

				}

			}

			message += "in: " + originalName ;

		} catch ( Exception e ) {

			var errMessage = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed creating items: {}", errMessage ) ;

			message = "Failed creating Items: Folders: " + newFolder + " files: " + newFile + "\n" + errMessage ;

		}

		createReport.put( "plain-text", message ) ;

		return createReport ;

	}

	@PostMapping ( "/update" )
	@ResponseBody
	public ObjectNode update (
								ModelMap modelMap ,
								boolean keepPermissions ,
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
								@RequestParam ( value = "chownUserid" , required = false , defaultValue = "csap" ) String chownUserid ,
								@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String svcNamePort ,
								@RequestParam ( value = "contents" , required = false , defaultValue = "" ) String contents ,
								HttpServletRequest request ,
								HttpServletResponse response ,
								HttpSession session )
		throws IOException {

		if ( StringUtils.isEmpty( fromFolder ) ) {

			logger.warn( "Missing or empty file for fromFolder --" ) ;

			throw new ResponseStatusException( HttpStatus.BAD_REQUEST,
					"Missing or empty file specified. This can occur if file being updated is too large" ) ;

////			response.setStatus( HttpStatus.INTERNAL_SERVER_ERROR.value( ) ) ;
//			response.setStatus( HttpStatus.BAD_REQUEST.value( ) ) ;
////			response.getWriter( )
////					.print( HttpStatus.INTERNAL_SERVER_ERROR.value( )
////							+ " : Exception during processing, examine server Logs" ) ;
////			return null ;
//			var error = jacksonMapper.createObjectNode( ) ;
//			error.put( "message", "Missing or empty file specified. This can occur if file being updated is too large --" ) ;
		} else {

			var contentIgnoredOnSave = editFile( modelMap, keepPermissions, fromFolder, chownUserid, svcNamePort,
					contents, request, session ) ;

		}

		return (ObjectNode) modelMap.get( "result" ) ;

	}

	@RequestMapping ( "/markdown/preview")
	@ResponseBody
	public String markdownPreview (
			String fromFolder ,
			String contents
	)  {

		return fileApiUtils.convertMarkdownToHtml( contents, "filePreview" ) ;

	}

	@RequestMapping ( {
			EDIT_URL, SAVE_URL
	} )
	public String editFile (
								ModelMap modelMap ,
								boolean keepPermissions ,
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
								@RequestParam ( value = "chownUserid" , required = false , defaultValue = "csap" ) String chownUserid ,
								@RequestParam ( value = CsapConstants.SERVICE_PORT_PARAM , required = false ) String svcNamePort ,
								@RequestParam ( value = "contents" , required = false , defaultValue = "" ) String contents ,
								HttpServletRequest request ,
								HttpSession session )
		throws IOException {

		modelMap.addAttribute( "serviceName", svcNamePort ) ;
		modelMap.addAttribute( "fromFolder", fromFolder ) ;

		if ( StringUtils.isEmpty( svcNamePort ) // incontext editing
				|| ( svcNamePort != null
						&& svcNamePort.equals( "null" ) ) ) {

			svcNamePort = null ;

		}

		File targetFile = csapApis.application( ).getRequestedFile( fromFolder, svcNamePort, false ) ;

		fileApiUtils.setCommonAttributes( modelMap, request, null, null ) ;

		fileApiUtils.auditTrail( targetFile, "edit" ) ;
		logger.info( "fromFolder: {} \n\t targetFile: {} ", fromFolder, targetFile ) ;

		modelMap.addAttribute( "targetFile", targetFile ) ;

		if ( isDockerFolder( fromFolder ) ) {

			String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
			String[] dockerContainerAndPath = fileApiUtils.splitDockerTarget( dockerTarget ) ;

			if ( StringUtils.isEmpty( contents ) ) {

				if ( csapApis.isCrioInstalledAndActive( )
						&& dockerTarget.contains( OsManager.CRIO_DELIMETER ) ) {

					contents = "Skipping save: CRIO file updates will be implement in final release" ;

				} else {

					contents = csapApis.containerIntegration( ).writeContainerFileToString(
							modelMap,
							dockerContainerAndPath[0],
							dockerContainerAndPath[1],
							getMaxEditSize( ),
							0,
							FileApiUtils.CHUNK_SIZE_PER_REQUEST ).toString( ) ;

				}

			} else {

				var dockerWriteResults = jacksonMapper.createObjectNode( ) ;

				if ( csapApis.isCrioInstalledAndActive( )
						&& dockerTarget.contains( OsManager.CRIO_DELIMETER ) ) {

					dockerWriteResults.put( "error",
							"Skipping save: CRIO file updates will be implement in final release " ) ;

				} else {

					dockerWriteResults = csapApis.containerIntegration( ).writeFileToContainer(
							contents,
							dockerContainerAndPath[0],
							dockerContainerAndPath[1],
							getMaxEditSize( ),
							FileApiUtils.CHUNK_SIZE_PER_REQUEST ) ;

				}

				logger.info( "dockerWriteResults {}", dockerWriteResults ) ;

				modelMap.addAttribute( "result", dockerWriteResults ) ;

			}

		} else if ( ! keepPermissions && // support nfs mount updates via cat by root
				( targetFile == null || ! targetFile.exists( ) ) ) {

			logger.warn( "Request file system does not exist: {}.  Check if {}  is bypassing security: ",
					targetFile.getCanonicalPath( ), CsapUser.currentUsersID( ) ) ;
			contents = "Invalid path: " + fromFolder ;

		} else {

			logger.info( "targetFile: {}, length: {},  contents length: {}", targetFile, targetFile.length( ),
					contents.length( ) ) ;

			// Only allow infra admin to view security files.
			if ( ! csapApis.security( ).getRoles( )
					.getAndStoreUserRoles( session, null )
					.contains( CsapSecurityRoles.INFRA_ROLE )
					&& ( fileApiUtils.isInfraOnlyFile( targetFile ) ) ) {

				logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security",
						fromFolder, CsapUser.currentUsersID( ) ) ;
				contents = "*** Content masked: can be accessed by infra admins: " + fromFolder ;

			} else if ( StringUtils.isEmpty( contents ) ) {

				if ( targetFile.length( ) > getMaxEditSize( ) ) {

					contents = "Error: selected file has size " + targetFile.length( ) / 1024
							+ " kb; it exceeds the max allowed of: " + getMaxEditSize( ) / 1024
							+ "kb\n CSAP download can be used to edit or view on desktop, then CSAP upload can be used." ;

				} else {

					if ( ! targetFile.canRead( ) ) {

						fileApiUtils.addUserReadPermissions( targetFile ) ;
						modelMap.addAttribute( "rootFile", "found" ) ;

					}

					contents = FileUtils.readFileToString( targetFile ) ;

					// } else {
					// File tempFile = createCsapUserReadableFile( targetFile );
					// contents = FileUtils.readFileToString( tempFile );
					// tempFile.delete();
					// modelMap.addAttribute( "rootFile", "found" );
					// }
				}

				csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/editor/load",
						CsapUser.currentUsersID( ),
						csapApis.events( ).fileName( targetFile, 100 ),
						targetFile.getAbsolutePath( ) ) ;

			} else {

				csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/editor/save",
						CsapUser.currentUsersID( ),
						csapApis.events( ).fileName( targetFile, 100 ),
						targetFile.getAbsolutePath( ) ) ;

				saveUpdatedFile( modelMap, chownUserid, svcNamePort, contents, targetFile, keepPermissions ) ;

				//
				// Avoid auto reloading - since context is a application being edited
				//
				logger.debug( "targetFile: {}, definition folder: {}",
						targetFile.getAbsolutePath( ),
						csapApis.application( ).getDefinitionFolder( ).getAbsolutePath( ) ) ;

				if ( targetFile.getAbsolutePath( ).startsWith( csapApis.application( ).getDefinitionFolder( )
						.getAbsolutePath( ) ) ) {

					csapApis.application( ).getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;
					// subsequent scan will trigger restart.
					csapApis.application( ).resetTimeStampsToBypassReload( ) ;

				}

			}

		}

		modelMap.addAttribute( "contents", contents ) ;

		return CsapConstants.FILE_URL + "/file-edit" ;

	}

	

	private void saveUpdatedFile (
									ModelMap modelMap ,
									String chownUserid ,
									String serviceName_port ,
									String contents ,
									File targetFile ,
									boolean keepPermissions )
		throws IOException {

		// Updated file provided
		StringBuilder results = new StringBuilder( "Updating File: " ) ;
		results.append( targetFile.getAbsolutePath( ) ) ;

		results.append( "\n\n" ) ;

		fileApiUtils.auditTrail( targetFile, "save" ) ;

		// csapApis.events().publishUserEvent( CsapEvents.CSAP_OS_CATEGORY +
		// "/edit/save",
		// CsapUser.currentUsersID(),
		// csapApis.events().fileName( targetFile, 100 ),
		// targetFile.getAbsolutePath() ) ;

		// handle k8 edits when service stopped

		if ( StringUtils.isNotEmpty( serviceName_port ) ) {

			var service = csapApis.application( ).getServiceInstanceCurrentHost( serviceName_port ) ;
			var desc = serviceName_port + "/edit" ;

			if ( service != null ) {

				desc = service.getName( ) + "/edit" ;

			}

			csapApis.events( ).publishUserEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + desc, CsapUser
					.currentUsersID( ),
					csapApis.events( ).fileName( targetFile, 100 ),
					targetFile.getAbsolutePath( ) ) ;

		}

		if ( Application.isRunningOnDesktop( ) ) {

			File backupFile = new File( targetFile.getAbsolutePath( ) + "."
					+ CsapUser.currentUsersID( ) + "."
					+ System.currentTimeMillis( ) ) ;

			logger.warn( CsapApplication.testHeader( "desktop detectect - file is being copied: {}" ), backupFile ) ;

			if ( ! backupFile.exists( ) ) {

				targetFile.renameTo( backupFile ) ;

			}

			try ( var fstream = new FileWriter( targetFile );
					var out = new BufferedWriter( fstream ); ) {
				// Create file

				out.write( contents ) ;

			}

		}

		File tempFolder = csapApis.application( ).csapPlatformTemp( ) ;

		if ( ! tempFolder.exists( ) ) {

			tempFolder.mkdirs( ) ;

		}

		File tempLocation = new File( tempFolder, "_" + targetFile.getName( ) ) ;

		try ( FileWriter fstream = new FileWriter( tempLocation );
				BufferedWriter out = new BufferedWriter( fstream ); ) {
			// Create file

			out.write( contents ) ;

		}

		var scriptPath = csapApis.application( ).csapPlatformPath( "/bin/csap-edit-file.sh" ) ;

		var backup_and_move_script = List.of(
				"#!/bin/bash",
				scriptPath.getAbsolutePath( )
						+ " " + FileApiUtils.pathWithSpacesEscaped( csapApis.application( ).getCsapInstallFolder( ) )
						+ " " + FileApiUtils.pathWithSpacesEscaped( tempLocation )
						+ " " + FileApiUtils.pathWithSpacesEscaped( targetFile )
						+ " " + chownUserid
						+ " " + CsapUser.currentUsersID( )
						+ " " + keepPermissions,
				"" ) ;

		results.append( fileApiUtils.getOsCommandRunner( ).runUsingRootUser( "backup-and-save-file", backup_and_move_script ) ) ;

		logger.debug( "result: {}", results ) ;

		ObjectNode resultObj = jacksonMapper.createObjectNode( ) ;
		resultObj.put( "plain-text", results.toString( ) ) ;
		modelMap.addAttribute( "result", resultObj ) ;

	}

	public static long getMaxEditSize ( ) {

		// allow additional for other parameters
		return CsapWebServerConfig.getMaxPostInBytes( ) - 500 ;

	}

}
