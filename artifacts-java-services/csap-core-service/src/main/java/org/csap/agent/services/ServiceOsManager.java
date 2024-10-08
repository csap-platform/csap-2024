package org.csap.agent.services;

import static java.util.Comparator.comparing ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.io.StringWriter ;
import java.net.URI ;
import java.net.URISyntaxException ;
import java.nio.charset.Charset ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.HashSet ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.NoSuchElementException ;
import java.util.Optional ;
import java.util.Set ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import jakarta.inject.Inject ;
import jakarta.servlet.http.HttpServletRequest ;
import jakarta.servlet.http.HttpSession ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.HttpdIntegration ;
import org.csap.agent.integrations.VersionControl ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.linux.ServiceJobRunner ;
import org.csap.agent.linux.TransferManager ;
import org.csap.agent.model.*;
import org.csap.agent.ui.editor.ServiceResources ;
import org.csap.agent.ui.rest.ApplicationBrowser ;
import org.csap.agent.ui.rest.ServiceRequests ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapWebSettings ;
import org.csap.security.CsapSecurityRestFilter ;
import org.csap.security.SpringAuthCachingFilter ;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.event.ContextRefreshedEvent ;
import org.springframework.context.event.EventListener ;
import org.springframework.core.annotation.Order ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.stereotype.Service ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.util.UriComponentsBuilder ;
import org.springframework.web.util.WebUtils ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Service
public class ServiceOsManager {

	private static final String CSAP_EVENT = "csapEvent" ;

	private static final String CSAP_JOB = "csapJob" ;

