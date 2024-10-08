package org.csap.agent.integration.linux;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.io.IOException ;

import jakarta.inject.Inject ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.C7 ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.AfterAll ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@CsapBareTest.Agent_Full
public class OsManager_Agent_Context {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	File csapApplicationDefinition = new File(
			OsManager_Agent_Context.class.getResource( "full_app.json" ).getPath( ) ) ;

	@BeforeAll
	public void setUp ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;

		csapApp.metricManager( ).setSuspendCollection( false ) ;

		assertThat( csapApp.load_junit_definition_start_collectors( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		var csapTestService = csapApp.getServiceInstanceCurrentHost( "k8s-csap-test" ) ;
		assertThat( csapTestService ).as( "k8s-csap-test template loaded" ).isNotNull( ) ;

		osManager.wait_for_initial_process_status_scan( 10 ) ;

		CSAP.setLogToDebug( OsManager.class.getName( ) ) ;

		// csapApp.setServiceDebugName( "k8s-csap-test" );
		osManager.expireAndUpdateProcessListing( ) ;

		CSAP.setLogToInfo( OsManager.class.getName( ) ) ;

		logger.info( CsapApplication.testHeader( "completed" ) ) ;

	}

	@AfterAll
	void afterAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Cleaning up thin tests" ) ) ;

		csapApp.metricManager( ).setSuspendCollection( true ) ;

	}

	@Inject
	Application csapApp ;

	@Inject
	OsManager osManager ;

	@Inject
	ObjectMapper jsonMapper ;

	@Test
	public void verify_os_settings_loaded ( ) {

		logger.info( "{}", osManager.getOsCommands( ) ) ;

		assertThat( osManager.getOsCommands( ).getSystemServiceDetails( "testservice" ) )
				.contains( "systemctl --no-pager status -l testservice" ) ;

	}

	@Test
	public void verify_service_resource_collection ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		var agentInstance = csapApp.getServiceInstanceCurrentHost( CsapConstants.AGENT_ID ) ;

		osManager.buildServiceStatsReportAndUpdateTopCpu( true ) ;
		osManager.getServiceResourceRunnable( ).testFullCollection( ) ;

		logger.info( "{} container: {}", agentInstance.toSummaryString( ), agentInstance.getDefaultContainer( ) ) ;
		osManager.checkForProcessStatusUpdate( ) ;

		assertThat( agentInstance.getOsProcessPriority( ) )
				.isEqualTo( -12 ) ;

		assertThat( agentInstance.getDefaultContainer( ).getCurrentProcessPriority( ) )
				.isEqualTo( "-12" ) ;

		assertThat( agentInstance.getDefaultContainer( ).getSocketCount( ) )
				.isEqualTo( 12 ) ;

		assertThat( agentInstance.getDefaultContainer( ).getTopCpu( ) )
				.isEqualTo( 9.1f ) ;

		assertThat( agentInstance.getDefaultContainer( ).getDiskUsageInMb( ) )
				.isEqualTo( 72 ) ;

		assertThat( agentInstance.getDefaultContainer( ).getDiskReadKb( ) )
				.isEqualTo( 22 ) ;

		assertThat( agentInstance.getDefaultContainer( ).getDiskWriteKb( ) )
				.isEqualTo( 6 ) ;

	}

	@Test
	public void verify_docker_pid_matching ( )
		throws JsonProcessingException ,
		IOException {

		JsonNode serviceRuntime = osManager.buildServiceStatsReportAndUpdateTopCpu( true ).at( "/ps/nginx" ) ;

		logger.info( "{} {}", CsapApplication.header( "Test: nginx runtime" ), CSAP.jsonPrint( serviceRuntime ) ) ;

		assertThat( serviceRuntime.at( "/containers/0/cpuUtil" ).asDouble( ) )
				.as( "nginx.cpuUtil" )
				.isEqualTo( 6.0 ) ;

		serviceRuntime = osManager.buildServiceStatsReportAndUpdateTopCpu( true ).at( "/ps/k8s-api-server" ) ;
		logger.info( "k8s-api-server Runtime: {}",
				CSAP.jsonPrint( serviceRuntime ) ) ;

		assertThat( serviceRuntime.at( "/containers/0/cpuUtil" ).asDouble( ) )
				.as( "/ps/k8s-api-server/cpuUtil" )
				.isEqualTo( 4.0 ) ;

		// serviceRuntime = osManager.getServiceMetrics( true ).at(
		// "/ps/k8s-not-running-service" );
		// logger.info( "k8s-not-running-service Runtime: {}",
		// CSAP.jsonPrint( serviceRuntime ) );
		//
		// assertThat( serviceRuntime )
		// .as( "kubernetes services that are not running are not reported in stats" )
		// .isNull();

	}

	@Test
	public void verify_process_discovery_summary ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		JsonNode summary = osManager.latest_process_discovery_results( ).get( C7.response_plain_text
				.val( ) ) ;

		logger.debug( "Discovered Containers: {}", summary.asText( ) ) ;

		assertThat( summary.asText( ) )
				.as( "summary information" )
				.contains( "podName", "etcd-centos1" ) ;

	}

	@Test
	public void verify_host_runtime_filters_stopped_k8_services ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		var runtimeStatus = osManager.getHostRuntime( ) ;
		