	final Logger logger = LoggerFactory.getLogger( ServiceOsManager.class ) ;

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	ObjectMapper jsonMapper ;
	VersionControl sourceControlManager ;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 2, "SvcMgr" ) ;

	ServiceDeployExecutor serviceDeployExecutor ;

	CsapApis csapApis ;

	@Autowired
	public ServiceOsManager (
			CsapApis csapApis,
			VersionControl sourceControlManager,
			ObjectMapper jsonMapper ) {

		this.jsonMapper = jsonMapper ;
		this.csapApis = csapApis ;
		this.sourceControlManager = sourceControlManager ;
		serviceDeployExecutor = new ServiceDeployExecutor(
				jsonMapper,
				csapApis.application( ).getCsapWorkingSubFolder( "_pause-all-deployments" ),
				CSAP_AGENT_AUTO_START_COMPLETE ) ;

	}

	// test only
	public ServiceOsManager ( CsapApis csapApis ) {

		// this.csapApis.application() = csapApis.application() ;
		this.jsonMapper = new ObjectMapper( ) ;
		this.csapApis = csapApis ;

		serviceDeployExecutor = new ServiceDeployExecutor(
				jsonMapper,
				csapApis.application( ).getCsapWorkingSubFolder( "_pause-all-deployments" ),
				CSAP_AGENT_AUTO_START_COMPLETE ) ;

	}

	public String getQueuedDeployments ( ) {

		return serviceDeployExecutor.pendingOperationsSummary( ) ;

	}

	public int getOpsQueued ( ) {

		return serviceDeployExecutor.getOpsQueued( ) ;

	}

	public ObjectNode cancelAllDeployments ( ) {

		logger.warn( "Emptying queue backlog" ) ;

		var status = jsonMapper.createObjectNode( ) ;

		status.put( "host", csapApis.application( ).getCsapHostName( ) ) ;

		status.set( "queue", serviceDeployExecutor.cancelRemaining( ) ) ;

		return status ;

	}

	public ObjectNode togglePauseOnDeployments ( ) {

		ObjectNode status = jsonMapper.createObjectNode( ) ;

		status.put( "host", csapApis.application( ).getCsapHostName( ) ) ;

		if ( serviceDeployExecutor.isPaused( ) ) {

			serviceDeployExecutor.resume( ) ;

		} else {

			serviceDeployExecutor.pause( ) ;

		}

		status.put( "isPaused", serviceDeployExecutor.isPaused( ) ) ;

		return status ;

	}

	public ObjectNode getJobStatus ( ) {

		ObjectNode status = jsonMapper.createObjectNode( ) ;

		status.put( "host", csapApis.application( ).getCsapHostName( ) ) ;

		status.put( "isPaused", serviceDeployExecutor.isPaused( ) ) ;

		status.set( "queue", serviceDeployExecutor.pendingOperations( ) ) ;

		return status ;

	}

	private static final String CSAP_REFERENCE_NOT_FOUND = "csap_reference_not_found" ;

	private static final String TRUE_STRING = "true" ;

	private static final String PACKAGE_SYNC = "package-transfer-id" ;

	public static final String BUILD_SUCCESS = "BUILD__SUCCESS" ;
	public final static String START_CLEAN = "startClean" ;
	public final static String START_NO_DEPLOY_PARAM = "noDeploy" ;
	public final static String SKIP_HEADERS = "skipHeaders" ;
	public static final String MAVEN_DEFAULT_BUILD = "default" ;

	// service deployment ops
	static String START_FILE = "csap-start.sh" ;
	static public String KILL_SCRIPT = "csap-kill.sh" ;
	static String DEPLOY_SCRIPT = "csap-deploy.sh" ;
	static String STOP_SCRIPT = "csap-stop.sh" ;

	// deploy log file names
	final static String START_OP = "-start" ;
	final static String STOP_OP = "-stop" ;
	final static String KILL_OP = "-kill" ;
	final static public String DEPLOY_OP = "-deploy" ;

	static private String JOB_RUNNER = "csap-job-run.sh" ;

	private boolean isInit = false ;

	@EventListener ( {
			ContextRefreshedEvent.class
	} )
	@Order ( CsapConstants.CSAP_SERVICE_STATE_LOAD_ORDER )
	public void on_start_up ( ) {
		// public void onSpringContextRefreshedEvent(ContextRefreshedEvent
		// event) {
		
		logger.warn( CsapApplication.highlightHeader( "Service Loading Startup" ) );

		if ( csapApis.application( ).isAdminProfile( ) ) {

			logger.debug( "Skipping init scince we are in mgr mode" ) ;
			csapApis.application( ).setBootstrapComplete( ) ;
			return ;

		}

		if ( isInit ) {

			logger.warn( "agent already initialized, but received a second ContextRefreshedEvent from Spring" ) ;
			return ;

		}

		File workingDir = csapApis.application( ).getCsapInstallFolder( ) ;
		File rebuildPath = new File( workingDir, "/bin/" + DEPLOY_SCRIPT ) ;

		if ( ! rebuildPath.exists( ) ) {

			logger.warn( "did not find: {}. \n Ensure latest csap-package-linx is deployed.",
					rebuildPath.getAbsolutePath( ) ) ;

		}

		isInit = true ;

		if ( csapApis.application( ).isStatefulRestartNeeded( ) ) {

			logger.warn(
					"Found -Dorg.csap.needStatefulRestart=yes, triggering a restart so that cluster params can be loaded" ) ;

			restartAgent( ) ;

		} else {

			// write the credential file out for cli access
			File credFile = new File( csapApis.application( ).getAgentRunHome( ), ".csap-config" ) ;

			if ( credFile.isFile( ) ) {

				logger.info( "Deleting: {}", credFile ) ;
				FileUtils.deleteQuietly( credFile ) ;

			}

			if ( csapSecurityRestFilter != null ) {

				try {

					logger.info( "Creating: {}", credFile ) ;
					FileUtils.write( credFile, "userid=csapcli,pass=" + csapSecurityRestFilter.getToken( ) ) ;

				} catch ( IOException e ) {

					logger.warn( "Failed updating: {}, {}", credFile, CSAP.buildCsapStack( e ) ) ;

				}

			}

			var autoStartDisableFile = csapApis.application( ).getAutoStartDisabledFile( ) ;

			if ( autoStartDisableFile.exists( ) ) {

				logger.warn( CsapApplication.header( "Auto starts disabled: remove '{}' to enable" ),
						autoStartDisableFile.getAbsolutePath( ) ) ;

				csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/agent-start-up",
						"WARNING: Auto start disabled", "Remove file to re-enable: " + autoStartDisableFile
								.getAbsolutePath( ) ) ;

				csapApis.application( ).setBootstrapComplete( ) ;

			} else {

				service_auto_startup( ) ;

			}

		}

	}

	@Autowired ( required = false )
	CsapSecurityRestFilter csapSecurityRestFilter ;

	private void restartAgent ( ) {

		ArrayList<String> params = new ArrayList<String>( ) ;
		params.add( "-cleanType" ) ;
		params.add( "no" ) ;

		String results ;

		try {

			results = run_service_script( Application.SYS_USER, KILL_SCRIPT, CsapConstants.AGENT_ID,
					params, null, null ) ;
			logger.warn( "Results from restart command: \n {}", results ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to issue restart command", CSAP.buildCsapStack( e ) ) ;

		}

	}

	/**
	 *
	 *
	 */
	private void service_auto_startup ( ) {

		var startReport = new StringBuilder(  "Service start report:" ) ;

		startReport.append( "\n\n  Hosts: \n\t" ) ;

		int hostCount = 0 ;

		for ( String host : csapApis.application( ).getActiveProject( ).getHostsCurrentLc( ) ) {

			startReport.append( StringUtils.rightPad( host, 17 ) ) ;
			startReport.append( " " ) ;

			if ( ++hostCount % 7 == 0 ) {

				startReport.append( "\n\t" ) ;

			}

		}
		startReport.append( "\n\n  Services:" ) ;

		// refresh instances with updated service stats.
		csapApis.osManager( ).checkForProcessStatusUpdate( ) ;

		// possibly kicked off on another thread; waiting is needed to determine
		// container states
		csapApis.osManager( ).wait_for_initial_process_status_scan( 10 ) ;

		isSkippingContainerAutostarts = false ;
		String service_start_messages = csapApis.application( )
				.servicesOnHost( )
//				.sorted( comparing( ServiceInstance::startOrder ) )
				.sorted( ServiceBase.csapAutoStartComparing( ServiceInstance::startOrder ) )
				.map( this::schedule_startup_deployment )
				.collect( Collectors.joining( ) ) ;

		startReport.append( service_start_messages ) ;

		ServiceInstance initCompleteInstance = new ServiceInstance( ) ;
		initCompleteInstance.setName( CSAP_AGENT_AUTO_START_COMPLETE ) ;
		// Hook to trigger init complete
		submitDeployJob( Application.SYS_USER, initCompleteInstance, null, true, false ) ;

		logger.warn( startReport.toString( ) ) ;

		csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/initializeServiceState",
				"Service Synchronization", startReport.toString( ) ) ;

	}

	// whether or not to auto start container services (kubernetes or docker)
	boolean isSkippingContainerAutostarts = false ;

	private String schedule_startup_deployment ( ServiceInstance serviceInstance ) {

		var startupStatusMessage = new StringBuilder( ) ;

		startupStatusMessage.append( "\n\t" + serviceInstance.paddedId( ) ) ;
		// Never trigger restarts of CsAgent
		if ( serviceInstance.getName( ).equals( CsapConstants.AGENT_NAME )
				|| serviceInstance.is_os_process_monitor( ) ) {

			startupStatusMessage.append( "\t\t - ignoring process monitor" ) ;
			return startupStatusMessage.toString( ) ;

		}


		if ( serviceInstance.is_files_only_package( ) ) {

			// special case for packages - we will only trigger if they
			// do not exist in runtime dir
			// Scripts are meant to only be invoked 1 time. If someone
			// cleans the folder - script will need to
			// check fs if they cannot be run twice.
			File scriptWorkingDirectory = new File( csapApis.application( ).getCsapWorkingFolder( ), serviceInstance
					.getServiceName_Port( ) ) ;

			logger.debug( "Checking: {}", scriptWorkingDirectory.getAbsolutePath( ) ) ;

			// initMessage.append( "\n Script: " +
			// checkFile.getAbsolutePath() );

			if ( scriptWorkingDirectory.exists( ) ) {

				logger.debug(
						"Found autostart on a instance  with is_files_only_package, but since it exists, autoStart is ignored" ) ;

				startupStatusMessage.append( "\t\t - package already deployed" ) ;
				return startupStatusMessage.toString( ) ;

			}

		}

		if ( serviceInstance.is_cluster_kubernetes( ) ) {

			if ( serviceInstance.isKubernetesMaster( )
					&& serviceInstance.getKubernetesPrimaryMaster( ).contains( csapApis.application( )
							.getCsapHostName( ) ) ) {

				File deployFile = k8s_last_deploy_file( serviceInstance ) ;

				if ( deployFile.exists( ) ) {

					startupStatusMessage.append( "\t\t - kubernetes master, service already deployed" ) ;
					return startupStatusMessage.toString( ) ;

				}

			} else {

				startupStatusMessage.append( "\t\t - kubernetes worker, deployment occurs on master" ) ;
				return startupStatusMessage.toString( ) ;

			}

		}

		File stopFile = csapApis.application( ).getCsapWorkingSubFolder( serviceInstance.getStoppedFileName( ) ) ;

		serviceInstance.getWorkingDirectory( ) ;

		boolean needToStartSevice = serviceInstance.isAutoStart( )
				&& ! serviceInstance.getDefaultContainer( ).isRunning( )
				&& ! stopFile.exists( ) ;

		if ( serviceInstance.is_files_only_package( )
				&& serviceInstance.getWorkingDirectory( ).exists( ) ) {

			needToStartSevice = false ;

		}

		if ( isSkippingContainerAutostarts && serviceInstance.is_docker_server( ) ) {

			startupStatusMessage.append( "\t\t - skipping container service autostart" ) ;

		} else if ( needToStartSevice ) {

			logger.debug( "{} - Scheduling auto deploy on startup", serviceInstance.getName( ) ) ;

			serviceInstance.getDefaultContainer( ).setCpuAuto( ) ;

			if ( getReImageFile( ).exists( ) && serviceInstance.is_csap_api_server( )
					&& serviceInstance.getUser( ) != null && ! serviceInstance.getUser( ).equals( csapApis
							.application( )
							.getAgentRunUser( ) ) ) {

				serviceInstance.getDefaultContainer( ).setCpuClean( ) ;

			}

			MultiValueMap<String, String> rebuildVariables = new LinkedMultiValueMap<String, String>( ) ;

			rebuildVariables.add( "scmUserid", Application.SYS_USER ) ;
			rebuildVariables.add( "scmPass", "dummyPass" ) ;
			rebuildVariables.add( "scmBranch", "dummBranch" ) ;
			rebuildVariables.add( CsapConstants.SERVICE_PORT_PARAM, serviceInstance.getServiceName_Port( ) ) ;
			rebuildVariables.add( "mavenDeployArtifact", MAVEN_DEFAULT_BUILD
					+ ":dummyStringToSkipSvn" ) ;
			rebuildVariables.add( "scmCommand", null ) ;
			rebuildVariables.add( "targetScpHosts", "" ) ;
			rebuildVariables.add( "hotDeploy", null ) ;

			startupStatusMessage.append( "\t\t - added to Deployment Queue" ) ;
			submitDeployJob(
					Application.SYS_USER,
					serviceInstance, rebuildVariables,
					true, false ) ;

		} else {

			if ( serviceInstance.getName( ).toLowerCase( ).equals( C7.dockerService.val( ) ) ) {

				isSkippingContainerAutostarts = true ;

			}

			logger.debug( "{} is already running or manually stopped, skipping autodeploy",
					serviceInstance.getName( ) ) ;

			if ( ! serviceInstance.isAutoStart( ) ) {

				startupStatusMessage.append( "\t\t - autostart disabled in definition" ) ;

			} else if ( stopFile.exists( ) ) {

				startupStatusMessage.append( "\t\t - service stopped by operator " + stopFile.getAbsolutePath( ) ) ;

			} else {

				startupStatusMessage.append( "\t\t - service already running" ) ;

			}

		}

		return startupStatusMessage.toString( ) ;

	}

	private void create_k8s_last_deploy_file ( ServiceInstance instance ) {

		File f = k8s_last_deploy_file( instance ) ;

		if ( f.exists( ) ) {

			f.delete( ) ;

		}

		try {

			f.createNewFile( ) ;

		} catch ( IOException e ) {

			logger.info( "Failed creating deploy file {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	private File k8s_last_deploy_file ( ServiceInstance instance ) {

		// Used to ensure after restart - kubernetes services are not autodeployed
		return new File( csapApis.application( ).getCsapWorkingFolder( ), "/"
				+ instance.getServiceName_Port( ) + "-k8-skip-auto" ) ;

	}

	public String run_service_script (
										String userName ,
										String scriptName ,
										String serviceName_port ,
										List<String> paramsInput ,
										HttpSession session ,
										BufferedWriter outputWriter ) {

		ServiceInstance serviceInstance = csapApis.application( ).getServiceInstanceCurrentHost( serviceName_port ) ;

		if ( serviceInstance == null ) {

			return "Error: Instance: " + serviceName_port + " was not found on VM:"
					+ csapApis.application( ).getCsapHostName( ) ;

		}

		String results = "" ;

		if ( serviceInstance.is_docker_server( )
				&& scriptName.equals( STOP_SCRIPT ) ) {

			logger.info( "{} Invoking: '{}'", userName, scriptName ) ;
			synchronizeServiceState( scriptName, serviceInstance ) ;
			boolean isKill = true ;

			if ( scriptName.equals( STOP_SCRIPT ) ) {

				isKill = false ;

			}

			ObjectNode stopResults = dockerHelper.containerStop( null, serviceInstance.getDockerContainerPath( ),
					isKill, 3 ) ;
			results = CSAP.jsonPrint( stopResults ) ;

		} else {

			results = runShellCommand( userName, scriptName, serviceInstance,
					paramsInput, null, null,
					session, outputWriter ) ;

		}

		return results ;

	}

	public String runServiceJob (
									ServiceInstance serviceInstance ,
									String jobDescription ,
									String jobEnvVariableDefinition ,
									BufferedWriter outputWriter )
		throws Exception {

		OutputFileMgr outputFm = new OutputFileMgr(
				serviceInstance.getServiceJobsLogDirectory( ),
				jobLogFile( jobDescription, "-launch" ) ) ;

		BufferedWriter jobWriter = outputFm.getBufferedWriter( ) ;

		if ( outputWriter != null ) {

			jobWriter = outputWriter ;

		}

		logger.info( "service: {} job: '{}', \n\t output: {}",
				serviceInstance.getName( ),
				jobDescription,
				outputFm.getOutputFile( ).getAbsolutePath( ) ) ;

		ArrayList<String> jobParameters = new ArrayList<>( ) ;
		jobParameters.add( jobDescription ) ;

		Map<String, String> environmentVariables = new LinkedHashMap<>( ) ;

		if ( serviceInstance.is_cluster_kubernetes( ) && csapApis.application( ).environmentSettings( )
				.isVsphereConfigured( ) ) {

			var vsphereConfiguration = csapApis.application( ).environmentSettings( ).getVsphereConfiguration( ) ;
			var vsphereVariables = csapApis.application( ).environmentSettings( ).getVsphereEnv( ) ;

			logger.info( "Adding vsphere variables" ) ;
			environmentVariables.putAll( vsphereVariables ) ;
			environmentVariables.put( "vm_path", vsphereConfiguration.at( "/filters/vm-path" ).asText( "not-set" ) ) ;
			environmentVariables.put( "resource_pool_path", vsphereConfiguration.at( "/filters/resource-pool-path" )
					.asText( "not-set" ) ) ;

		}

		if ( StringUtils.isNotEmpty( jobEnvVariableDefinition ) ) {

			try {

				var paramMap = jsonMapper.readTree( jobEnvVariableDefinition ) ;
				CSAP.asStreamHandleNulls( (ObjectNode) paramMap )
						.forEach( attributeName -> {

							environmentVariables.put( attributeName, paramMap.path( attributeName ).asText(
									"job-param-not-found" ) ) ;

						} ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed building job parameters: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		String results = runShellCommand(
				CsapConstants.AGENT_NAME,
				ServiceOsManager.JOB_RUNNER,
				serviceInstance,
				jobParameters,
				environmentVariables,
				null,
				null, jobWriter ) ;

		if ( outputWriter != null ) {

			outputFm.print( results ) ;

		}

		outputFm.close( ) ;

		return results ;

	}

	@Inject
	CsapEncryptionConfiguration csapEncryptableProperties ;

	private String runShellCommand (
										String userName ,
										String scriptName ,
										ServiceInstance serviceInstance ,
										List<String> paramsInput ,
										Map<String, String> environmentVariablesInput ,
										String parameters_override ,
										HttpSession session ,
										BufferedWriter outputWriter ) {

		logger.debug( "{} , Invoking: '{}', parameters_override: {} , params: {}",
				serviceInstance.getServiceName_Port( ), scriptName, parameters_override, paramsInput ) ;

		String result = "" ;

		File workingDir = csapApis.application( ).getCsapInstallFolder( ) ;
		File scriptPath = new File( workingDir, "/bin/" + scriptName ) ;

		// String userName = getUserIdFromContext();
		List<String> parameters = new ArrayList<String>( ) ;
		parameters.add( "bash" ) ;
		// parmList.add("-c") ;
		parameters.add( scriptPath.getAbsolutePath( ) ) ;

		if ( serviceInstance.is_os_process_monitor( ) 
				&& ! scriptName.equals( JOB_RUNNER )) {

			return "Skipping " + scriptName + ", service is a csap-api runtime: " + serviceInstance
					.getServiceName_Port( ) ;

		}

		synchronizeServiceState( scriptName, serviceInstance ) ;

		// parameters.add( "-jmxAuth" ) ;
		// parameters.add( csapApis.application().getJmxAuth() ) ;

		// parmList.add( "-loadBalanceUrl" );
		// parmList.add( Application.getCurrentLifeCycleMetaData().getLbUrl() );
		String programParameters = serviceInstance.getParameters( ) ;

		if ( StringUtils.isNotEmpty( parameters_override )
				&& ! parameters_override.startsWith( "default" ) ) {

			programParameters = parameters_override ;

		}

		if ( serviceInstance.is_springboot_server( ) && serviceInstance.isTomcatAjp( ) ) {

			programParameters += " -Dserver.context-path=/" + serviceInstance.getContext( ) ;
			programParameters += " -Dserver.servlet.context-path=/" + serviceInstance.getContext( ) ;

		}

		programParameters = serviceInstance.resolveRuntimeVariables( programParameters ) ;

		parameters.add( "-csapDeployOp" ) ;

		// if ( serviceInstance.isTomcatPackaging() ) {
		// addTomcatParameters( serviceInstance, parameters ) ;
		//
		// // might be null for some containers
		// if ( serviceInstance.getContext().length() > 0 ) {
		// parameters.add( "-context" ) ;
		// parameters.add( serviceInstance.getContext() ) ;
		// }
		// }

		parameters.add( "-repo" ) ;
		parameters.add( serviceInstance.getMavenRepo( ) ) ;

		parameters.add( "-osProcessPriority" ) ;
		parameters.add( Integer.toString( serviceInstance.getOsProcessPriority( ) ) ) ;

		if ( serviceInstance.getMetaData( ).contains( HttpdIntegration.SKIP_INTERNAL_HTTP_TAG ) ) {

			parameters.add( "-" + HttpdIntegration.SKIP_INTERNAL_HTTP_TAG ) ;

		}

		if ( serviceInstance.getMetaData( ).contains( Application.SKIP_TOMCAT_JAR_SCAN ) ) {

			parameters.add( "-" + Application.SKIP_TOMCAT_JAR_SCAN ) ;

		}

		LinkedHashMap<String, String> serviceEnvironmentVariables = new LinkedHashMap<>( ) ;
		serviceEnvironmentVariables.put( "csapParams", programParameters ) ;
		// serviceEnvironmentVariables.put( "isJmxAuth", Boolean.toString(
		// csapApis.application().getCsapCoreService().isJmxSecure() ).toLowerCase() ) ;
		// serviceEnvironmentVariables.put( "jmxUser",
		// csapApis.application().getCsapCoreService().getJmxUser() ) ;
		// serviceEnvironmentVariables.put( "jmxPassword",
		// csapApis.application().getCsapCoreService().getJmxPass() ) ;

		if ( environmentVariablesInput != null ) {

			serviceEnvironmentVariables.putAll( environmentVariablesInput ) ;

		}

		if ( scriptName.equals( ServiceOsManager.JOB_RUNNER ) ) {

			configureServiceJob( paramsInput, serviceInstance, serviceEnvironmentVariables ) ;

		} else {

			// add all the params
			parameters.addAll( paramsInput ) ;

		}

		if ( ! scriptPath.exists( ) && ! Application.isRunningOnDesktop( ) ) {

			result = "Failed to find path: " + scriptPath + " for :\n" + parameters.toString( ) ;
			logger.error( result ) ;
			csapApis.events( ).publishEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + serviceInstance.getName( ),
					result, "No more\nDetails",
					new NoSuchElementException( scriptPath.getAbsolutePath( ) ) ) ;
			return result ;

		}

		if ( ! scriptPath.canExecute( ) && ! Application.isRunningOnDesktop( ) ) {

			result = "Not able to execute, check permissions: " + scriptPath.getAbsolutePath( ) ;
			logger.error( result ) ;

		}

		var timer = csapApis.metrics( ).startTimer( ) ;

		try {

			addCsapModelEnvVariables( serviceInstance, serviceEnvironmentVariables ) ;

			addServiceEnvironment( serviceInstance, serviceEnvironmentVariables, true, outputWriter ) ;

			StringBuilder description = new StringBuilder( ) ;

			var summary = "Script: " + scriptName + ", triggered by: " + userName ;

			if ( serviceEnvironmentVariables.containsKey( CSAP_EVENT ) ) {

				summary = "Event: " + serviceEnvironmentVariables.get( CSAP_EVENT )
						+ " Job: " + serviceEnvironmentVariables.get( CSAP_JOB ) ;

				description.append( "\n" + CSAP.padLine( "Event" ) + serviceEnvironmentVariables.get( CSAP_EVENT ) ) ;
				description.append( "\n" + CSAP.padLine( "Job" ) + serviceEnvironmentVariables.get( CSAP_JOB ) ) ;

			}

			description.append( "\n" + CSAP.padLine( "Script" ) + scriptName ) ;
			description.append( "\n" + CSAP.padLine( "Time Out" ) + serviceInstance.getDeployTimeOutSeconds( ) ) ;
			description.append( "\n" + CSAP.padLine( "Working Dir" ) + workingDir ) ;
			description.append( "\n" + CSAP.padLine( "Parameters" ) + parameters.toString( ) ) ;

			if ( serviceEnvironmentVariables.containsKey( ENVIRONMENT_CUSTOM_VALS ) ) {

				description
						.append( "\n" + CSAP.padLine( "Custom Environment" ) + serviceEnvironmentVariables.get(
								ENVIRONMENT_CUSTOM_VALS ) ) ;

			}

			description.append( "\n" ) ;

			csapApis.events( ).publishEvent(
					CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + serviceInstance.getName( ),
					summary,
					description.toString( ) ) ;

			result = osCommandRunner.executeString(
					parameters,
					serviceEnvironmentVariables,
					workingDir, null, session,
					serviceInstance.getDeployTimeOutSeconds( ),
					1,
					outputWriter ) ;

		} catch ( Exception e ) {

			logger.error( "{} Failed to set environment variables for service {}",
					serviceInstance.getName( ), CSAP.buildCsapStack( e ) ) ;

		}

		csapApis.metrics( ).stopTimer( timer, "agent.deploy." + serviceInstance.getName( ) ) ;

		logger.debug( "{} results from {} are {}", serviceInstance.getName( ), scriptName, result ) ;

		if ( result.length( ) > 600000 ) {

			int origLength = result.length( ) ;
			result = "\n\n ======= Only last 600k of command output shown as it was " + origLength
					+ " characters. View full output in runtime folder."
					+ result.substring( result.length( ) - 600000 ) ;

		}

		csapApis.application( ).setLastOp( System.currentTimeMillis( ) + "::" + userName + " invoked: " + scriptName
				+ " on: "
				+ serviceInstance.getServiceName_Port( ) + " time: " + new Date( ) ) ;

		if ( ! csapApis.application( ).isStatefulRestartNeeded( ) ) {

			// finally refresh processes and versions
			serviceInstance.setFileSystemScanRequired( true ) ;

			// check for application updates
			csapApis.application( ).run_application_scan( ) ;

			// updated os caches
			csapApis.osManager( ).resetAllCaches( ) ;

		}

		return result ;

	}

	private boolean printEmailWarningOnce = true ;

	private void addCsapModelEnvVariables (
											ServiceInstance serviceInstance ,
											Map<String, String> serviceEnvironmentVariables ) {

		ObjectNode notifications = serviceInstance.getAttributeAsObject( ServiceAttributes.notifications ) ;

		if ( notifications != null ) {

			if ( notifications.has( "csapAddresses" ) ) {

				String emailAddresses = notifications.get( "csapAddresses" ).asText( ) ;

				if ( emailAddresses.contains( "someUser" ) ) {

					logger.warn( "Default 'someUser' found in emailAddress list, not enabling notifications: {}",
							emailAddresses ) ;

				} else {

					serviceEnvironmentVariables.put( "csapAddresses", emailAddresses ) ;

					if ( notifications.has( "csapFrequency" ) ) {

						serviceEnvironmentVariables.put( "csapFrequency", notifications.get( "csapFrequency" )
								.asText( ) ) ;

					}

					if ( notifications.has( "csapTimeUnit" ) ) {

						serviceEnvironmentVariables.put( "csapTimeUnit", notifications.get( "csapTimeUnit" )
								.asText( ) ) ;

					}

					if ( notifications.has( "csapMaxBacklog" ) ) {

						serviceEnvironmentVariables.put( "csapMaxBacklog", notifications.get( "csapMaxBacklog" )
								.asText( ) ) ;

					}

				}

			}

		}
		if ( csapApis.application().isMacOsProfileActive() ) {
			serviceEnvironmentVariables.put( CsapApplication.CSAP_INSTALL_VARIABLE,
					csapApis.application().getInstallationFolderAsString() );
		}

		if ( printEmailWarningOnce && ! serviceEnvironmentVariables.containsKey( "csapAddresses" ) ) {

			printEmailWarningOnce = false ;
			logger.warn( "Notifications are disabled. Update Application.json as needed" ) ;
			serviceEnvironmentVariables.put( "csapAddresses", "disabled" ) ;

		}

		// Clustering infor
		try {

			Project activeModel = csapApis.application( ).getActiveProject( ) ;

			List<String> serviceHostList = activeModel.findOtherHostsForService( serviceInstance.getName( ) ) ;
			StringBuilder hostsForBash = new StringBuilder( ) ;
			serviceHostList.stream( ).forEach( ( host ) -> {

				if ( hostsForBash.length( ) > 0 ) {

					hostsForBash.append( " " ) ;

				}

				hostsForBash.append( host ) ;

			} ) ;
			serviceEnvironmentVariables.put( "csapPeers", hostsForBash.toString( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to set peers for service {}", serviceInstance.getName( ), e ) ;

		}

		// String version = serviceInstance.getMavenVersion();
		String version = serviceInstance.getScmVersion( ) ;

		// logger.info("version is: {}" , version);
		if ( version.length( ) != 0 && version.contains( " " ) ) {

			version = version.substring( 0, version.indexOf( " " ) ) ;

		}

		// logger.info("version is: {}" , version);
		if ( version.length( ) == 0 ) {

			version = serviceInstance.getMavenVersion( ) ;

		}

		// logger.info("version is: {}" , version);
		serviceEnvironmentVariables.put( "csapVersion", version ) ;

		serviceEnvironmentVariables.put( "csapServer", serviceInstance.getProcessRuntime( ).getId( ) ) ;

		if ( serviceInstance.getClusterType( ) == ClusterType.KUBERNETES_PROVIDER ) {

			serviceEnvironmentVariables.put( "kubernetesMasterDns", serviceInstance.getKubernetesMasterDns( ) ) ;

			try {

				String kubernetesMasters = CSAP.jsonStream( serviceInstance.getKubernetesMasterHostNames( ) )
						.map( JsonNode::asText )
						.collect( Collectors.joining( " " ) ) ;

				serviceEnvironmentVariables.put( "kubernetesMasters", kubernetesMasters ) ;
				// backwards compatible
				serviceEnvironmentVariables.put( "kubernetesMaster", kubernetesMasters ) ;

			} catch ( Exception e ) {

				logger.error( "Failed setting kubernetesMasters variable: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		if ( serviceInstance.isTomcatPackaging( ) ) {

			serviceEnvironmentVariables.put( "csapTomcat", TRUE_STRING ) ;

		}

		if ( serviceInstance.is_cluster_single_host_modjk( ) ) {

			serviceEnvironmentVariables.put( "csapHttpPerHost", TRUE_STRING ) ;

		}

		serviceEnvironmentVariables.put( "csapName", serviceInstance.getName( ) ) ;
		
		if ( serviceInstance.hasProcessGroup( ) ) {
			serviceEnvironmentVariables.put( "csapProcessGroup", serviceInstance.getProcessGroup( ) ) ;
		}

		if ( serviceInstance.getKubernetesNamespace( ) != null ) {

			serviceEnvironmentVariables.put( "csapNameSpace", serviceInstance.getKubernetesNamespace( ) ) ;

		}

		serviceEnvironmentVariables.put( "csapPids", serviceInstance.getDefaultContainer( ).getPidsAsString( ) ) ;
		serviceEnvironmentVariables.put( CsapWebSettings.DEFAULT_AJP_VARIABLE_IN_YAML, csapApis.application( )
				.getTomcatAjpKey( ) ) ;
		serviceEnvironmentVariables.put( "csapHost", csapApis.application( ).getCsapHostName( ) ) ;
		serviceEnvironmentVariables.put( "csapWorkingDir", serviceInstance.getWorkingDirectory( ).getAbsolutePath( ) ) ;
		
		

		var deployLogFolder = csapApis.application( ).getWorkingLogDir( serviceInstance.getServiceName_Port( ) ) ;

		if ( deployLogFolder != null ) {
			
			var deployLogPath =  deployLogFolder.getAbsolutePath( ) ;

			serviceEnvironmentVariables.put( "csapLogDir", deployLogPath ) ;

			serviceEnvironmentVariables.put( "appLogDir", serviceInstance.getLogDirectory( ) ) ;

		} else {

			logger.warn( "unable to determine log dir: {}", serviceInstance.getName( ) ) ;

		}

		serviceEnvironmentVariables.put( "csapHttpPort", serviceInstance.getPort( ) ) ;
		serviceEnvironmentVariables.put( "csapPrimaryPort", serviceInstance.getPort( ) ) ;
		serviceEnvironmentVariables.put( "csapJmxPort", serviceInstance.getJmxPort( ) ) ;
		serviceEnvironmentVariables.put( "csapServiceLife", serviceInstance.getLifecycle( ) ) ;

		var firstAdmin = csapApis.application( ).findFirstServiceInstanceInLifecycle( CsapConstants.ADMIN_NAME ) ;

		if ( firstAdmin != null ) {

			var cliUrl = "http://"
					+ csapApis.application( ).getHostUsingFqdn( firstAdmin.getHostName( ) )
					+ ":" + firstAdmin.getPort( ) + "/"
					+ firstAdmin.getContext( ) ;

			serviceEnvironmentVariables.put( "csapAdminUrl", cliUrl ) ;

		}

		if ( serviceInstance.is_cluster_kubernetes( ) ) {

			serviceEnvironmentVariables.put( "csapReplicaCount", serviceInstance.getKubernetesReplicaCount( ).asText(
					"1" ) ) ;

		}

		if ( serviceInstance.is_docker_server( ) ) {

			serviceEnvironmentVariables.put( "csapArtifact", serviceInstance.getDockerImageName( ) ) ;

		} else if ( StringUtils.isNotEmpty( serviceInstance.getMavenId( ) ) ) {

			serviceEnvironmentVariables.put( "csapArtifact", serviceInstance.getMavenId( ) ) ;

		}

		// add infra settings

		serviceEnvironmentVariables.put( "hostUrlPattern", csapApis.application( ).getAgentHostUrlPattern( true ) ) ;

		if ( csapApis.application( ).isCompanyVariableConfigured( "spring.mail.host" ) ) {

			serviceEnvironmentVariables.put( "mailServer", csapApis.application( ).getCompanyConfiguration(
					"spring.mail.host", "" ) ) ;

		}

		if ( csapApis.application( ).isCompanyVariableConfigured( "spring.mail.port" ) ) {

			serviceEnvironmentVariables.put( "mailPort", csapApis.application( ).getCompanyConfiguration(
					"spring.mail.port", "" ) ) ;

		}

		// add yaml replacements
		var count = 1 ;

		for ( var set : csapApis.application( ).environmentSettings( ).getKubernetesYamlReplacements( ).entrySet( ) ) {

			serviceEnvironmentVariables.put( "yamlCurrent" + count, set.getKey( ) ) ;
			serviceEnvironmentVariables.put( "yamlNew" + count, set.getValue( ) ) ;

			count++ ;

		}

	}

	private void configureServiceJob (
										List<String> commandParameters ,
										ServiceInstance serviceInstance ,
										Map<String, String> environmentVariables ) {

		logger.debug( "commandParameters: {}, \n\t environmentVariables: {}", commandParameters,
				environmentVariables ) ;

		try {

			if ( commandParameters.size( ) == 1 ) {

				String targetJob = commandParameters.get( 0 ) ;

				serviceInstance.getJobs( ).forEach( job -> {

					if ( job.getDescription( ).equals( targetJob ) ) {

						try {

							String jobCommand = csapApis.application( ).resolveDefinitionVariables( job.getScript( ),
									serviceInstance ) ;
							// String jobCommand = serviceInstance.resolveRuntimeVariables( job.getScript()
							// ) ;
							// logger.info( "raw jobPath: {}", jobPath );
							// Parsing handled during service load
							environmentVariables.put( CSAP_JOB, jobCommand ) ;
							environmentVariables.put( CSAP_EVENT, job.getFrequency( ) ) ;
							environmentVariables.put( "csapJobBackground", Boolean.toString( job
									.isRunInBackground( ) ) ) ;
							environmentVariables.put( "outputFile", jobLogFile( job.getDescription( ), "-output" ) ) ;
							logger.debug( "Invoking job: {}, output: {}", jobCommand ) ;

							// variableNameList.add( "csapJob" );
						} catch ( Exception ex ) {

							logger.error( "{} Failed to parse: {}", serviceInstance.getServiceName_Port( ),
									serviceInstance.getJobs( ), ex ) ;

						}

					}

				} ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to configure job", e ) ;

		}

	}

	public String jobLogFile ( String desc , String notes ) {

		return CSAP.camelToSnake( CSAP.alphaNumericOnly( desc ) )
				+ "-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm" ) )
				+ notes
				+ ".log" ;

	}

	public static final String ENVIRONMENT_CUSTOM_VALS = "customAttributes" ;

	/**
	 * Stateful filesystem related activity.
	 *
	 * @param scriptName
	 * @param serviceInstance
	 */
	private void synchronizeServiceState ( String scriptName , ServiceInstance serviceInstance ) {

		logger.debug( "{} , script: {}, autoKill: {}", serviceInstance.getName( ), scriptName,
				serviceInstance.getDefaultContainer( ).isAutoKillInProgress( ) ) ;

		if ( serviceInstance.getDefaultContainer( ).isAutoKillInProgress( ) ) {

			// reset the flag in case admin trys to kill;
			serviceInstance.getDefaultContainer( ).setAutoKillInProgress( false ) ;
			return ;

		}

		try {

			if ( scriptName.equals( KILL_SCRIPT ) || scriptName.equals( STOP_SCRIPT ) ) {

				// Make sure everything is update to date - include checking the
				// version in start file
				csapApis.application( ).run_application_scan( ) ;
				File stopFile = csapApis.application( ).getCsapWorkingSubFolder( serviceInstance
						.getStoppedFileName( ) ) ;

				if ( ! stopFile.exists( ) ) {

					stopFile.createNewFile( ) ;

				}

			} else if ( scriptName.equals( START_FILE ) ) {

				// Make sure everything is update to date - include checking the
				// version in start file
				csapApis.application( ).run_application_scan( ) ;
				File stopFile = csapApis.application( ).getCsapWorkingSubFolder( serviceInstance
						.getStoppedFileName( ) ) ;

				if ( stopFile.exists( ) ) {

					stopFile.delete( ) ;

				}

			}

		} catch ( Exception e ) {

			logger.error( "Failed on user stop file maintenance", e ) ;

		}

	}

	public LinkedHashMap<String, String> buildServiceEnvironmentVariables ( ServiceInstance serviceInstance ) {

		StringWriter sw = new StringWriter( ) ;
		BufferedWriter stringWriter = new BufferedWriter( sw ) ;
		LinkedHashMap<String, String> environmentVariables = new LinkedHashMap<>( ) ;

		addServiceEnvironment( serviceInstance, environmentVariables, true, stringWriter ) ;

		return environmentVariables ;

	}

	public void addServiceEnvironment (
										ServiceInstance serviceInstance ,
										LinkedHashMap<String, String> environmentVariables ,
										boolean isProcessUserVariables ,
										BufferedWriter outputWriter ) {

		Set<String> variableNamesForLogging = new HashSet<>( ) ;

		if ( serviceInstance.getName( ).matches( KubernetesIntegration.getServicePattern( ) )
				&& csapApis.application( ).environmentSettings( ).isVsphereConfigured( ) ) {

			var vsphereConfiguration = csapApis.application( ).environmentSettings( ).getVsphereConfiguration( ) ;
			var vsphereVariables = csapApis.application( ).environmentSettings( ).getVsphereEnv( ) ;

			variableNamesForLogging.addAll( vsphereVariables.keySet( ) ) ;
			logger.debug( "Adding vsphere variables" ) ;
			environmentVariables.putAll( vsphereVariables ) ;
			environmentVariables.put( "vm_path", vsphereConfiguration.at( "/filters/vm-path" ).asText( "not-set" ) ) ;
			environmentVariables.put( "resource_pool_path", vsphereConfiguration.at( "/filters/resource-pool-path" )
					.asText( "not-set" ) ) ;

		}

		// Global Variables - load from the package
		add_map_variables(
				buildConfigMapWithClusterOverrides( EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME, serviceInstance
						.getCluster( ) ),
				isProcessUserVariables,
				serviceInstance,
				environmentVariables,
				variableNamesForLogging,
				outputWriter ) ;

		logger.debug( CsapApplication.header( "{} Global Environment Variables: \n {}" ),
				serviceInstance.getServiceName_Port( ),
				WordUtils.wrap( environmentVariables.toString( ), 40, "\n\t", false ) ) ;

		JsonNode serviceEnvVarDefinition = serviceInstance.getAttributeOrMissing(
				ServiceAttributes.environmentVariables ) ;

		// Load specified configuration maps
		CSAP.asStream( serviceEnvVarDefinition.fieldNames( ) )
				.filter( name -> name.equals( EnvironmentSettings.CONFIGURATION_MAPS ) )
				.map( name -> serviceEnvVarDefinition.path( EnvironmentSettings.CONFIGURATION_MAPS ) )
				.flatMap( configMapNameArray -> CSAP.jsonStream( configMapNameArray ) )
				.map( JsonNode::asText )
				.map( configMapName -> buildConfigMapWithClusterOverrides( configMapName, serviceInstance
						.getCluster( ) ) )
				.forEach( configMap -> {

					// csapApis.application().lifeCycleSettings().getConfigurationMap( configMapName
					// )
					logger.debug( "configMap: {}", CSAP.jsonPrint( configMap ) ) ;

					if ( configMap.isMissingNode( ) ) {

						try {

							outputWriter.write( CsapApplication.LINE
									+ "\n One or more service configuration maps are missing: "
									+ serviceEnvVarDefinition.path( EnvironmentSettings.CONFIGURATION_MAPS )
									+ CsapApplication.LINE ) ;

						} catch ( IOException e ) {

							logger.error( "Failed to print config maps warning: {}", CSAP.buildCsapStack( e ) ) ;

						}

					} else {

						add_map_variables( configMap,
								isProcessUserVariables,
								serviceInstance, environmentVariables, variableNamesForLogging, outputWriter ) ;

					}

				} ) ;

		logger.debug( CsapApplication.header( "{} Lifecycle Environment Variables: \n {}" ),
				serviceInstance.getServiceName_Port( ),
				WordUtils.wrap( environmentVariables.toString( ), 40, "\n\t", false ) ) ;

		// Service Variables
		serviceInstance.environmentVariableNames( )
				.filter( name -> ! name.equals( EnvironmentSettings.CONFIGURATION_MAPS ) )
				.forEach( variableName -> {

					addVariableToEnvVars(
							variableName,
							serviceEnvVarDefinition.path( variableName ).asText( ),
							environmentVariables,
							serviceInstance, variableNamesForLogging,
							outputWriter ) ;

				} ) ;

		logger.debug( CsapApplication.header( "{} Service Environment Variables: \n {}" ),
				serviceInstance.getServiceName_Port( ),
				WordUtils.wrap( environmentVariables.toString( ), 40, "\n\t", false ) ) ;

		// Service Variables using life cycle
		serviceInstance.environmentLifeVariableNames( )
				.filter( name -> ! name.equals( EnvironmentSettings.CONFIGURATION_MAPS ) )
				.forEach( variableName -> {

					addVariableToEnvVars(
							variableName,
							serviceInstance.getLifeEnvironmentVariables( ).path( variableName ).asText( ),
							environmentVariables,
							serviceInstance, variableNamesForLogging,
							outputWriter ) ;

				} ) ;

		logger.debug( CsapApplication.header( "{} Life Environment Variables: \n {}" ),
				serviceInstance.getServiceName_Port( ),
				WordUtils.wrap( environmentVariables.toString( ), 40, "\n\t", false ) ) ;

		environmentVariables.put( ENVIRONMENT_CUSTOM_VALS, variableNamesForLogging.toString( ) ) ;

		// Need for decryption of property files
		if ( csapEncryptableProperties != null ) {

			environmentVariables.put( CsapEncryptionConfiguration.ENV_VARIABLE, csapEncryptableProperties
					.getToken( ) ) ;
			environmentVariables.put( CsapEncryptionConfiguration.ALGORITHM_ENV_VARIABLE, csapEncryptableProperties
					.getAlgorithm( ) ) ;

		}

		// Standard for metadata
		environmentVariables.put( "csapPackage", csapApis.application( ).getActiveProjectName( ) ) ;
		environmentVariables.put( "csapLife", csapApis.application( ).getCsapHostEnvironmentName( ) ) ;
		environmentVariables.put( "csapLbUrl", csapApis.application( ).rootProjectEnvSettings( )
				.getLoadbalancerUrl( ) ) ;

		logger.debug( "environmentVariables: {}", environmentVariables ) ;

		if ( isProcessUserVariables ) {

			environmentVariables.entrySet( ).stream( )
					.filter( variableEntry -> variableEntry.toString( ).contains( CsapConstants.CSAP_VARIABLE_PREFIX ) )
					.forEach( variableEntry -> {

//						variableEntry.setValue( resolveOnePassVariables( serviceInstance, variableEntry
//								.getValue( ) ) ) ;
						variableEntry.setValue( csapApis.application( ).resolveDefinitionVariables( variableEntry
								.getValue( ),
								serviceInstance ) ) ;

					} ) ;

		}

		return ;

	}

	private JsonNode buildConfigMapWithClusterOverrides ( String configurationMapName , String clusterName ) {
		// Global Variables - load from the master package only.
		// JsonNode globalReferences =
		// csapApis.application().lifeCycleSettings().getConfigurationMap(
		// "global" ) ;
		// add_map_variables( serviceInstance, environmentVariables,
		// variableNamesForLogging, globalReferences, outputWriter ) ;

		JsonNode configMap = csapApis.application( ).environmentSettings( )
				.getConfigurationMap( configurationMapName ) ;

		ObjectNode aggregateMap = jsonMapper.createObjectNode( ) ;

		if ( configMap.isObject( ) ) {

			aggregateMap.setAll( (ObjectNode) configMap ) ;

		}

		var clusterOverrideName = configurationMapName + "-" + clusterName ;

		JsonNode configMapClusterOverride = csapApis.application( ).environmentSettings( )
				.getConfigurationMap( clusterOverrideName ) ;

		if ( configMapClusterOverride.isObject( ) ) {

			aggregateMap.setAll( (ObjectNode) configMapClusterOverride ) ;

		}

		//
		if ( aggregateMap.size( ) > 0 ) {

			try {

				aggregateMap = (ObjectNode) jsonMapper.readTree(
						aggregateMap.toString( ).replaceAll( Matcher.quoteReplacement(
								CsapConstants.CSAP_DEF_FQDN_HOST ),
								csapApis.application( ).getHostFqdn( ) ) ) ;

				logger.debug( "clusterOverrideName: {}, aggregateMap: {}", clusterOverrideName, aggregateMap ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed fqdn replace in configmap: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		return aggregateMap ;

	}

	private void add_map_variables (
										JsonNode csapConfigurationMap ,
										boolean isProcessUserVariables ,
										ServiceInstance serviceInstance ,
										LinkedHashMap<String, String> environmentVariables ,
										Set<String> variableNamesForLogging ,
										BufferedWriter outputWriter ) {

		CSAP.asStream( csapConfigurationMap.fieldNames( ) )
				.forEach( originalName -> {

					var resolvedName = originalName ;

					if ( isProcessUserVariables ) {

						resolvedName = processUserVariables( originalName ) ;

					}

					addVariableToEnvVars(
							resolvedName,
							csapConfigurationMap.path( originalName ).asText( ),
							environmentVariables,
							serviceInstance, variableNamesForLogging,
							outputWriter ) ;

				} ) ;

	}

	private static String DO_DECODE = "doDecode:" ;
	private static String PERFORMANCE_APPLICATION = "$application:" ;

	private void addVariableToEnvVars (
										String resolvedName ,
										String originalValue ,
										Map<String, String> environmentVariables ,
										ServiceInstance serviceInstance ,
										Set<String> auditNameList ,
										BufferedWriter outputWriter ) {

		try {

			var resolvedValue = originalValue ;

			auditNameList.add( resolvedName ) ;

			logger.debug( "{} variableName: {}", serviceInstance.getName( ), resolvedName ) ;

			boolean isDecodeNeeded = false ;

			if ( resolvedValue.contains( DO_DECODE ) ) {

				isDecodeNeeded = true ;
				resolvedValue = resolvedValue.substring( DO_DECODE.length( ) ) ;

			}

			// redis service integration

			if ( resolvedValue.contains( CsapConstants.SERVICE_HOSTS ) ) {

				String serviceHosts = resolvedValue.substring( CsapConstants.SERVICE_HOSTS.length( ) ) ;
				resolvedValue = getServiceReferenceHosts( serviceHosts ) ;

			}

			if ( resolvedValue.contains( PERFORMANCE_APPLICATION ) ) {

				String searchKey = resolvedValue.substring( PERFORMANCE_APPLICATION.length( ) ) ;
				resolvedValue = csapApis.osManager( ).getLastCollected( serviceInstance, searchKey ) ;

				// serviceInstance.
			}

			logger.debug( "{} Adding customAttributeName: {} , {}", serviceInstance.getName( ),
					resolvedName, resolvedValue ) ;

			if ( isDecodeNeeded ) {

				resolvedValue = csapApis.application( ).decode( resolvedValue, "Environment Variable: "
						+ resolvedName ) ;

			} else if ( resolvedValue.startsWith( "$" ) ) {

				resolvedValue = serviceInstance.resolveRuntimeVariables( resolvedValue ) ;

			}

			if ( resolvedValue.equals( CSAP_REFERENCE_NOT_FOUND ) ) {

				logger.debug( "Missing environment variable: '{}', reference: '{}'", resolvedName, originalValue ) ;

				var message = "No value found for environment variable: '" + resolvedName + "', reference: '"
						+ originalValue + "'" ;
				outputWriter.write( CsapApplication.header( message ) ) ;

			} else {

				environmentVariables.put( resolvedName, resolvedValue ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "{} Failed building custom list: {}", resolvedName, CSAP.buildCsapStack( e ) ) ;

		}

	}

	public File buildDeplomentFile (
										ServiceInstance serviceInstance ,
										File sourceFile ,
										ObjectNode containerConfiguration ) {

		var fileContents = Application.readFile( sourceFile ) ;

		//
		// First: replace any UI modifications to deployment
		//

		fileContents = replaceDeployOverRides(
				CsapConstants.CSAP_DEF_IMAGE,
				C7.imageName.val( ),
				containerConfiguration, fileContents ) ;

		fileContents = replaceDeployOverRides(
				CsapConstants.CSAP_DEF_HELM_CHART_NAME,
				C7.helmChartName.val( ),
				containerConfiguration, fileContents ) ;

		fileContents = replaceDeployOverRides(
				CsapConstants.CSAP_DEF_HELM_CHART_VERSION,
				C7.helmChartVersion.val( ),
				containerConfiguration, fileContents ) ;

		fileContents = replaceDeployOverRides(
				CsapConstants.CSAP_DEF_HELM_CHART_REPO,
				C7.helmChartRepo.val( ),
				containerConfiguration, fileContents ) ;

		fileContents = replaceDeployOverRides(
				CsapConstants.CSAP_DEF_REPLICA,
				C7.containerCount.val( ),
				containerConfiguration, fileContents ) ;

		//
		// Second: replace any remaining variables
		//
		fileContents = csapApis.application( ).resolveDefinitionVariables( fileContents, serviceInstance ) ;

		// resolve image names eg. image: csap/ to junit-test/
		for ( var set : csapApis.application( ).environmentSettings( ).getKubernetesYamlReplacements( ).entrySet( ) ) {

			fileContents = fileContents.replaceAll(
					Matcher.quoteReplacement( set.getKey( ) ),
					Matcher.quoteReplacement( set.getValue( ) ) ) ;

		}

		var workingFolder = serviceInstance.getWorkingDirectory( ) ;
		var yamlFile = new File( workingFolder, sourceFile.getName( ) ) ;

		try {

			logger.debug( "Creating: {}", yamlFile ) ;
			yamlFile.delete( ) ;
			FileUtils.forceMkdir( workingFolder ) ;
			FileUtils.writeStringToFile( yamlFile, fileContents ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to preserve yaml {} on disk: {}", yamlFile, CSAP.buildCsapStack( e ) ) ;

		}

		return yamlFile ;

	}

	private String replaceDeployOverRides (
											String targetToken ,
											String targetPath ,
											ObjectNode containerConfiguration ,
											String fileContents ) {

		var chartVersion = containerConfiguration.path( targetPath ).asText( ) ;

		if ( StringUtils.isNotEmpty( chartVersion ) ) {

			fileContents = fileContents.replaceAll(
					Matcher.quoteReplacement( targetToken ),
					Matcher.quoteReplacement( chartVersion ) ) ;

		}

		return fileContents ;

	}

	private String processUserVariables ( String originalName ) {

		var resolvedName = originalName ;

		if ( resolvedName.startsWith( CsapConstants.CSAP_VARIABLE_PREFIX ) && resolvedName.length( ) > 2 ) {

			resolvedName = resolvedName.substring( 2 ) ;
			resolvedName = resolvedName.replaceAll(
					Matcher.quoteReplacement( "-" ),
					Matcher.quoteReplacement( "_" ) ) ;

		}

		return resolvedName ;

	}

	private String getServiceReferenceHosts ( String serviceName ) {

		StringBuffer serviceHosts = new StringBuffer( ) ;
		csapApis.application( )
				.getRootProject( ).getAllPackagesModel( )
				.getServiceInstances( serviceName )
				.map( serviceInstance -> serviceInstance.getHostName( ) )
				.forEach( host -> {

					if ( serviceHosts.length( ) > 0 ) {

						serviceHosts.append( " " ) ;

					}

					serviceHosts.append( host ) ;

				} ) ;

		if ( serviceHosts.length( ) == 0 ) {

			logger.warn( "Did not find any instances of service: {}", serviceName ) ;
			serviceHosts.append( "noHostsFoundFor_" + serviceName ) ;

		}

		return serviceHosts.toString( ) ;

	}

	public void submitKillJob ( String userid , String serviceNamePort , ArrayList<String> params ) {

		String serviceName = serviceNamePort ;

		if ( serviceName.indexOf( "_" ) != -1 ) {

			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) ) ;

		}

		try {

			OutputFileMgr outputFm = new OutputFileMgr(
					csapApis.application( ).getCsapWorkingFolder( ), "/"
							+ serviceName + KILL_OP ) ;
			outputFm.print( "\n Request(s) Queued:\n" + serviceDeployExecutor.pendingOperations( ) ) ;

			outputFm.close( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed closing log file", e ) ;

		}

		// logger.info("Generating Event: " + CsapEventClient.CSAP_SVC_CATEGORY
		// + "/" + serviceName);

		csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + serviceName,
				"Kill request added to queue",
				" Command: \n" + params ) ;

		if ( isKillAgentQueued ) {

			logger.warn(
					"CSAP agent kill request proceeding immediately, bypassing queue. Only done if multiple requests received" ) ;
			killJobRunnable( userid, serviceNamePort, params, null ) ;

		} else {

			serviceDeployExecutor.addOperation(
					( ) -> killJobRunnable( userid, serviceNamePort, params, null ),
					userid,
					serviceName,
					"kill" ) ;

		}

		if ( serviceName.equals( CsapConstants.AGENT_NAME ) ) {

			isKillAgentQueued = true ;

		}

		return ;

	}

	public void submitStopJob ( String userid , String serviceNamePort , ArrayList<String> params ) {

		String serviceName = serviceNamePort ;

		if ( serviceName.indexOf( "_" ) != -1 ) {

			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) ) ;

		}

		try {

			OutputFileMgr outputFm = new OutputFileMgr(
					csapApis.application( ).getCsapWorkingFolder( ), "/"
							+ serviceName + STOP_OP ) ;
			outputFm.print( "\n Request(s) Queued:\n" + serviceDeployExecutor.pendingOperations( ) ) ;

			outputFm.close( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed closing log file", e ) ;

		}

		// logger.info("Generating Event: " + CsapEventClient.CSAP_SVC_CATEGORY
		// + "/" + serviceName);

		csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + serviceName,
				"Stop request added to queue",
				" Command: \n" + params ) ;

		serviceDeployExecutor.addOperation(
				( ) -> stopServiceRunnable( userid, serviceNamePort ),
				userid,
				serviceName,
				"stop" ) ;

		return ;

	}

	private boolean isKillAgentQueued = false ;

	void killJobRunnable (
							String userid ,
							String serviceNamePort ,
							ArrayList<String> params ,
							OutputFileMgr outputFmInBound ) {

		try {

			OutputFileMgr outputFm = outputFmInBound ;

			ServiceInstance serviceInstance = csapApis.application( ).getServiceInstanceCurrentHost( serviceNamePort ) ;

			if ( outputFmInBound == null ) {

				outputFm = new OutputFileMgr( csapApis.application( ).getCsapWorkingFolder( ),
						"/" + serviceInstance.getName( ) + KILL_OP ) ;

			}

			// we dump start output immediately to catch any error
			// conditions in logs
			if ( serviceInstance != null && serviceInstance.is_java_application_server( ) ) {

				logger.debug( "Pushing logs Immediately" ) ;
				outputFm.setForceImmediate( true ) ;

			}

			outputFm.print( "Requested by: '" + userid + "'\n" ) ;

			logger.info( CsapApplication.header(
					"{} Invoked kill for {}: params: {}. \n\t Results are stored in: {} " ),
					userid, serviceInstance.getName( ), params, outputFm.getOutputFile( ).getCanonicalPath( ) ) ;

			// Before killing, lets publish alls stats so gaps do not appear
			if ( serviceNamePort.startsWith( CsapConstants.AGENT_NAME ) ) {

				try {

					outputFm.printHeader(
							"Shutting down agent collection threads and flushing events. This can take 30-60 seconds to complete...." ) ;
					logger.warn( "Shutting down agent prior to issuing kill" ) ;
//					csapApis.application( ).shutdown( ) ;
					csapApis.shutDownCsap( ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed shutting down manager services {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			csapApis.osManager( )
					.getJobRunner( )
					.runJobUsingEvent( serviceInstance,
							ServiceJobRunner.Event.preStop,
							outputFm.getBufferedWriter( ) ) ;

			if ( serviceInstance.is_docker_server( ) ) {

				killServiceUsingDocker( serviceInstance, outputFm, params, userid ) ;

			} else {

				if ( serviceInstance.isRunUsingDocker( ) ) {

					params.add( "skipImageRemove" ) ;
					killServiceUsingDocker( serviceInstance, outputFm, params, userid ) ;
					String[] chmodLines = {
							"#!/bin/bash",
							"echo detected docker container, updating files owned by root to 777 in "
									+ serviceInstance.getWorkingDirectory( ),
							"find " + serviceInstance.getWorkingDirectory( ) + " -user root | xargs chmod 777",
							""
					} ;
					String chmodResponse = osCommandRunner.runUsingRootUser(
							"dockerFileCleanup",
							Arrays.asList( chmodLines ) ) ;
					outputFm.print( chmodResponse ) ;

				}

				runShellCommand( userid, KILL_SCRIPT, serviceInstance, params,
						buildDockerEnvVariables( serviceInstance ),
						null, null,
						outputFm.getBufferedWriter( ) ) ;

			}

			csapApis.osManager( )
					.getJobRunner( )
					.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.postStop, outputFm
							.getBufferedWriter( ) ) ;

			if ( outputFmInBound == null ) {

				outputFm.opCompleted( ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to complete kill {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.warn( CsapApplication.arrowMessage( " Kill Completed for service: {}" ), serviceNamePort) ;

	}

	void stopServiceRunnable ( String user , String svcName ) {

		logger.info( "service : " + svcName ) ;

		ArrayList<String> params = new ArrayList<String>( ) ;

		OutputFileMgr outputFm = null ;
		String results = "Failed" ;

		try {

			outputFm = new OutputFileMgr(
					csapApis.application( ).getCsapWorkingFolder( ), "/"
							+ svcName + ServiceOsManager.STOP_OP ) ;

			// Runs a blocking request

			results = run_service_script(
					user,
					STOP_SCRIPT,
					svcName,
					params,
					null, outputFm.getBufferedWriter( ) ) ;

			outputFm.opCompleted( ) ;

		} catch ( Exception e ) {

			logger.warn( "Stop operation failed" ) ;

		}

		return ;

	}

	public void submitStartJob (
									String userid ,
									String serviceNamePort ,
									ArrayList<String> params ,
									String commandArguments ,
									String deployId ) {

		String serviceName = serviceNamePort ;

		if ( serviceName.indexOf( "_" ) != -1 ) {

			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) ) ;

		}

		try {

			OutputFileMgr outputFm = new OutputFileMgr(
					csapApis.application( ).getCsapWorkingFolder( ), "/"
							+ serviceName + START_OP ) ;
			outputFm.print( "\n Request(s) Queued:\n" + serviceDeployExecutor.pendingOperations( ) ) ;

			outputFm.close( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed closing log file", e ) ;

		}

		serviceDeployExecutor.addOperation(
				( ) -> startServiceJobRunnable( userid, serviceNamePort, params, commandArguments, deployId ),
				userid,
				serviceName,
				"start" ) ;

		csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + serviceName,
				"Start request added to queue",
				" Command: \n" + params ) ;

		return ;

	}

	void startServiceJobRunnable (
									String userid ,
									String serviceNamePort ,
									ArrayList<String> params ,
									String commandArguments ,
									String deployId ) {

		if ( serviceNamePort.equals( CsapConstants.AGENT_NAME ) ) {

			try {

				csapApis.shutDownCsap( ) ;

			} catch ( Exception e ) {

				logger.info( "Failed shutting down manager services: {}", CSAP.buildCsapStack( e ) ) ;

			}

		} else if ( csapApis.isShutdownInProgress( ) ) {

			logger.info( "Deployment aborted due to shutdown in progress: {} ", serviceNamePort ) ;
			return ;

		}

		OutputFileMgr outputFm = null ;

		ServiceInstance serviceInstance = csapApis.application( ).getServiceInstanceCurrentHost( serviceNamePort ) ;

		try {

			outputFm = new OutputFileMgr( csapApis.application( ).getCsapWorkingFolder( ),
					"/" + serviceInstance.getName( ) + START_OP ) ;

			logger.info(
					"{} Invoking start script for {}: params: {}, commandArguments: {}. \n\t Results are stored in: {} ",
					userid, serviceNamePort, params,
					commandArguments,
					outputFm.getOutputFile( ).getCanonicalPath( ) ) ;

			// we dump start output immediately to catch any error
			// conditions in logs
			if ( serviceInstance != null && serviceInstance.is_java_application_server( ) ) {

				logger.debug( "Pushing logs Immediately" ) ;
				outputFm.setForceImmediate( true ) ;

			}

			outputFm.print( "Requested by: '" + userid + "'\n" ) ;

			boolean skipStart = false ;

			if ( StringUtils.isNoneEmpty( deployId ) ) {

				File syncLocation = getSyncLocation( ) ;
				syncLocation.mkdirs( ) ;

				// more defensive - nio handles large director
				try ( Stream<Path> pathStream = Files.list( syncLocation.toPath( ) ) ) {

					Optional<Path> deploymentCheck = pathStream
							.filter( path -> path.getFileName( ).toString( ).endsWith( deployId ) )
							.findFirst( ) ;

					if ( ! deploymentCheck.isPresent( ) ) {

						skipStart = true ;

					} else {

						List<String> lines = Files.readAllLines( deploymentCheck.get( ) ) ;

						if ( lines.isEmpty( ) || lines.get( 0 ).toLowerCase( ).contains( "fail" ) ) {

							logger.warn( "Deployment file is empty or containes fail" ) ;
							skipStart = true ;

						}

					}

				}

			}

			if ( ! skipStart ) {

				csapApis.osManager( )
						.getJobRunner( )
						.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.preStart, outputFm
								.getBufferedWriter( ) ) ;

				if ( serviceInstance.is_docker_server( ) ) {

					if ( serviceInstance.isSkipSpecificationGeneration( ) ) {

						outputFm.print( "Kubernetes file deployment performed - skipping start up" ) ;

					} else {

						startServiceUsingDocker( serviceInstance, outputFm, commandArguments, userid ) ;

					}

				} else {

					runShellCommand(
							userid, START_FILE, serviceInstance, params,
							buildDockerEnvVariables( serviceInstance ),
							commandArguments, null,
							outputFm.getBufferedWriter( ) ) ;

					if ( serviceInstance.isRunUsingDocker( ) ) {

						startServiceUsingDocker( serviceInstance, outputFm, commandArguments, userid ) ;

					}

				}

				csapApis.osManager( )
						.getJobRunner( )
						.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.postStart, outputFm
								.getBufferedWriter( ) ) ;

			} else {

				outputFm.print( "Warning: skipping start due to deployment failure. Reference id: " + deployId ) ;
				logger.warn( "Warning: skipping start of '{}' due to deployment failure. Reference id: '{}'",
						serviceInstance.getName( ),
						deployId ) ;

			}

		} catch ( Exception e ) {

			String message = "Failed to start service: " +
					CSAP.buildCsapStack( e ) ;
			logger.error( message ) ;
			outputFm.print( message ) ;

		} finally {

			if ( outputFm != null ) {

				outputFm.opCompleted( ) ;

			}

		}

		logger.warn( CsapApplication.arrowMessage( " Startup Completed for service: {}" ), serviceNamePort) ;

	}

	private Map<String, String> buildDockerEnvVariables ( ServiceInstance serviceInstance ) {

		Map<String, String> startEnvironmentVariables = new HashMap<>( ) ;

		if ( serviceInstance.isRunUsingDocker( ) ) {

			startEnvironmentVariables.put( "csapDockerTarget", TRUE_STRING ) ;

		} else {

			startEnvironmentVariables.put( "csapDockerTarget", "false" ) ;

		}

		return startEnvironmentVariables ;

	}

	public File getReImageFile ( ) {

		return new File( csapApis.application( ).getCsapInstallFolder( ), "reImageIndicator" ) ;

	}

	public void submitDeployJob (
									String requestedByUserid ,
									ServiceInstance instance ,
									MultiValueMap<String, String> rebuildVariables ,
									boolean isPerformStart ,
									boolean isForceDeploy ) {

		logger.debug( "adding: {}", instance.toSummaryString( ) ) ;

		if ( serviceDeployExecutor.pendingOperationsCount( ) == 0 ||
				( serviceDeployExecutor.pendingOperationsCount( ) > 0
						&& ! serviceDeployExecutor.isServiceQueued( instance.getName( ) ) ) ) {

			// update deploy file to reflect new schedule in progress.
			try {

				OutputFileMgr outputFm = new OutputFileMgr(
						csapApis.application( ).getCsapWorkingFolder( ), "/"
								+ instance.getName( ) + DEPLOY_OP ) ;
				outputFm.print( "*** Scheduling Deployment" ) ;
				outputFm.close( ) ;

				// do not to opComplete as UI polling will end
			} catch ( Exception e ) {

				logger.warn( "Failed creating output file", CSAP.buildCsapStack( e ) ) ;

			}

		}

		String userName = Application.SYS_USER ;

		if ( ! instance.getName( ).equals( CSAP_AGENT_AUTO_START_COMPLETE ) ) {

			if ( ( csapApis.application( ).isDesktopHost( )
				|| csapApis.application().isMacOsProfileActive() )
					&& ! csapApis.application( ).isBootstrapComplete( ) ) {

				logger.warn( CsapApplication.testHeader( "{} Skipping deployment on desktop startup" ), instance
						.getName( ) ) ;
				return ;

			}

			if ( rebuildVariables != null && rebuildVariables.containsKey( "scmUserid" ) ) {

				userName = rebuildVariables.getFirst( "scmUserid" ) ;

				if ( userName.equals( "dummy" ) ) {

					userName = Application.SYS_USER ;

				}

			}

			csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + instance.getName( ),
					"Deploy request added to queue",
					"Timeout configured in cluster.js: "
							+ instance.getDeployTimeOutSeconds( ) + ", Command: \n" + rebuildVariables ) ;

		}

		serviceDeployExecutor.addOperation(
				( ) -> deployServiceRunnable( requestedByUserid, instance, rebuildVariables, isPerformStart,
						isForceDeploy ),
				userName,
				instance.getName( ),
				"deployment" ) ;

		return ;

	}

	void deployServiceRunnable (
									String requestedByUserid ,
									ServiceInstance instance ,
									MultiValueMap<String, String> rebuildVariables ,
									boolean isPerformStart ,
									boolean isForceDeploy ) {

		if ( csapApis.isShutdownInProgress( ) || csapApis.application( ).isJunit( ) ) {

			logger.info( "Deployment aborted due to shutdown in progress: {} ", instance ) ;
			return ;

		}

		try {

			logger.debug( "adding: {}", instance.toSummaryString( ) ) ;

			//
			if ( instance.getName( ).equals( CSAP_AGENT_AUTO_START_COMPLETE ) ) {

				// hook so UI can switch agent out of bootstrap status
				csapApis.application( ).setBootstrapComplete( ) ;
				getReImageFile( ).delete( ) ;

				csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/agent-start-up",
						"All services synchronized", null,
						csapApis.application( ).healthManager( ).statusForAdminOrAgent( ServiceAlertsEnum.ALERT_LEVEL,
								false ) ) ;

				// tagged again - to appear in UI view
				csapApis.events( ).publishEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + CsapConstants.AGENT_NAME,
						"All services synchronized", null,
						csapApis.application( ).healthManager( ).statusForAdminOrAgent( ServiceAlertsEnum.ALERT_LEVEL,
								false ) ) ;

			} else {

				deployAndOptionalStart( requestedByUserid, instance, rebuildVariables, isPerformStart, isForceDeploy ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed deployment {}",
					CSAP.buildCsapStack( e ) ) ;

		} finally {

			csapApis.application( ).run_application_scan( ) ;

		}

	}

	public static String filterField ( String input , String filter ) {

		return input.replaceAll( "\\b" + filter + "[^\\s]*",
				filter + "[*MASKED*]," ) ;

	}

	private void deployAndOptionalStart (
											String requestedByUserid ,
											ServiceInstance serviceInstance ,
											MultiValueMap<String, String> deploymentVariables ,
											boolean isPerformStart ,
											boolean isForceDeploy )
		throws IOException ,
		Exception {

		logger.debug( CsapApplication.header( "Deployment: {}  \n {} " ),
				serviceInstance.getServiceName_Port( ),
				filterField( deploymentVariables.toString( ), "scmPass" ) ) ;

		if ( serviceInstance.getName( ).equals( C7.dockerService.val( ) )
				|| serviceInstance.getName( ).equals( C7.podmanService.val( ) ) ) {

			csapApis.setContainerProviderDeployInProgress( true ) ;

		}

		csapApis.application( ).setAgentStatus( serviceInstance.getServiceName_Port( ) ) ;

		// rebuildServer("CsAgentAutoDeploy", "dummyPass",
		// "dummBranch", serviceName + "_" + servicePort,
		// javaOpts, "", null, null, MAVEN_DEFAULT_BUILD
		// + ":dummyStringToSkipSvn", null, null);
		if ( getReImageFile( ).exists( ) && serviceInstance.is_csap_api_server( )
				&& csapApis.application( ).isRunningAsRoot( ) ) {

			if ( serviceInstance.getUser( ) != null && ! serviceInstance.getUser( ).equals( csapApis.application( )
					.getAgentRunUser( ) ) ) {

				File propFile = new File( "/home/" + serviceInstance.getUser( ) ) ;

				serviceInstance.getDefaultContainer( ).setCpuClean( ) ;
				logger.info( "Doing a clean of " + propFile.getAbsolutePath( ) ) ;

				List<String> parmList = Arrays.asList( "bash", "-c", "sudo rm -rf "
						+ propFile.getAbsolutePath( ) + "/*" ) ;
				// osCommandRunner.executeString(parmList);
				osCommandRunner.executeString( parmList,
						csapApis.application( ).getCsapInstallFolder( ), null, null, 600, 1, null ) ;
				serviceInstance.getDefaultContainer( ).setCpuAuto( ) ;

			}

		}

		File csapPackageFolder = csapApis.application( ).getCsapPackageFolder( ) ;
		File deployFile = new File( csapPackageFolder, serviceInstance.getDeployFileName( ) ) ;

		// We always use existing artifact if it exists.
		if ( ( ! deployFile.exists( ) ) || isForceDeploy ) {

			// we need to rebuild
			logger.info( "Deploying Service: {} \t Force Deploy: {} \n\t path: {}",
					serviceInstance.getServiceName_Port( ), isForceDeploy, deployFile.getAbsolutePath( ) ) ;

			// This adds default parameter values to handle restart scenario
			// where no params are passed
			// They are retrieved via the getFirst method
			deploymentVariables.add( "scmUserid", Application.SYS_USER ) ;
			deploymentVariables.add( "scmPass", encryptor.encrypt( "dummyPass" ) ) ;
			deploymentVariables.add( "scmBranch", "dummBranch" ) ;
			deploymentVariables.add( "mavenDeployArtifact", MAVEN_DEFAULT_BUILD + ":dummyStringToSkipSvn" ) ;
			deploymentVariables.add( "scmCommand", null ) ;
			deploymentVariables.add( "targetScpHosts", "" ) ;
			deploymentVariables.add( "hotDeploy", null ) ;

			OutputFileMgr outputFm = new OutputFileMgr(
					csapApis.application( ).getCsapWorkingFolder( ), "/"
							+ serviceInstance.getName( ) + DEPLOY_OP ) ;

			// outputFm.printImmediate("Building: " +
			// rebuildVariables);
			try {

				// pending refactoring
				var deployConfiguration = deploymentVariables.getFirst( "mavenDeployArtifact" ) ;
				var parameters = deploymentVariables.getFirst( "javaOpts" ) ;

				deployService(
						serviceInstance,
						deploymentVariables.getFirst( "primaryHost" ),
						deploymentVariables.getFirst( "deployId" ),
						requestedByUserid,
						deploymentVariables.getFirst( "scmUserid" ),
						deploymentVariables.getFirst( "scmPass" ),
						deploymentVariables.getFirst( "repoPass" ),
						deploymentVariables.getFirst( "scmBranch" ),
						deployConfiguration,
						deploymentVariables.getFirst( "scmCommand" ),
						deploymentVariables.getFirst( "targetScpHosts" ),
						deploymentVariables.getFirst( "hotDeploy" ),
						parameters,
						outputFm ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to deploy: {}", CSAP.buildCsapStack( e ) ) ;

			} finally {

				outputFm.opCompleted( ) ;

			}

			// syncBuildForScmSession(scmUserid, scmPass, scmBranch,
			// svcName, mavenDeployArtifact, scmCommand,
			// targetScpHosts, hotDeploy, response, session)
		}

		// UI starts are triggered client side, so this is usually
		// invoked only from localhost during startup
		if ( isPerformStart && ! serviceInstance.is_cluster_kubernetes( ) ) {

			logger.info( serviceInstance.getServiceName_Port( ) + ": Found deployment artifact in "
					+ csapPackageFolder.getAbsolutePath( ) + ", Issueing a start" ) ;
			ArrayList<String> params = new ArrayList<String>( ) ;

			// typically a restart of VM.
			// params.add("-cleanType");
			// params.add("clean");
			// params.add("-hotDeploy");
			// params.add("-skipDeployment");
			OutputFileMgr outputFm = new OutputFileMgr(
					csapApis.application( ).getCsapWorkingFolder( ), "/"
							+ serviceInstance.getName( ) + START_OP ) ;

			try {

				csapApis.osManager( )
						.getJobRunner( )
						.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.preStart, outputFm
								.getBufferedWriter( ) ) ;

				if ( serviceInstance.is_docker_server( ) ) {

					startServiceUsingDocker( serviceInstance, outputFm, null, requestedByUserid ) ;

				} else {

					// String results = runScript( requestedByUserid,
					// START_FILE,
					// serviceInstance.getServiceName_Port(),
					// params, null, outputFm.getBufferedWriter() );
					runShellCommand(
							requestedByUserid, START_FILE, serviceInstance, params,
							buildDockerEnvVariables( serviceInstance ),
							null, null,
							outputFm.getBufferedWriter( ) ) ;

					if ( serviceInstance.isRunUsingDocker( ) ) {

						startServiceUsingDocker( serviceInstance, outputFm, null, requestedByUserid ) ;

					}

				}

				csapApis.osManager( )
						.getJobRunner( )
						.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.postStart, outputFm
								.getBufferedWriter( ) ) ;

			} finally {

				outputFm.opCompleted( ) ;

			}

		}

		if ( serviceInstance.getName( ).equals( C7.dockerService.val( ) )
				|| serviceInstance.getName( ).equals( C7.podmanService.val( ) ) ) {

			csapApis.setContainerProviderDeployInProgress( false ) ;
			File updatedAgentGroup = new File( serviceInstance.getWorkingDirectory( ),
					"restart-agent-for-docker-group" ) ;

			if ( updatedAgentGroup.exists( ) ) {

				logger.info( CsapApplication.header( "Restart trigger: {}" ), updatedAgentGroup.getAbsolutePath( ) ) ;
				FileUtils.deleteQuietly( updatedAgentGroup ) ;
				csapApis.application( ).shutdown( ) ;
				List<String> script = List.of( "systemctl restart csap" ) ;
				osCommandRunner.runUsingRootUser( "docker-group-restart-trigger", script ) ;

			}

		}

		csapApis.osManager( ).resetAllCaches( ) ;


		logger.warn( CsapApplication.arrowMessage( " Deployment Completed for service: {}" ), serviceInstance.getName( )) ;

		csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + serviceInstance.getName( ),
				"Deployment Completed For Service " + serviceInstance.getServiceName_Port( ),
				"" ) ;

		serviceInstance.getDefaultContainer( ).setCpuReset( ) ;

		// refresh status after deployment
		if ( ! csapApis.application( ).isBootstrapComplete( )
				&& serviceInstance.getName( ).matches( KubernetesIntegration.getServicePattern( ) ) ) {

			logger.info( "Sleeping 10 seconds to let kubelet initialize" ) ;
			TimeUnit.SECONDS.sleep( 10 ) ;

		}

	}

	private File getSyncLocation ( )
		throws IOException {

		return new File( csapApis.application( ).getCsapSavedFolder( ), PACKAGE_SYNC ).getCanonicalFile( ) ;

	}

	@Autowired ( required = false )
	ContainerIntegration dockerHelper = null ;

	private String deployContainerService (
											ServiceInstance serviceInstance ,
											OutputFileMgr outputFileMgr ,
											String deployConfiguration ,
											String csapUserid) {

		if ( serviceInstance.is_cluster_kubernetes( ) ) {

			boolean isCurrentlyRunning = csapApis.isKubernetesInstalledAndActive( ) && kubernetes.isPodRunning(
					serviceInstance ) ;

			if ( isCurrentlyRunning
					&& ! serviceInstance.isSkipSpecificationGeneration( )
					&& ! serviceInstance.isHelmConfigured( ) ) {

				outputFileMgr.printHeader(
						"Found running container instance  for a generate spec - issueing a remove" ) ;

				try {

					// killServiceUsingDocker( serviceInstance, outputFileMgr, new
					// ArrayList<String>(), scmUserid ) ;
					killJobRunnable( csapUserid, serviceInstance.getServiceName_Port( ), new ArrayList<String>( ),
							outputFileMgr ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed to kill container{} ", CSAP.buildCsapStack( e ) ) ;
					outputFileMgr.printHeader( "Failed to kill container" + CSAP.buildCsapStack( e ) ) ;

				}

			}

			// Remove previous stopped file
			synchronizeServiceState( START_FILE, serviceInstance ) ;

			File versionFile = csapApis.application( ).getDeployVersionFile( serviceInstance ) ;

			ObjectNode containerConfiguration = build_container_run_configuration( serviceInstance, outputFileMgr,
					deployConfiguration ) ;

			logger.debug( "containerConfiguration: {}", CSAP.jsonPrint( containerConfiguration ) ) ;

			createVersionFile( versionFile, null, csapUserid, "kubernetes spec deployment" ) ;

			var specFiles = buildSpecificationFileArray( serviceInstance,
					serviceInstance.getKubernetesDeploymentSpecifications( ) ) ;

			var externalDeployResults = deploy_using_specification_files(
					serviceInstance,
					containerConfiguration,
					specFiles,
					outputFileMgr,
					csapUserid,
					isCurrentlyRunning ) ;

			logger.info( "externalDeployResults: {}", externalDeployResults ) ;

			if ( serviceInstance.isSkipSpecificationGeneration( ) ) {

				outputFileMgr.printImmediate( BUILD_SUCCESS ) ;
				return BUILD_SUCCESS ;

			} else {

				outputFileMgr.printHeader( "Generating kubernetes specifications for deployment" ) ;
				ObjectNode results = kubernetes.specBuilder( ).deploy_csap_service( serviceInstance,
						containerConfiguration ) ;
				addResultsToOutput( outputFileMgr, results, results.path( C7.response_start_results
						.val( ) ) ) ;
				outputFileMgr.printImmediate( BUILD_SUCCESS ) ;
				return BUILD_SUCCESS ;

			}

		}

		String imageName = serviceInstance.getDockerImageName( ) ;

		if ( deployConfiguration != null &&
				! deployConfiguration.startsWith( MAVEN_DEFAULT_BUILD ) &&
				deployConfiguration.trim( ).length( ) > 0 ) {

			imageName = deployConfiguration ;

		}

		logger.info( "Deployment of {} using image: {} ", serviceInstance.getName( ), imageName ) ;

		outputFileMgr
				.printHeader( "starting docker pull of image: " + imageName ) ;

		var results = dockerHelper.imagePull(
				imageName,
				outputFileMgr,
				serviceInstance.getDeployTimeOutSeconds( ), 2 ) ;

		logger.info( "pull results: {}", results ) ;
		outputFileMgr.printImmediate( CSAP.jsonPrint( results ) ) ;

		boolean pullCompleted = results.path( C7.pull_complete.val( ) ).asBoolean( ) ;
		boolean pullErrors = results.path( C7.error.val( ) ).asBoolean( ) ;

		if ( ( ! pullErrors ) &&
				pullCompleted ) {

			File versionFile = csapApis.application( ).getDeployVersionFile( serviceInstance ) ;
			createVersionFile( versionFile, null, csapUserid, "Docker Pull" ) ;
			return BUILD_SUCCESS ;

		}

		logger.warn( "Pull did not complete,  errorsFound: '{}', pull still in progress: '{}'", pullErrors,
				! pullCompleted ) ;

		if ( ! pullCompleted ) {

			outputFileMgr.printImmediate( "\n\nWARNING: docker pull did not complete in specified: "
					+ serviceInstance.getDeployTimeOutMinutes( )
					+ "minutes. Verify docker repository, and/or update sevice deployment time out minutes\n\n" ) ;

		}

		return "Docker Pull did not complete successfully" ;

	}

	private String deploy_using_specification_files (
														ServiceInstance serviceInstance ,
														ObjectNode containerConfiguration ,
														Stream<URI> filesToDeploy ,
														OutputFileMgr outputFileMgr ,
														String scmUserid ,
														boolean isCurrentlyRunning ) {

		var workingFolder = serviceInstance.getWorkingDirectory( ) ;

		if ( workingFolder.exists( ) ) {

			outputFileMgr.printHeader( "Deleting previous deployment folder : "
					+ workingFolder.getAbsolutePath( ) ) ;
			FileUtils.deleteQuietly( workingFolder ) ;

		}

		var deploymentCommands = filesToDeploy
				.map( filePath -> {

					var command = "unable to run" ;

					try {

						File sourceFile = new File( filePath ) ;

						if ( ! sourceFile.exists( ) ) {

							outputFileMgr.printHeader( "Error: unable to locate deployment file : "
									+ sourceFile.getAbsolutePath( ) ) ;

							command = "skipping " + sourceFile.getAbsolutePath( ) + " file not found" ;

						} else {

							var deploymentFile = buildDeplomentFile(
									serviceInstance,
									sourceFile,
									containerConfiguration ) ;

							if ( sourceFile.getName( ).endsWith( ".sh" ) ) {

								outputFileMgr.printHeader( "invoking script with deploy " + deploymentFile
										.getAbsolutePath( ) ) ;

								var output = csapApis.osManager( ).kubernetesShell(
										"deploy", deploymentFile,
										C7.response_shell ) ;

								outputFileMgr.printHeader( output.path( C7.response_shell.val( ) )
										.asText( ) ) ;

							} else if ( sourceFile.getName( ).startsWith( "helm" ) ) {
								// just written out

							} else {

								var yamlFile = csapApis.application( ).createYamlFile(
										"-deploy-" + sourceFile.getName( ) + "-",
										Application.readFile( deploymentFile ),
										serviceInstance.getName( ) ) ;

								var deployCommand = "create --save-config" ;

								if ( isCurrentlyRunning ) {

									outputFileMgr.print( CSAP.padNoLine( "note" )
											+ "found running containers, switching to updating the existing deployment" ) ;
									deployCommand = "apply" ;

								}

								command = deployCommand + " -f " + yamlFile.getAbsolutePath( ) ;

								outputFileMgr.printHeader( command ) ;
								ObjectNode output = csapApis.osManager( ).kubernetesCli( command,
										C7.response_shell ) ;
								outputFileMgr.printHeader( output.path( C7.response_shell.val( ) )
										.asText( ) ) ;

							}

						}

					} catch ( IOException e ) {

						logger.warn( "Failed to run deploy: {}", CSAP.buildCsapStack( e ) ) ;

					}

					return command ;

				} )

				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "Deployment specification files (kubectl): '{}'", deploymentCommands ) ;
		return deploymentCommands ;

	}

	private void startServiceUsingDocker (
											ServiceInstance serviceInstance ,
											OutputFileMgr outputFileMgr ,
											String commandArguments ,
											String userid )
		throws Exception {

		//
		logger.info( "Starting docker service: {}, type: {}, using: {} ",
				serviceInstance.getServiceName_Port( ), serviceInstance.getRuntime( ), commandArguments ) ;

		if ( ! csapApis.isContainerProviderInstalledAndActive( ) ) {

			outputFileMgr.printHeader( "WARNING: docker not enabled, skipping docker start" ) ;
			logger.warn( "docker not enabled, skipping docker start" ) ;
			return ;

		}

		ObjectNode dockerRunConfiguration = build_container_run_configuration( serviceInstance, outputFileMgr,
				commandArguments ) ;

		if ( dockerHelper.findContainerByName( serviceInstance.getDockerContainerPath( ) ).isPresent( ) ) {

			outputFileMgr.printHeader( "Found running container instance - issueing a remove" ) ;
			killServiceUsingDocker( serviceInstance, outputFileMgr, new ArrayList<String>( ), userid ) ;

		}

		// remove stopped state file
		synchronizeServiceState( START_FILE, serviceInstance ) ;

		if ( csapApis.isContainerProviderInstalledAndActive( )
				&& serviceInstance.isRunUsingDocker( ) ) {

			// SpringBoot inside of docker

			String targetImage = dockerRunConfiguration.path( C7.imageName.val( ) ).asText( "" ) ;

			if ( ! StringUtils.isEmpty( targetImage ) ) {

				// && ! dockerHelper.findImageByName( targetImage ).isPresent()
				logger.warn( "Running in docker container, checking for specified image: {}", targetImage ) ;
				ObjectNode results = dockerHelper.imagePull(
						targetImage,
						outputFileMgr,
						serviceInstance.getDeployTimeOutSeconds( ),
						2 ) ;

				logger.info( "pull results: {}", results ) ;

			} else {

				outputFileMgr.print( "Skipping pull because image is empty" ) ;

			}

		}

		ObjectNode results ;

		if ( ! serviceInstance.is_cluster_kubernetes( ) ) {

			var dockerRunCommand = dockerRunConfiguration.path( C7.run.val( ) ).asText( ) ;

			if ( StringUtils.isNotEmpty( dockerRunCommand ) ) {

				if ( dockerRunCommand.equals( "skip-start" )) {
					results = jsonMapper.createObjectNode() ;
					results.put( C7.response_shell.val(), "docker run command: skip-shell. Assuming event-post-start is being used to launch container" ) ;
				} else {
					results = csapApis.osManager( ).dockerCli( dockerRunCommand, C7.response_shell );
				}

			} else {

				outputFileMgr.printHeader( "Creating docker container" ) ;
				results = dockerHelper.containerCreateAndStart( serviceInstance, dockerRunConfiguration ) ;

			}

		} else {

			outputFileMgr.printHeader( "Skipping kubernetes start" ) ;
			// results = kubernetes.deploy_csap_service( serviceInstance,
			// dockerRunConfiguration ) ;
			results = jsonMapper.createObjectNode( ) ;
			ObjectNode startResults = results.putObject( C7.response_start_results.val( ) ) ;
			startResults.put( "summary", "Kubernetes Deployments are autostarted" ) ;

		}

		serviceInstance.setFileSystemScanRequired( true ) ;

		addResultsToOutput( outputFileMgr, results, results.path( C7.response_start_results.val( ) ) ) ;

	}

	private ObjectNode build_container_run_configuration (
															ServiceInstance serviceInstance ,
															OutputFileMgr outputFileMgr ,
															String deployConfiguration ) {

		ObjectNode runSettings = serviceInstance.getDockerSettings( ).deepCopy( ) ;

		// Boot in Docker support
		if ( StringUtils.isNotEmpty( deployConfiguration ) && ! deployConfiguration.startsWith(
				MAVEN_DEFAULT_BUILD ) ) {

			if ( serviceInstance.isRunUsingDocker( ) ) {

				// parameters not updated when running in container.
				logger.info( "Ignoring '{}' due to running in container", deployConfiguration ) ;

			} else {

				logger.debug( "commandArguments: {}", deployConfiguration ) ;

				try {

					runSettings = (ObjectNode) jsonMapper.readTree( deployConfiguration ) ;

				} catch ( Exception e ) {

					logger.warn( "Unable to parse passed command: {}, default will be used. Parsing error: {}",
							deployConfiguration,
							CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

		logger.debug( "Pre sub def: {}", CSAP.jsonPrint( runSettings ) ) ;
		// update commands with command line overrides
		JsonNode commandArray = runSettings.path( C7.command.val( ) ) ;
		ArrayNode updatedCommands = buildRuntimeParameters( serviceInstance, deployConfiguration, commandArray ) ;

		if ( updatedCommands.size( ) > 0 ) {

			runSettings.set( C7.command.val( ), updatedCommands ) ;

		}

		// update entry with command line overrides
		JsonNode entryArray = runSettings.path( C7.entryPoint.val( ) ) ;
		ArrayNode updatedEntry = buildRuntimeParameters( serviceInstance, deployConfiguration, entryArray ) ;

		if ( updatedEntry.size( ) > 0 ) {

			runSettings.set( C7.entryPoint.val( ), updatedEntry ) ;

		}

		logger.debug( "Post sub def: {}", CSAP.jsonPrint( runSettings ) ) ;

		ArrayNode runtimeEnvVars = (ArrayNode) runSettings.get( C7.environmentVariables.val( ) ) ;

		if ( runtimeEnvVars == null ) {

			runtimeEnvVars = runSettings.putArray( C7.environmentVariables.val( ) ) ;

		}

		ArrayNode updatedEnvironmentVariables = runtimeEnvVars ;

		// add environment variables from service
		LinkedHashMap<String, String> serviceEnvVars = new LinkedHashMap<>( ) ;
		addCsapModelEnvVariables( serviceInstance, serviceEnvVars ) ;
		addServiceEnvironment( serviceInstance, serviceEnvVars, true, outputFileMgr.getBufferedWriter( ) ) ;

		serviceEnvVars
				.entrySet( )
				.stream( )
				.forEach( variable -> {

					updatedEnvironmentVariables.add( variable.getKey( ) + "=" + variable.getValue( ) ) ;

				} ) ;

		// finally - replace variables
		logger.debug( "Before replaceRunVariables: {}", CSAP.jsonPrint( runSettings ) ) ;

		try {

			runSettings = (ObjectNode) jsonMapper.readTree(
					csapApis.application( ).resolveDefinitionVariables( runSettings.toString( ), serviceInstance,
							true ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to add runtime configuration: {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.debug( "After replaceRunVariables: {}", CSAP.jsonPrint( runSettings ) ) ;
		return runSettings ;

	}

	@Autowired ( required = false )
	KubernetesIntegration kubernetes = null ;

	private ArrayNode buildRuntimeParameters (
												ServiceInstance serviceInstance ,
												String commandArguments ,
												JsonNode entryOrCommandItems ) {

		ArrayNode updatedCommands = jsonMapper.createArrayNode( ) ;

		if ( entryOrCommandItems != null ) {

			CsapConstants.jsonStream( entryOrCommandItems )
					.map( JsonNode::asText )
					.map( String::trim )
					.forEach( command -> {

						if ( command.equals( CsapConstants.CSAP_DEF_PARAMETERS ) ) {

							// this is itemized parameters scenarios
							String[] serviceParams = commandArguments.split( " " ) ;

							for ( String param : serviceParams ) {

								String trimmedParam = serviceInstance.resolveRuntimeVariables( param ) ;

								if ( trimmedParam.length( ) > 0 ) {

									updatedCommands.add( trimmedParam ) ;

								}

							}

						} else {

							// run time updates of parameters of java in docker
							String commandWithRunOptions = command ;

							if ( StringUtils.isNotEmpty( commandArguments ) &&
									command.contains( CsapConstants.CSAP_DEF_PARAMETERS ) ) {

								commandWithRunOptions = command.trim( ).replaceAll(
										Matcher.quoteReplacement( CsapConstants.CSAP_DEF_PARAMETERS ),
										Matcher.quoteReplacement( commandArguments ) ) ;

							}

							String commandItem = serviceInstance.resolveRuntimeVariables( commandWithRunOptions ) ;
							updatedCommands.add( commandItem ) ;

						}

					} ) ;

		}

		return updatedCommands ;

	}

	public void killServiceUsingDocker (
											ServiceInstance serviceInstance ,
											OutputFileMgr outputFileMgr ,
											ArrayList<String> params ,
											String userid )
		throws Exception {

		//
		logger.info( "Killing docker service: {}, using: {} ",
				serviceInstance.getServiceName_Port( ), params ) ;

		synchronizeServiceState( KILL_SCRIPT, serviceInstance ) ;

		outputFileMgr.printHeader( "Issueing remove of " + serviceInstance.getServiceName_Port( ) + " parameters: "
				+ params ) ;

		ObjectNode killResults ;

		if ( serviceInstance.is_cluster_kubernetes( ) ) {

			var specFiles = buildSpecificationFileArray(
					serviceInstance,
					serviceInstance.getKubernetesDeploymentSpecifications( ) ) ;

			if ( serviceInstance.isSkipSpecificationGeneration( ) ) {

				var specRemoveResults = kubernetes_remove_by_specs( serviceInstance, specFiles, params, outputFileMgr,
						userid ) ;
				killResults = specRemoveResults ;

			} else {

				killResults = kubernetes.specBuilder( ).remove_csap_service( serviceInstance ) ;

				if ( params.contains( "clean" ) ) {

					killResults.set( "remove-claim", kubernetes.specBuilder( ).persistentVolumeClaimDelete(
							serviceInstance ) ) ;

				}

				// assume storage is last
				var specRemoveResults = kubernetes_remove_by_specs( serviceInstance, specFiles, params, outputFileMgr,
						userid ) ;
				killResults.set( "spec-remove-results", specRemoveResults ) ;

			}

		} else {

			killResults = removeDockerResources( serviceInstance, outputFileMgr, params ) ;

		}

		// clean up any service jobs
		if ( params.contains( "clean" ) || params.contains( "cleanVolumes" ) ) {

			File workingFolder = serviceInstance.getWorkingDirectory( ) ;
			FileUtils.deleteQuietly( workingFolder ) ;
			outputFileMgr.printHeader( "'clean' specified, deleted folder: " + workingFolder.getAbsolutePath( ) ) ;

		}

		addResultsToOutput( outputFileMgr, killResults, killResults ) ;

	}

	private ObjectNode removeDockerResources (
												ServiceInstance serviceInstance ,
												OutputFileMgr outputFileMgr ,
												ArrayList<String> params )
		throws Exception {

		ObjectNode killResults ;
		killResults = dockerHelper.containerRemove(
				null,
				serviceInstance.getDockerContainerPath( ),
				true ) ;

		addResultsToOutput( outputFileMgr, killResults, killResults ) ;

		if ( ! params.contains( "skipImageRemove" )
				&& ( params.contains( "clean" ) || params.contains( "cleanVolumes" ) ) ) {

			outputFileMgr.printHeader( "Removing image: " + serviceInstance.getDockerImageName( ) ) ;

			ObjectNode removeResults = dockerHelper
					.imageRemove( false,
							null,
							serviceInstance.getDockerImageName( ) ) ;

			addResultsToOutput( outputFileMgr, null, removeResults ) ;

		}

		if ( params.contains( "cleanVolumes" ) && serviceInstance.getDockerSettings( ) != null ) {

			JsonNode volumes = serviceInstance.getDockerSettings( ).path( C7.volumes.val( ) ) ;

			try {

				volumes = jsonMapper.readTree( csapApis.application( ).resolveDefinitionVariables( volumes.toString( ),
						serviceInstance ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to add runtime configuration for volumes: {}", CSAP.buildCsapStack( e ) ) ;

			}

			if ( volumes.isArray( ) ) {

				volumes
						.forEach( volumeDef -> {

							if ( volumeDef.at( "/" + C7.create_persistent.val( ) + "/enabled" ).asBoolean(
									false ) ) {

								String hostPath = volumeDef.path( C7.volume_host_path.val( ) ).asText( "" ) ;
								String driver = volumeDef.at( "/" + C7.create_persistent.val( ) + "/driver" )
										.asText( "no-driver-specified" ) ;

								if ( StringUtils.isNotEmpty( hostPath ) && ! driver.equals( "host" ) ) {

									logger.info( "Removing volume: {}", volumeDef ) ;
									outputFileMgr.printHeader( "Removing docker local volume: " + hostPath ) ;
									ObjectNode removeResults = dockerHelper.volumeDelete( hostPath ) ;
									addResultsToOutput( outputFileMgr, null, removeResults ) ;

								} else if ( StringUtils.isNotEmpty( hostPath )
										&& ! hostPath.equals( "/" )
										&& ! hostPath.equals( csapApis.application( ).getCsapInstallFolder( )
												.getAbsolutePath( ) )
										&& ! hostPath.equals( csapApis.application( ).getCsapWorkingFolder( )
												.getAbsolutePath( ) )
										&& ! hostPath.startsWith( csapApis.application( ).getCsapInstallFolder( )
												.getAbsolutePath( ) ) ) {

									outputFileMgr.printHeader( "Removing docker host volume: " + hostPath ) ;
									String[] volumeDeleteScript = {
											"#!/bin/bash",
											"echo running root script to delete: " + hostPath,
											"rm --recursive --force " + hostPath,
											""
									} ;

									try {

										String deleteOutput = osCommandRunner.runUsingRootUser(
												"docker-volume-cleanup",
												Arrays.asList( volumeDeleteScript ) ) ;
										outputFileMgr.print( deleteOutput ) ;

									} catch ( Exception e ) {

										logger.warn( "Failed running volume script: {}", CSAP.buildCsapStack( e ) ) ;

									}

								} else {

									logger.info( "Skipping: {}", volumeDef ) ;

								}

							}

						} ) ;

			}

		}

		return killResults ;

	}

	private ObjectNode kubernetes_remove_by_specs (
													ServiceInstance serviceInstance ,
													Stream<URI> specFiles ,
													ArrayList<String> params ,
													OutputFileMgr outputFileMgr ,
													String userid ) {

		ObjectNode results ;
		results = jsonMapper.createObjectNode( ) ;

		List<URI> filePaths = specFiles

				// filter deploy only unless clean is specified
				.filter( path -> {

					if ( ( path.toString( ).contains( "deploy-only" ) ) ) {

						if ( ( params != null ) && ( params.contains( "clean" ) ) ) {

							return true ;

						} else {

							outputFileMgr.printHeader( "Info: skipping deploy-only file '" + path
									+ "' Use clean option to invoke" ) ;
							return false ;

						}

					}

					return true ;

				} )

				// reverse the order
				.collect( Collectors.collectingAndThen(
						Collectors.toCollection( ArrayList::new ), deploymentFiles -> {

							Collections.reverse( deploymentFiles ) ;
							return deploymentFiles.stream( ) ;

						} ) )

				// perform the operation
				.map( filePath -> {

					try {

						File sourceFile = new File( filePath ) ;

						if ( ! sourceFile.exists( ) ) {

							outputFileMgr.printHeader( "Error: unable to locate deployment file : " + sourceFile
									.getAbsolutePath( ) ) ;
							results.put( filePath.toString( ), "missing" ) ;

						} else {

							var deploymentFile = buildDeplomentFile(
									serviceInstance,
									sourceFile,
									jsonMapper.createObjectNode( ) ) ;

							if ( sourceFile.getName( ).endsWith( ".sh" ) ) {

								//
								// any sh file - but specifically helm-deploy.sh
								//

								outputFileMgr.printHeader( "invoking script with remove " + deploymentFile
										.getAbsolutePath( ) ) ;

								var output = csapApis.osManager( ).kubernetesShell(
										"remove", deploymentFile,
										C7.response_shell ) ;

								outputFileMgr.printHeader( output.path( C7.response_shell.val( ) )
										.asText( ) ) ;

							} else if ( sourceFile.getName( ).startsWith( "helm" ) ) {
								// just written out
								// handles helm-values.yaml files

							} else {

								var preservedYamlFile = csapApis.application( ).createYamlFile(
										"-delete-" + sourceFile.getName( ) + "-",
										Application.readFile( deploymentFile ),
										serviceInstance.getName( ) ) ;

								var command = "delete -f " + preservedYamlFile.getAbsolutePath( ) ;

								// if ( (params != null) && (params.contains( "clean" )) ) {
								// command += " --grace-period=0 --force " ;
								// }

								outputFileMgr.printHeader( command ) ;
								ObjectNode output = csapApis.osManager( ).kubernetesCli( command,
										C7.response_shell ) ;
								outputFileMgr.printHeader( output.path( C7.response_shell.val( ) )
										.asText( ) ) ;
								results.put( filePath.toString( ), "completed" ) ;

							}

						}

					} catch ( IOException e ) {

						logger.warn( "Failed to delete: {}", CSAP.buildCsapStack( e ) ) ;

					}

					return filePath ;

				} )
				.collect( Collectors.toList( ) ) ;
		logger.info( "Deploy spec remove on : {}", filePaths ) ;
		return results ;

	}

	public Stream<URI> buildSpecificationFileArray (
														ServiceInstance serviceInstance ,
														JsonNode specificationCsapPaths ) {

		if ( specificationCsapPaths.isArray( ) ) {

			return CSAP.jsonStream( specificationCsapPaths )

					.map( JsonNode::asText )

					.filter( StringUtils::isNotEmpty )

					.map( specificationCsapPath -> csapApis.application( ).resolveDefinitionVariables(
							specificationCsapPath,
							serviceInstance ) )

					.map( specificationCsapPath -> {

						var resolvedResourcePath = new File( specificationCsapPath ).toURI( ) ;

						try {

							if ( specificationCsapPath.contains( CsapConstants.SEARCH_RESOURCES ) ) {

								var serviceName = serviceInstance.getName( ) ;

								// Always use current application resource if it exists
								var definitionPath = "" ;
								var applicationResourceURI = performSearchForResources(
										serviceName,
										specificationCsapPath,
										definitionPath ) ;
								var applicationResourceSpec = new File( applicationResourceURI ) ;

								logger.debug( "applicationResourceSpec: {}", applicationResourceSpec ) ;

								if ( applicationResourceSpec.exists( ) ) {

									resolvedResourcePath = applicationResourceURI ;

								} else {

									// If not found in application definition - check in template folder
									var templatePath = csapApis.application( ).getActiveProject( )
											.find_service_template_path(
													serviceName ) ;
									definitionPath = "/" + templatePath ;
									var templateResourceURI = performSearchForResources( serviceName,
											specificationCsapPath, definitionPath ) ;

									applicationResourceSpec = new File( templateResourceURI ) ;

									if ( applicationResourceSpec.exists( ) ) {

										resolvedResourcePath = templateResourceURI ;

									}

								}

							}

						} catch ( Exception e ) {

							logger.warn( "{}", CSAP.buildCsapStack( e ) ) ;

						}

						return resolvedResourcePath ;

					} ) ;

		}

		List<URI> emptyList = List.of( ) ;
		return emptyList.stream( ) ;

	}

	private URI performSearchForResources ( String serviceName , String specificationPath , String definitionPath )
		throws URISyntaxException {

		var resourceFolderUri = ServiceResources.serviceResourceTemplateFolder( definitionPath, serviceName ).toURI( )
				.toString( ) ;

		// default is root of resource folder
		var specificationLocationOnDisk = specificationPath.replaceAll(
				Pattern.quote( CsapConstants.SEARCH_RESOURCES ),
				resourceFolderUri ) ;

		// check for common folder
		var commonSpec = specificationPath.replaceAll(
				Pattern.quote( CsapConstants.SEARCH_RESOURCES ),
				resourceFolderUri + "common" + "/" ) ;

		var commonResourceSpec = new File( new URI( commonSpec ) ) ;
		logger.debug( "exists: {}, commonSpec: {} commonResourceSpec: {}, ",
				commonResourceSpec.exists( ), commonSpec, commonResourceSpec.getAbsolutePath( ) ) ;

		if ( commonResourceSpec.exists( ) ) {

			specificationLocationOnDisk = commonSpec ;

		}

		//
		// check for base environments
		//

		var imports = csapApis.application( ).getRootProject( ).getImports( csapApis.application( )
				.getCsapHostEnvironmentName( ) ) ;

		if ( imports.isArray( ) ) {

			var baseEnvMatches = CSAP.jsonStream( imports )
					.map( JsonNode::asText )
					.map( importEnvName -> {

						var baseEnvSpec = specificationPath.replaceAll(
								Pattern.quote( CsapConstants.SEARCH_RESOURCES ),
								resourceFolderUri + importEnvName + "/" ) ;

						return baseEnvSpec ;

					} )
					.filter( baseEnvSpec -> {

						try {

							var baseEnvFile = new File( new URI( baseEnvSpec ) ) ;
							logger.debug( "lifecycleSpec: {} lifecycleFile: {}", baseEnvSpec, baseEnvFile
									.getAbsolutePath( ) ) ;

							if ( baseEnvFile.exists( ) ) {

								return true ;

							}

						} catch ( Exception e ) {

							logger.info( "Failed to process path: {}", baseEnvSpec ) ;

						}

						return false ;

					} )
					.collect( Collectors.toList( ) ) ;

			if ( baseEnvMatches.size( ) > 0 ) {

				specificationLocationOnDisk = baseEnvMatches.get( baseEnvMatches.size( ) - 1 ) ;
				logger.debug( CSAP.buildDescription( "base env matches found",
						"baseEnvMatches", baseEnvMatches,
						"specificationLocationOnDisk", specificationLocationOnDisk ) ) ;

			}

		}

		//
		// check for current environment
		//
		var environmentSpec = specificationPath.replaceAll(
				Pattern.quote( CsapConstants.SEARCH_RESOURCES ),
				resourceFolderUri + csapApis.application( ).getCsapHostEnvironmentName( ) + "/" ) ;
		var environmentFile = new File( new URI( environmentSpec ) ) ;
		logger.debug( "lifecycleSpec: {} lifecycleFile: {}", environmentSpec, environmentFile.getAbsolutePath( ) ) ;

		if ( environmentFile.exists( ) ) {

			specificationLocationOnDisk = environmentSpec ;

		}

		return new URI( specificationLocationOnDisk ) ;

	}

	private void addResultsToOutput (
										OutputFileMgr outputFileMgr ,
										ObjectNode results ,
										JsonNode commandResults ) {

		if ( commandResults.has( C7.errorReason.val( ) ) ) {

			outputFileMgr.print( C7.error.val( ) + ":" + commandResults.get( C7.error.val( ) )
					.asText( ) ) ;
			outputFileMgr.print( commandResults.get( C7.errorReason.val( ) ).asText( ) ) ;

			if ( results != commandResults ) {

				outputFileMgr.print( "\n\n Details: \n" + CSAP.jsonPrint( results ) ) ;

			}

		} else {

			if ( results != null ) {

				outputFileMgr.print( CSAP.jsonPrint( results ) ) ;

			}

		}

	}

	public File createVersionFile (
									File versionFile ,
									String version ,
									String userid ,
									String description ) {

		String foundTime = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d" ) ) ;

		if ( version == null ) {

			version = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm" ) ) ;

		}

		List<String> lines = Arrays.asList(
				"Deployment Notes",
				"<version>" + version + "</version>",
				description + " by: " + userid + " at " + foundTime ) ;

		try {

			Files.write( versionFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;

		} catch ( IOException ex ) {

			logger.error( "Failed creating version file: {}", CSAP.buildCsapStack( ex ) ) ;

		}

		return versionFile ;

	}

	// only a single thread doing deploys else need synchronized
	public boolean deployService (
									ServiceInstance serviceInstance ,
									String primaryHost ,
									String deployId ,
									String requestedByUserid ,
									String scmUserid ,
									String scmPass ,
									String repoPass ,
									String scmBranch ,
									String deployConfiguration ,
									String scmCommand ,
									String targetScpHosts ,
									String hotDeploy ,
									String parameters ,
									OutputFileMgr outputFileMgr )
		throws Exception {

		var deployInfo = new StringBuilder( "Deployment" ) ;
		deployInfo.append( CSAP.padLine( "service" ) + serviceInstance.getName( ) ) ;
		deployInfo.append( CSAP.padLine( "user" ) + "api: " + requestedByUserid + " request: " + scmUserid ) ;
		deployInfo.append( CSAP.padLine( "scmBranch" ) + scmBranch ) ;
		deployInfo.append( CSAP.padLine( "scmCommand" ) + scmCommand ) ;
		deployInfo.append( CSAP.padLine( "hotDeploy" ) + hotDeploy ) ;
		deployInfo.append( CSAP.padLine( "deployId" ) + deployId ) ;
		deployInfo.append( CSAP.padLine( "primaryHost" ) + primaryHost ) ;
		deployInfo.append( CSAP.padLine( "targetScpHosts" ) + targetScpHosts ) ;
		deployInfo.append( CSAP.padLine( "parameters" ) + parameters ) ;
		deployInfo.append( CSAP.padLine( "deployConfiguration" ) + deployConfiguration ) ;

		logger.info( CsapApplication.header( deployInfo.toString( ) ) ) ;

		File svcDirOnHost = csapApis.application( ).getCsapInstallFolder( ) ;
		File workingDir = new File( svcDirOnHost.getAbsolutePath( ) ) ;

		//
		// Prechecks
		//
		if ( serviceInstance.is_cluster_kubernetes( ) ) {

			if ( ! csapApis.isKubernetesInstalledAndActive( ) ) {

				outputFileMgr.printHeader( "Error: Kubernetes is not running - Sleeping 10 seconds and trying again" ) ;

				csapApis.osManager( ).resetAllCaches( ) ;

				TimeUnit.SECONDS.sleep( 10 ) ;

				if ( ! csapApis.isKubernetesInstalledAndActive( ) ) {

					outputFileMgr.printHeader( "Error: Kubernetes is not running - Aborting deployment" ) ;
					return false ;

				}

			}

			JsonNode report = kubernetes.buildSummaryReport( "all" ) ;

			if ( ! report.path( K8.heartbeat.val( ) ).asBoolean( false ) ) {

				logger.info(
						"Error: Unable to connect to kubernetes - Sleeping 10 seconds and trying again, full report: {}",
						CSAP.jsonPrint( report ) ) ;
				outputFileMgr.printHeader(
						"Error: Unable to connect to kubernetes - Sleeping 10 seconds and trying again" ) ;

				TimeUnit.SECONDS.sleep( 10 ) ;
				report = kubernetes.buildSummaryReport( "all" ) ;

				if ( ! report.path( K8.heartbeat.val( ) ).asBoolean( false ) ) {

					logger.info( "Warning: no active pods in cluster, full report: {}", CSAP.jsonPrint( report ) ) ;
					outputFileMgr.printHeader( "Error: Unable to connect to kubernetes - deployment aborted" ) ;
					return false ;

				}

			}

		}

		//
		// Step 1 - Check out the code if a source build is requested: either
		// cvs or svn
		//

		boolean isSourceCodeOk = true ;

		if ( StringUtils.isEmpty( deployConfiguration )
				&& ! serviceInstance.is_cluster_kubernetes( ) ) {
			// only login on source build

			logger.debug( "scm: {}", serviceInstance.getScm( ) ) ;

			if ( primaryHost == null || primaryHost.equals( ( csapApis.application( ).getCsapHostName( ) ) ) ) {

				isSourceCodeOk = checkoutFromSourceControl(
						serviceInstance,
						scmUserid,
						scmPass,
						scmBranch,
						outputFileMgr,
						workingDir ) ;

			} else {

				logger.info( "Skipping source checkout on secondary node" ) ;
				isSourceCodeOk = true ;

			}

		}

		csapApis.application().setCachedRepo( scmUserid, repoPass );

		//
		// Step 2: Get build params for rebuild script
		//
		var params = buildDeployParameters(
				serviceInstance, scmUserid, scmBranch,
				deployConfiguration, scmCommand, hotDeploy ) ;

		outputFileMgr.printHeader( "Deployment on host: " + csapApis.application( ).getCsapHostName( ) ) ;

		//

		Map<String, String> deployEnvironmentVariables = new HashMap<>( ) ;
		String waitForPrimaryFile = "none" ;
		File deployCompleteFile = new File(
				getSyncLocation( ),
				serviceInstance.getName( ) + "." + primaryHost + "." + deployId ) ;

		if ( ! serviceInstance.is_docker_server( ) &&
				primaryHost != null ) {
			// synchronize state using file system. Corner cases can occur - but
			// are highly unusual

			if ( ! primaryHost.equals( ( csapApis.application( ).getCsapHostName( ) ) ) ) {

				// in case file has been left on FS by aborted requests
				// It is possible that sync occurs quicker then requests - but
				// also highly unlikely
				waitForPrimaryFile = deployCompleteFile.getCanonicalPath( ) ;

			} else {

				logger.info( "Creating: {}", deployCompleteFile ) ;
				deployCompleteFile.getParentFile( ).mkdirs( ) ;
				deployCompleteFile.createNewFile( ) ;

			}

			deployEnvironmentVariables.put( "waitForPrimaryFile", waitForPrimaryFile ) ;

		}

		deployEnvironmentVariables.put( "buildSubDir", "/" ) ;

		if ( serviceInstance.getScmBuildLocation( ).length( ) > 0 ) {

			deployEnvironmentVariables.put( "buildSubDir", serviceInstance.getScmBuildLocation( ) ) ;

		}

		//
		// Step 3: Trigger the deployment
		//
		String buildResults = "" ;

		if ( isSourceCodeOk ) {

			String results = csapApis.osManager( )
					.getJobRunner( )
					.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.preDeploy, outputFileMgr
							.getBufferedWriter( ) ) ;

			if ( results.contains( "CSAP_DEPLOY_ABORT" ) ) {

				outputFileMgr.printHeader( "Found CSAP_DEPLOY_ABORT, exiting with error" ) ;
				isSourceCodeOk = false ;

			}

		}

		if ( isSourceCodeOk ) {

			if ( serviceInstance.is_docker_server( ) ) {

				buildResults = deployContainerService(
						serviceInstance,
						outputFileMgr,
						deployConfiguration,
						requestedByUserid) ;

			} else {

				//
				// Updated gradle and/or maven settings file....
				// add scmUserid

				if ( StringUtils.isNotEmpty( scmUserid )) {
					deployEnvironmentVariables.put( "csapRepoUser", VersionControl.stripBrowserConvenienceSuffix( scmUserid ) );
					if ( StringUtils.isNotEmpty( repoPass )) {
						deployEnvironmentVariables.put( "csapRepoPass", repoPass ) ;
					}
				}

				logger.info( CSAP.buildDescription("deployEnvironmentVariables", deployEnvironmentVariables) ) ;

				buildResults = runShellCommand(
						requestedByUserid, DEPLOY_SCRIPT, serviceInstance, params,
						deployEnvironmentVariables, parameters,
						null,
						outputFileMgr.getBufferedWriter( ) ) ;

			}

			csapApis.osManager( )
					.getJobRunner( )
					.runJobUsingEvent( serviceInstance, ServiceJobRunner.Event.postDeploy, outputFileMgr
							.getBufferedWriter( ) ) ;

			//

			if ( serviceInstance.is_cluster_kubernetes( ) ) {

				// k8s gets queued...so rescan all
				csapApis.application( ).markServicesForFileSystemScan( true ) ;

			} else {

				serviceInstance.setFileSystemScanRequired( true ) ;

			}

		}

		if ( primaryHost != null &&
				( ! primaryHost.equals( ( csapApis.application( ).getCsapHostName( ) ) ) ) ) {

			logger.info( "Completed deployment using sync from: {}", primaryHost ) ;
			return isSourceCodeOk ;

		}

		// Test data for desktop
		if ( Application.isRunningOnDesktop( ) &&
				! serviceInstance.is_docker_server( ) ) {

			outputFileMgr.printHeader( "Desktop test" ) ;
			outputFileMgr.printImmediate( csapApis.application( ).check_for_stub( "", "linux/buildResults.txt" ) ) ;

		}

		boolean isBuildSuccessful = is_errors_in_output( buildResults ) ;

		// Update build status file
		List<String> lines = Arrays.asList( "source: " + csapApis.application( ).getCsapHostName( )
				+ " deploy passed" ) ;

		if ( ! isBuildSuccessful ) {

			lines = Arrays.asList( "source: " + csapApis.application( ).getCsapHostName( ) + " deploy failed" ) ;

		} else {

			if ( serviceInstance.is_cluster_kubernetes( ) && serviceInstance.isKubernetesMaster( ) ) {

				create_k8s_last_deploy_file( serviceInstance ) ;

			}

		}

		try {

			deployCompleteFile.getParentFile( ).mkdirs( ) ;
			Files.write( deployCompleteFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;

		} catch ( Exception ex ) {

			logger.error( "Failed creating {}, {}", deployCompleteFile, CSAP.buildCsapStack( ex ) ) ;

		}

		// addOtherHostsParam(svcName, params);
		if ( targetScpHosts != null && targetScpHosts.trim( ).length( ) > 0 ) {

			pushFilesToOtherHosts(
					requestedByUserid, targetScpHosts, serviceInstance,
					outputFileMgr.getBufferedWriter( ),
					isBuildSuccessful,
					deployCompleteFile ) ;

		}

		return isSourceCodeOk ;

	}

	private boolean is_errors_in_output ( String buildResults ) {

		// need to add in scp commands here
		String last400CharactersFromBuild = buildResults ;

		if ( buildResults.length( ) > 500 ) {

			last400CharactersFromBuild = buildResults.substring( buildResults.length( ) - 400 ) ;

		}

		logger.info( "deploy results (last 400 characters): {}", CsapApplication.header(
				last400CharactersFromBuild ) ) ;

		boolean isBuildSuccessful = last400CharactersFromBuild.contains( BUILD_SUCCESS ) ;
		return isBuildSuccessful ;

	}

	private ArrayList<String> buildDeployParameters (
														ServiceInstance serviceInstance ,
														String scmUserid ,
														String scmBranch ,
														String mavenDeployArtifact ,
														String scmCommand ,
														String hotDeploy ) {

		ArrayList<String> params = new ArrayList<String>( ) ;
		params.add( "-scmUser" ) ;
		params.add( scmUserid ) ;
		params.add( "-scmBranch" ) ;
		params.add( scmBranch ) ;

		if ( hotDeploy != null ) {

			params.add( "-hotDeploy" ) ;

		}

		if ( mavenDeployArtifact != null ) {

			params.add( "-mavenCommand" ) ;

			if ( mavenDeployArtifact.startsWith( MAVEN_DEFAULT_BUILD ) ) {
				// Hook for deployments of multiple artifacts

				if ( serviceInstance != null ) {

					params.add( serviceInstance.getMavenId( ) ) ;

				} else {

					params.add( "serviceWasNull" ) ;

				}

			} else {

				// Hook for deployScripts not like spaces
				params.add( mavenDeployArtifact.replaceAll( " ", "__" ) ) ;

			}

		} else {

			// Source build Option
			params.add( "-mavenCommand" ) ;
			if ( StringUtils.isEmpty( scmCommand  )) {
				scmCommand=" " ;
			}
			// Hook for deployScripts not like spaces
			params.add( scmCommand.replaceAll( " ", "__" ) ) ;

		}

		if ( serviceInstance.getMavenSecondary( ) != null ) {

			params.add( "-secondary" ) ;
			params.add( serviceInstance.getMavenSecondary( ) ) ;

		}

		return params ;

	}

	private boolean checkoutFromSourceControl (
												ServiceInstance instanceConfig ,
												String scmUserid ,
												String scmPass ,
												String scmBranch ,
												OutputFileMgr outputFileMgr ,
												File workingDir ) {

		try {

			sourceControlManager.checkOutFolder(
					scmUserid, scmPass, scmBranch, instanceConfig.getServiceName_Port( ),
					instanceConfig, outputFileMgr.getBufferedWriter( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to do source checkout: {}, {} ",
					instanceConfig.getScmLocation( ),
					CSAP.buildCsapStack( e ) ) ;

			outputFileMgr
					.printImmediate( CsapConstants.CONFIG_PARSE_ERROR
							+ "GIT Failure: Verify password and target is correct, and that url exists\n"
							+ instanceConfig.getScmLocation( ) + "\n Exception: " + e ) ;

			if ( e.toString( ).indexOf( "is already a working copy for a different URL" ) != -1 ) {

				File serviceBuildFolder = csapApis.application( ).getCsapBuildFolder( instanceConfig
						.getServiceName_Port( ) ) ;
				outputFileMgr
						.printImmediate( "Blowing away previous build folder, try again:"
								+ serviceBuildFolder ) ;
				FileUtils.deleteQuietly( serviceBuildFolder ) ;

			}

			return false ;

		}

		return true ;

	}


	/**
	 *
	 * @param userid
	 * @param targetScpHosts
	 * @param serviceInstance
	 * @param outputWriter
	 * @param isBuildSuccessful
	 * @param deployCompleteFile
	 * @throws IOException
	 */
	private void pushFilesToOtherHosts (
											String userid ,
											String targetScpHosts ,
											ServiceInstance serviceInstance ,
											BufferedWriter outputWriter ,
											boolean isBuildSuccessful ,
											File deployCompleteFile )
		throws IOException {

		String[] hostsArray = targetScpHosts.trim( ).split( " " ) ;

		List<String> hostList = new ArrayList<String>( Arrays.asList( hostsArray ) ) ;
		hostList.remove( csapApis.application( ).getCsapHostName( ) ) ;

		if ( hostList.size( ) == 0 ) {

			logger.info( "No other hosts specified" ) ;
			return ;

		}

		// StringBuffer buf = new StringBuffer("");

		// Trigger reload on otherhosts...
		// for (String host : hostsArray) {

		// // do not need current host
		// if (host.equals(csapApis.application().getCsapHostName()))
		// continue;
		// in a multi-service deploy, only push if host contains
		// service
		if ( serviceInstance == null ) {

			logger.error( "Warning: service was not found in Application.json definition" ) ;
			return ;

		}

		File deployFile = csapApis.application( ).getServiceDeployFile( serviceInstance ) ;
		File deployVersionFile = csapApis.application( ).getDeployVersionFile( serviceInstance ) ;

		logger.debug( "Checking for deployment Files: {}", deployFile.getAbsolutePath( ) ) ;

		if ( isBuildSuccessful ) {

			TransferManager transferManager = new TransferManager( csapApis, 120, outputWriter ) ;

			if ( deployFile.exists( ) ) {

				transferManager.httpCopyViaCsAgent( userid, deployFile,
						Application.CSAP_PACKAGES_TOKEN, hostList ) ;

			} else {

				logger.warn( "Did not find deployment file: {}", deployFile.getAbsolutePath( ) ) ;

			}

			transferManager.httpCopyViaCsAgent( userid, deployVersionFile,
					Application.CSAP_PACKAGES_TOKEN, hostList ) ;

			File secondaryFolder = new File( csapApis.application( ).getCsapPackageFolder( ),
					serviceInstance.getName( ) + ".secondary" ) ;

			if ( secondaryFolder.exists( ) && secondaryFolder.isDirectory( ) ) {

				syncToOtherHosts( userid, hostList, secondaryFolder.getAbsolutePath( ),
						Application.CSAP_PACKAGES_TOKEN + serviceInstance.getName( ) + ".secondary",
						csapApis.application( ).getAgentRunUser( ),
						userid, outputWriter ) ;

			}

			logger.debug( "sending complete file to remote hosts" ) ;

			String transResults = transferManager.waitForComplete( ) ;

			logger.info( "Transfer results have been added $PROCESSING/{}.deploy.log", serviceInstance.getName( ) ) ;

			if ( transResults.contains( CsapConstants.CONFIG_PARSE_ERROR ) ) {

				logger.warn( "Found 1 or more errors in transfer results" ) ;
				outputWriter.write( CsapApplication.header( "Found Errors, review is required" ) ) ;
				outputWriter.write( transResults ) ;

			}

		}

		TransferManager deployCompleteManager = new TransferManager( csapApis, 30, null ) ;
		deployCompleteManager.httpCopyViaCsAgent( userid,
				deployCompleteFile,
				Application.CSAP_SAVED_TOKEN + PACKAGE_SYNC, hostList ) ;

		String transferResults = deployCompleteManager.waitForComplete( ) ;

		logger.info( "Deployment sync completed", transferResults ) ;

		if ( transferResults.contains( CsapConstants.CONFIG_PARSE_ERROR ) ) {

			logger.warn( "Found 1 or more errors in transfer results" ) ;
			outputWriter.write( "\n WARNING:  Found 1 or more errors in transfer results: " + hostList + " =====\n" ) ;

		}

		// avoid writing sync stats so errors are more obvious
		// outputWriter.write( transResults );

	}

	public String syncToOtherHosts (
										String userid ,
										List<String> hostList ,
										String locationToZip ,
										String extractDir ,
										String chownUserid ,
										String auditUser ,
										BufferedWriter outputWriter )
		throws IOException {

		logger.debug( "auditUser: {}, locationToZip: {}, extractDir: {}, chownUserid: {}, hostList: {}",
				auditUser, locationToZip, extractDir, chownUserid, hostList ) ;

		if ( hostList != null && hostList.contains( csapApis.application( ).getCsapHostName( ) ) ) {

			logger.debug( "Removing : {}", csapApis.application( ).getCsapHostName( ) ) ;
			// always remove current host
			hostList.remove( csapApis.application( ).getCsapHostName( ) ) ;

		}

		if ( hostList == null || hostList.size( ) == 0 ) {

			return "No Additional Synchronization required" ;

		}

		TransferManager transferManager = new TransferManager( csapApis, 120, outputWriter ) ;

		File zipLocation = new File( locationToZip ) ;

		String result = "Specified Location does not exist: " + locationToZip + " on host: "
				+ csapApis.application( ).getCsapHostName( ) ;

		if ( zipLocation.exists( ) ) {

			// logger.info("******* extractDir: "+ extractDir + " full path: " +
			// targetFolder.getAbsolutePath());
			// targetFolder.getAbsolutePath()
			transferManager.httpCopyViaCsAgent( userid, zipLocation,
					extractDir, hostList, chownUserid ) ;

			result = transferManager.waitForComplete( ) ;

		}

		logger.debug( "Result: {}", result ) ;

		return result ;

	}

	private static String CSAP_AGENT_AUTO_START_COMPLETE = CsapConstants.AGENT_NAME + "-auto-start-complete" ;

	// @Deprecated
	// public ObjectNode remoteAdminUsingUserCredentials (
	// String[] hosts, String commandUrl,
	// MultiValueMap<String, String> urlVariables,
	// HttpServletRequest request ) {
	//
	// return remoteAdminExecute( Arrays.asList( hosts ), commandUrl,
	// urlVariables, extractSsoCookie( request ) );
	//
	// }

	public ObjectNode remoteAgentsUsingUserCredentials (
															List<String> hosts ,
															String commandUrl ,
															MultiValueMap<String, String> urlVariables ,
															HttpServletRequest request ) {

		return remoteHttp( hosts, commandUrl, urlVariables, extractSsoCookie( request ), HttpMethod.POST ) ;

	}

	public final static String STATELESS = "NO_COOKIE" ;

	public ObjectNode remoteAgentsGet (
										List<String> hosts ,
										String commandUrl ,
										MultiValueMap<String, String> urlVariables ) {

		return remoteHttp( hosts, commandUrl, urlVariables, STATELESS, HttpMethod.GET ) ;

	}

	public ObjectNode remoteAgentsStateless (
												List<String> hosts ,
												String commandUrl ,
												MultiValueMap<String, String> urlVariables ) {

//		return remoteHttp( hosts, commandUrl, urlVariables, STATELESS, HttpMethod.POST ) ;
		return remoteHttp( hosts, commandUrl, urlVariables, STATELESS, HttpMethod.GET ) ;

	}

	public ObjectNode remoteAgentsApi (
										String apiUser ,
										String apiPass ,
										List<String> hosts ,
										String commandUrl ,
										MultiValueMap<String, String> urlVariables ) {

		urlVariables.set( SpringAuthCachingFilter.USERID, apiUser ) ;
		urlVariables.set( SpringAuthCachingFilter.PASSWORD, apiPass ) ;

		return remoteHttp( hosts, commandUrl, urlVariables, STATELESS, HttpMethod.POST ) ;

	}

	@Autowired ( required = false )
	private CsapOauth2SecurityConfiguration csapOauthConfig ;

	private ObjectNode remoteHttp (
									List<String> hosts ,
									String commandUrl ,
									MultiValueMap<String, String> urlVariables ,
									String ssoCookieStringForHeader ,
									HttpMethod httpMethod ) {

		var httpRequestReport = jsonMapper.createObjectNode( ) ;
		// API calls do not need sso token.

		logger.debug( "hosts: {} ssoCookieStringForHeader: {}",
				hosts,
				ssoCookieStringForHeader ) ;

		if ( hosts == null || hosts.size( ) == 0 ) {

			httpRequestReport.put( CsapConstants.CONFIG_PARSE_ERROR, "One or more hosts required" ) ;
			return httpRequestReport ;

		}

		// hook to prune null params and get max timeout
		var maxTimeoutInMs = 25000 ;
		var timeoutSecondsForAnonymousRequests = 3 ;
		var keysToPrune = new ArrayList<String>( ) ;

		for ( var key : urlVariables.keySet( ) ) {

			if ( urlVariables.get( key ).get( 0 ) == null ) {

				keysToPrune.add( key ) ;
				continue ;

			}

			if ( key.equals( CsapConstants.SERVICE_NOPORT_PARAM ) ) {

				for ( var serviceName : urlVariables.get( key ) ) {

					timeoutSecondsForAnonymousRequests = csapApis.application( ).getMaxDeploySecondsForService(
							serviceName ) ;

					int serviceMaxMs = csapApis.application( ).getMaxDeploySecondsForService( serviceName ) * 1000 ;

					if ( serviceMaxMs > maxTimeoutInMs ) {

						maxTimeoutInMs = serviceMaxMs ;

					}

				}

			}

			if ( key.equals( CsapConstants.SERVICE_PORT_PARAM ) ) {

				for ( var serviceName_port : urlVariables.get( key ) ) {

					// Use the longest time configured
					timeoutSecondsForAnonymousRequests = csapApis.application( ).getMaxDeploySecondsForService(
							serviceName_port ) ;

					int serviceMaxMs = csapApis.application( ).getMaxDeploySecondsForService( serviceName_port )
							* 1000 ;

					if ( serviceMaxMs > maxTimeoutInMs ) {

						maxTimeoutInMs = serviceMaxMs ;

					}

				}

			}

		}

		logger.debug( "timeoutSecondsForNonSsl: {}, variables: {}", timeoutSecondsForAnonymousRequests, urlVariables ) ;

		if ( timeoutSecondsForAnonymousRequests > 60 ) {

			logger.debug( "Maxing connection time to 60 seconds" ) ;
			timeoutSecondsForAnonymousRequests = 60 ;

		}

		for ( String key : keysToPrune ) {

			urlVariables.remove( key ) ;

		}

		var agentRestTemplate = csapApis.application( ).getAgentPooledConnection( 1,
				timeoutSecondsForAnonymousRequests ) ;
		String connectionType = "pooled" ;

		if ( ! ssoCookieStringForHeader.equals( STATELESS ) ) {

			//
			// ssoCookie ensures we have record if initiating userid, and provides extended
			// timeout
			//

			agentRestTemplate = csapApis.application( ).getAgentPooledConnection( 1, maxTimeoutInMs ) ;

//			connectionType = "transient" ;
//			
//			SsoRequestFactory simpleClientRequestFactory = new SsoRequestFactory(
//					ssoCookieStringForHeader, maxTimeoutInMs ) ;
//
//			restTemplate = new RestTemplate( simpleClientRequestFactory ) ;

			if ( csapOauthConfig != null ) {

				logger.debug( "Updating web sso" ) ;
				csapOauthConfig.addWebSso( agentRestTemplate ) ;

			} else {

				agentRestTemplate.setInterceptors(
						Collections.singletonList( (
														request ,
														body ,
														execution ) -> {

							request
									.getHeaders( )
									.add( "Cookie", ssoCookieStringForHeader ) ;

							return execution.execute( request, body ) ;

						} ) ) ;

			}

		}

		var allTimer = csapApis.metrics( ).startTimer( ) ;

		for ( String host : hosts ) {

			if ( Application.isRunningOnDesktop( ) && host.equals( "localhost" ) ) {

				httpRequestReport.put( host, "Skipping host because desktop detected" ) ;
				continue ;

			}

			String url = csapApis.application( ).getAgentUrl( host, commandUrl, true ) ;
			var hostTimer = csapApis.metrics( ).startTimer( ) ;

			try {

				urlVariables.add( CsapConstants.HOST_PARAM, host ) ;

				logger.debug( "Executing remote {} command: {}, params: {} ", httpMethod, url, urlVariables ) ;

				ResponseEntity<String> response ;

				if ( httpMethod == HttpMethod.POST ) {

					response = agentRestTemplate.postForEntity( url, urlVariables,
							String.class ) ;

				} else {

					UriComponentsBuilder builder = UriComponentsBuilder
							.fromUriString( url ) ;

					for ( var param : urlVariables.entrySet( ) ) {

						builder.queryParam( param.getKey( ), param.getValue( ) ) ;

					}

					logger.debug( "HttpGet: {}", builder.toUriString( ) ) ;

					response = agentRestTemplate.getForEntity( builder.toUriString( ), String.class ) ;

				}

				var timeNanos = csapApis.metrics( ).stopTimer( hostTimer,
						"admin.remote.http.command." + host ) ;
				JsonNode remoteCommandResponse = null ;

				if ( response.getStatusCode( ) == HttpStatus.OK ) {

					var type = response.getHeaders( ).getContentType( ) ;

					if ( type != null && type.toString( ).contains( "html" ) ) {

						httpRequestReport.put( host, response.getBody( ) ) ;

					} else {

						remoteCommandResponse = (JsonNode) jsonMapper.readTree( response.getBody( ) ) ;
						httpRequestReport.set( host, remoteCommandResponse ) ;

					}

				} else {

					logger.warn(
							"{} : Status: '{}' response from command: {} ,  Time taken: {}, \n urlVariables: {} \n response: {}",
							host, response.getStatusCode( ), url,
							TimeUnit.NANOSECONDS.toSeconds( timeNanos ), urlVariables,
							response ) ;

					httpRequestReport.put( host, CsapConstants.CONFIG_PARSE_ERROR ) ;
					httpRequestReport.set( host + "_reason", jsonMapper.convertValue( response, ObjectNode.class ) ) ;

				}

				logger.debug( "{} : command: {} ,  Time taken: {}, \n urlVariables: {} \n restResult: {}",
						host, url, TimeUnit.NANOSECONDS.toSeconds( timeNanos ), urlVariables, remoteCommandResponse ) ;

			} catch ( Exception e ) {

				logger.warn( "Exception on url: {}, connection: {}, time out: {} (s) variables: {}, {}", url,
						connectionType,
						timeoutSecondsForAnonymousRequests, urlVariables,
						CSAP.buildCsapStack( e ) ) ;
				logger.debug( "Failed remote connection", e ) ;

				logger.info( "converters: {}", agentRestTemplate.getMessageConverters( ) ) ;

				httpRequestReport.put( host, CsapConstants.CONFIG_PARSE_ERROR + " Connection Failure"
						+ "\n\n Resource: " + url + "\n\nMessage: "
						+ e.getMessage( )
						+ "\n\nIf caused by timeout, consider extending deploy timeout in the application definition" ) ;

			}

		}

		var timeNanos = csapApis.metrics( ).stopTimer( allTimer, "admin.remote.http.command" ) ;

		if ( ! commandUrl.contains( ServiceRequests.DEPLOY_PROGRESS_URL )
				&& ! commandUrl.contains( AgentApi.LOG_CHANGES )
				&& ! commandUrl.contains( ApplicationBrowser.POD_LOG_URL ) ) {

			logger.info( CSAP.buildDescription(
					"Remote Command Completed",
					"duration", CSAP.autoFormatNanos( timeNanos ),
					"commandUrl", commandUrl,
					"hosts", hosts,
					"connectionType", connectionType ) ) ;

		} else {

			logger.debug( "********** Completed: {} ,  on host(s): {}, time: {}, connectionType: {} ",
					commandUrl,
					hosts,
					TimeUnit.NANOSECONDS.toSeconds( timeNanos ),
					connectionType ) ;

		}

		return httpRequestReport ;

	}

	private String extractSsoCookie ( HttpServletRequest request ) {

		String ssoCookieStringForHeader = "csapApis.application().getSecuritySettings().getCookie().getName()"
				+ "=NotUsed" ;

		if ( csapOauthConfig != null ) {

			ssoCookieStringForHeader = "csapApis.application().getSecuritySettings().getCookie().getName()" + "=oauth" ;

		} else if ( request != null && csapApis.application( ).getSecuritySettings( ) != null ) {

			try {

				ssoCookieStringForHeader = csapApis.application( ).getSecuritySettings( ).getCookie( ).getName( ) + "="
						+ WebUtils.getCookie(
								request,
								csapApis.application( ).getSecuritySettings( )
										.getCookie( )
										.getName( ) )
								.getValue( ) ;

			} catch ( Exception e ) {

				logger.error( "Failed finding sso cookie: {}, reason: {}", ssoCookieStringForHeader, CSAP
						.buildCsapStack( e ) ) ;

			}

		}

		return ssoCookieStringForHeader ;

	}

}