//		logger.info( "Full Runtime: {} ", CSAP.jsonPrint( runtimeStatus ) );
		
		var apiServiceReport = runtimeStatus.path( "services" ).path( "k8s-api-server" ) ;

		logger.info( "Host Runtime Services: {}", CSAP.jsonPrint( apiServiceReport ) ) ;

		assertThat( apiServiceReport.isMissingNode( ) ).isFalse( ) ;

		var notRunningService = runtimeStatus.path( "services" ).path( "k8s-not-running-service" ) ;

		assertThat( notRunningService.isMissingNode( ) ).isFalse( ) ;
		assertThat( notRunningService.at("/containers/0/running").asBoolean( true) ).isFalse( ) ;
		
//		assertThat( runtimeStatus.at( "/services/k8s-not-running-service" ).isMissingNode( ) )
//				.as( "k8s-not-running-service should be filtered" )
//				.isTrue( ) ;

		// JsonPath.read(metricsNode.toString(), "$.ps.CsAgent.cpuUtil"));
	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void verify_parsing_of_os_data_for_service ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		// logger.info( "lastCollection pid mappings: {}" ,
		// osManager.getLatestProcessSummary() );

		JsonNode agentCollection = osManager.buildServiceStatsReportAndUpdateTopCpu( true ).at( "/ps/"
				+ CsapConstants.AGENT_NAME ) ;

		logger.info( "CsAgent Runtime: {}",
				CSAP.jsonPrint( agentCollection ) ) ;

		assertThat( agentCollection.at( ContainerState.JSON_PATH_CPU ).asDouble( ) )
				.as( "CsAgent.cpuUtil" )
				.isGreaterThan( 0.0 ) ;

		assertThat( agentCollection.at( ContainerState.JSON_PATH_PRIORITY ).asInt( ) )
				.as( "CsAgent.currentProcessPriority" )
				.isEqualTo( -12 ) ;

		// JsonPath.read(metricsNode.toString(), "$.ps.CsAgent.cpuUtil"));
	}

	@Test
	public void verify_host_runtime_collection ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		JsonNode runtimeStatus = osManager.getHostRuntime( ) ;

		logger.info( "Host Runtime: {}", CSAP.jsonPrint( runtimeStatus.path( "hostStats" ) ) ) ;

		JsonNode hostStats = runtimeStatus.path( "hostStats" ) ;

		assertThat( hostStats.at( "/cpuCount" ).asInt( ) )
				.as( "cpuCount" )
				.isGreaterThan( 0 ) ;

		assertThat( hostStats.at( "/memoryAggregateFreeMb" ).asInt( ) )
				.as( "memoryAggregateFreeMb" )
				.isEqualTo( 14441 ) ;

		// JsonPath.read(metricsNode.toString(), "$.ps.CsAgent.cpuUtil"));
	}

	@Test
	public void verify_host_status_with_multiple_container_services ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var runtimeStatus = osManager.getHostRuntime( ) ;
		logger.debug( "Host Runtime: {}", CSAP.jsonPrint( runtimeStatus ) ) ;

		var csapTestService = csapApp.getServiceInstanceCurrentHost( "k8s-csap-test" ) ;

		logger.info( CsapApplication.testHeader( "{}" ), csapTestService.getDefaultContainer( ) ) ;

		assertThat( csapTestService.getContainerStatusList( ).size( ) )
				.as( "container count" )
				.isEqualTo( 2 ) ;

		var csapTestStatus = runtimeStatus.at( "/services/" + csapTestService.getServiceName_Port( ) ) ;
		logger.info( "csapTestStatus: {}", CSAP.jsonPrint( csapTestStatus ) ) ;

		// assertThat( csapTestStatus.at( "/cpuCount" ).asInt() )
		// .as( "cpuCount" )
		// .isEqualTo( 8 );

		// JsonPath.read(metricsNode.toString(), "$.ps.CsAgent.cpuUtil"));
	}

}
